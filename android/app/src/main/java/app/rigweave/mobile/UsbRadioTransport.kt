package app.rigweave.mobile

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialPort.ControlLine
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface UsbResult {
    data class Connected(val frames: ByteArray, val detail: String, val cwFrames: ByteArray = byteArrayOf()) : UsbResult
    data class PermissionRequired(val detail: String) : UsbResult
    data class Unavailable(val detail: String) : UsbResult
}

data class SerialDeviceDescriptor(
    val sessionKey: String,
    val stableKey: String,
    val driverFamily: String,
    val manufacturer: String,
    val product: String,
    val vidPid: String,
    val serialNumber: String,
    val portIndex: Int,
    val deviceAddress: String,
) {
    val label: String get() = listOf(driverFamily, manufacturer.takeIf(String::isNotBlank), product.takeIf(String::isNotBlank), vidPid,
        serialNumber.takeIf(String::isNotBlank)?.let { "S/N $it" }, "port $portIndex", deviceAddress)
        .filterNotNull().filter(String::isNotBlank).joinToString(" · ")
}

data class FreshTqResponse(val frames: ByteArray, val transmitting: Boolean?)

class UsbRadioTransport(private val context: Context) {
    companion object { const val ACTION_USB_PERMISSION = "app.rigweave.mobile.USB_PERMISSION" }
    private data class Candidate(val descriptor: SerialDeviceDescriptor, val driver: UsbSerialDriver, val portIndex: Int)

    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val prefs = context.getSharedPreferences("rigweave-usb", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var connection: UsbDeviceConnection? = null
    private var port: UsbSerialPort? = null
    private var sessionSelection: String? = null
    private val fastQueries = listOf("FA;", "FB;", "IF;", "TQ;", "SM;", "SW;", "PO;", "AG;", "RG;", "BW;",
        "PC;", "ML;", "MG;", "KS;", "IS;")
    private val slowQueries = listOf("MD;", "DS;", "GT;", "PA;", "RA;", "RT;", "XT;", "FR;", "FT;")
    private val instrumentQueries = fastQueries + slowQueries
    private val connectQueries = listOf("K3;", "OM;", "ID;", "K31;", "AI2;") + instrumentQueries
    private var pollCount = 0
    @Volatile private var voiceOperationExclusive = false
    @Volatile private var eqOperationExclusive = false

    var candidates by mutableStateOf(emptyList<SerialDeviceDescriptor>()); private set
    var selected by mutableStateOf<SerialDeviceDescriptor?>(null); private set
    var controlLineStatus by mutableStateOf("RTS/DTR not checked"); private set
    val isConnected: Boolean get() = port?.isOpen == true

    fun beginVoiceOperation() { voiceOperationExclusive = true }
    fun endVoiceOperation() { voiceOperationExclusive = false }

    suspend fun <T> exclusiveEqTransaction(block: suspend (EqCatIo) -> T): T = withContext(Dispatchers.IO) {
        mutex.lock()
        try {
            check(!voiceOperationExclusive) { "Voice macro owns CAT; stop it before opening EQ Studio" }
            val active = port ?: error("Connect the USB serial adapter first")
            eqOperationExclusive = true
            block(EqTransportSession(active))
        } finally {
            eqOperationExclusive = false
            mutex.unlock()
        }
    }

    fun discovered(): List<String> = refreshCandidates().map(SerialDeviceDescriptor::label)

    fun refreshCandidates(): List<SerialDeviceDescriptor> {
        candidates = candidateRecords().map(Candidate::descriptor)
        return candidates
    }

    suspend fun selectCandidate(sessionKey: String) = withContext(Dispatchers.IO) { mutex.withLock {
        closeLocked()
        val candidate = candidateRecords().firstOrNull { it.descriptor.sessionKey == sessionKey }
        sessionSelection = candidate?.descriptor?.sessionKey
        if (candidate == null) prefs.edit().remove("cat_stable_key").apply()
        else prefs.edit().putString("cat_stable_key", candidate.descriptor.stableKey).apply()
        selected = candidate?.descriptor
        refreshCandidates()
    } }

    suspend fun connect(): UsbResult = withContext(Dispatchers.IO) { mutex.withLock {
        if (port?.isOpen == true) return@withLock UsbResult.Connected(byteArrayOf(), "CAT is already connected · ${selected?.label.orEmpty()}")
        val available = candidateRecords()
        candidates = available.map(Candidate::descriptor)
        val explicit = sessionSelection?.let { key -> available.firstOrNull { it.descriptor.sessionKey == key } }
        val policy = chooseStableCandidate(available, prefs.getString("cat_stable_key", null)) { it.descriptor.stableKey }
        val choice = explicit ?: policy.selected
        if (choice == null) return@withLock UsbResult.Unavailable(if (available.size > 1) "${policy.reason} in Settings · Safety" else policy.reason)
        if (!awaitPermission(choice.driver.device)) {
            return@withLock UsbResult.PermissionRequired("USB permission was not granted · tap Connect to try again")
        }
        closeLocked()
        val refreshed = candidateRecords().firstOrNull {
            it.driver.device.deviceId == choice.driver.device.deviceId && it.portIndex == choice.portIndex
        } ?: choice
        val openedConnection = manager.openDevice(refreshed.driver.device)
            ?: return@withLock UsbResult.Unavailable("USB device could not be opened")
        val openedPort = refreshed.driver.ports.getOrNull(refreshed.portIndex) ?: run {
            openedConnection.close(); return@withLock UsbResult.Unavailable("Selected serial port is no longer available")
        }
        try {
            openedPort.open(openedConnection)
            openedPort.setParameters(38_400, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            deassertControlLines(openedPort)
            connection = openedConnection
            port = openedPort
            pollCount = 0
            selected = refreshed.descriptor
            prefs.edit().putString("cat_stable_key", refreshed.descriptor.stableKey).apply()
            val frames = exchange(connectQueries)
            UsbResult.Connected(frames, "Connected at 38,400 baud · ${refreshed.descriptor.label} · $controlLineStatus")
        } catch (error: Exception) {
            runCatching { deassertControlLines(openedPort) }
            runCatching { openedPort.close() }
            openedConnection.close()
            connection = null; port = null; selected = null
            UsbResult.Unavailable("USB/CAT failed closed: ${error.message ?: error.javaClass.simpleName}")
        }
    } }

    suspend fun send(command: String): UsbResult = withContext(Dispatchers.IO) { mutex.withLock {
        val active = port ?: return@withLock UsbResult.Unavailable("Connect the USB serial adapter first")
        val normalized = normalize(command) ?: return@withLock UsbResult.Unavailable("CAT command is empty")
        if (voiceOperationExclusive && normalized != "RX;") return@withLock UsbResult.Unavailable("Voice macro owns CAT until RX cleanup completes")
        try {
            active.write(normalized.toByteArray(Charsets.US_ASCII), 1_000)
            val frames = readFrames(active) + exchange(instrumentQueries)
            UsbResult.Connected(frames, "Sent $normalized")
        } catch (error: Exception) { failLocked(error) }
    } }

    suspend fun sendFast(command: String): ByteArray = withContext(Dispatchers.IO) { mutex.withLock {
        val active = port ?: error("CAT adapter is disconnected")
        val normalized = normalize(command) ?: error("CAT command is empty")
        try {
            active.write(normalized.toByteArray(Charsets.US_ASCII), 500)
            readFrames(active, 100, 30)
        } catch (error: Exception) { closeLocked(); throw error }
    } }

    suspend fun queryTqFresh(): FreshTqResponse = withContext(Dispatchers.IO) { mutex.withLock {
        try {
            val frames = exchangeFresh("TQ;", initialTimeout = 180, trailingTimeout = 35)
            FreshTqResponse(frames, parseFreshTq(frames))
        } catch (error: Exception) { closeLocked(); throw error }
    } }

    suspend fun confirmTq(expected: Boolean, timeoutMillis: Long = 1_000): FreshTqResponse = withContext(Dispatchers.IO) { mutex.withLock {
        val started = System.currentTimeMillis()
        val all = ArrayList<Byte>()
        var value: Boolean? = null
        while (System.currentTimeMillis() - started < timeoutMillis) {
            val frames = exchangeFresh("TQ;", initialTimeout = 120, trailingTimeout = 25)
            frames.forEach(all::add)
            value = parseFreshTq(frames)
            if (value == expected) break
            delay(70)
        }
        FreshTqResponse(all.toByteArray(), value.takeIf { it == expected })
    } }

    suspend fun poll(): UsbResult? = withContext(Dispatchers.IO) { mutex.withLock {
        if (port == null || voiceOperationExclusive || eqOperationExclusive) return@withLock null
        try {
            val queries = if (pollCount++ % 6 == 0) instrumentQueries else fastQueries
            UsbResult.Connected(exchange(queries), "Live CAT state · ${selected?.label.orEmpty()}")
        } catch (error: Exception) { failLocked(error) }
    } }

    suspend fun pollCwText(): UsbResult? = withContext(Dispatchers.IO) { mutex.withLock {
        if (port == null || voiceOperationExclusive || eqOperationExclusive) return@withLock null
        try {
            val frames = exchange(listOf("DB;"), initialTimeout = 120, trailingTimeout = 40)
            UsbResult.Connected(frames, "Live CW text", frames)
        } catch (error: Exception) { failLocked(error) }
    } }

    suspend fun disconnect() = withContext(Dispatchers.IO) { mutex.withLock { closeLocked() } }

    private fun candidateRecords(): List<Candidate> = UsbSerialProber.getDefaultProber().findAllDrivers(manager).flatMap { driver ->
        driver.ports.indices.map { portIndex ->
            val device = driver.device
            val serial = if (manager.hasPermission(device)) runCatching { device.serialNumber.orEmpty() }.getOrDefault("") else ""
            val family = driver.javaClass.simpleName.removeSuffix("SerialDriver").ifBlank { "USB serial" }
            val manufacturer = device.manufacturerName.orEmpty()
            val product = device.productName.orEmpty()
            val vidPid = "%04X:%04X".format(device.vendorId, device.productId)
            val stable = listOf(family, manufacturer, product, vidPid, serial, portIndex.toString()).joinToString("|")
            Candidate(SerialDeviceDescriptor("${device.deviceId}:$portIndex", stable, family, manufacturer, product,
                vidPid, serial, portIndex, device.deviceName), driver, portIndex)
        }
    }

    private suspend fun awaitPermission(device: UsbDevice): Boolean {
        if (manager.hasPermission(device)) return true
        val result = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            @Suppress("DEPRECATION")
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != ACTION_USB_PERMISSION) return
                val returnedDevice = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (returnedDevice?.deviceId != device.deviceId) return
                result.complete(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_USB_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED)
        return try {
            val pendingIntent = PendingIntent.getBroadcast(context, device.deviceId,
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.requestPermission(device, pendingIntent)
            result.await()
        } finally { runCatching { context.unregisterReceiver(receiver) } }
    }

    private fun deassertControlLines(active: UsbSerialPort) {
        val supported = active.supportedControlLines
        if (ControlLine.RTS in supported) active.rts = false
        if (ControlLine.DTR in supported) active.dtr = false
        val activeLines = if (ControlLine.RTS in supported || ControlLine.DTR in supported) active.controlLines else emptySet()
        require(ControlLine.RTS !in activeLines && ControlLine.DTR !in activeLines) { "RTS/DTR could not be confirmed inactive" }
        controlLineStatus = "RTS/DTR confirmed inactive"
    }

    private fun exchange(commands: List<String>, initialTimeout: Int = 350, trailingTimeout: Int = 35): ByteArray {
        val active = port ?: return byteArrayOf()
        if (commands.isEmpty()) return byteArrayOf()
        active.write(commands.joinToString("").toByteArray(Charsets.US_ASCII), 1_000)
        return readFrames(active, initialTimeout, trailingTimeout)
    }

    private fun exchangeFresh(command: String, initialTimeout: Int, trailingTimeout: Int): ByteArray {
        val active = port ?: return byteArrayOf()
        readFrames(active, initialTimeout = 1, trailingTimeout = 1)
        active.write(command.toByteArray(Charsets.US_ASCII), 500)
        return readFrames(active, initialTimeout, trailingTimeout)
    }

    private fun readFrames(active: UsbSerialPort, initialTimeout: Int = 350, trailingTimeout: Int = 35): ByteArray {
        val output = ArrayList<Byte>()
        var timeout = initialTimeout
        repeat(12) {
            val buffer = ByteArray(512)
            val count = active.read(buffer, timeout).coerceAtLeast(0)
            if (count == 0) return output.toByteArray()
            repeat(count) { index -> output += buffer[index] }
            timeout = trailingTimeout
        }
        return output.toByteArray()
    }

    private inner class EqTransportSession(private val active: UsbSerialPort) : EqCatIo {
        private val allowedWrites = Regex("^(MN(?:008|009|255);|SWT(?:19|27|20|28|21|29|32|33);|UP;|DN;|TE(?:[+-]\\d{2}){8};)$")
        private val allowedQueries = setOf("TQ;", "MN;", "DB;", "FT;", "MD;", "MD$;", "ES;", "RVM;", "ID;", "OM;")

        override suspend fun query(command: String, expectedPrefix: String, timeoutMillis: Long): String {
            val normalized = normalize(command) ?: error("CAT query is empty")
            require(normalized in allowedQueries) { "EQ query is not allowlisted: $normalized" }
            readFrames(active, 1, 1)
            active.write(normalized.toByteArray(Charsets.US_ASCII), 500)
            val deadline = System.currentTimeMillis() + timeoutMillis
            val pending = StringBuilder()
            val unsolicited = mutableListOf<String>()
            var matched: String? = null
            var quietDeadline = Long.MAX_VALUE
            while (System.currentTimeMillis() < deadline) {
                val buffer = ByteArray(256)
                val now = System.currentTimeMillis()
                if (matched != null && now >= quietDeadline) return matched
                val remaining = minOf(deadline - now, if (matched == null) 120 else quietDeadline - now)
                val wait = remaining.coerceIn(1, 120).toInt()
                val count = active.read(buffer, wait).coerceAtLeast(0)
                if (count == 0) {
                    if (matched != null && System.currentTimeMillis() >= quietDeadline) return matched
                    continue
                }
                pending.append(buffer.copyOf(count).toString(Charsets.US_ASCII))
                while (';' in pending) {
                    val end = pending.indexOf(";")
                    val frame = pending.substring(0, end + 1)
                    pending.delete(0, end + 1)
                    if (frame.startsWith(expectedPrefix, ignoreCase = true)) {
                        matched = frame
                        quietDeadline = System.currentTimeMillis() + 55
                    } else unsolicited += frame
                }
            }
            if (matched != null) return matched
            error("Timed out waiting for $expectedPrefix response${if (unsolicited.isEmpty()) "" else " (${unsolicited.size} unrelated frame(s) received)"}")
        }

        override suspend fun write(command: String) {
            val normalized = normalize(command) ?: error("CAT command is empty")
            require(allowedWrites.matches(normalized)) { "EQ write is not allowlisted: $normalized" }
            active.write(normalized.toByteArray(Charsets.US_ASCII), 500)
        }
    }

    private fun normalize(command: String): String? = command.trim().let { if (it.endsWith(';')) it else "$it;" }.takeUnless { it == ";" }

    private fun failLocked(error: Exception): UsbResult.Unavailable {
        closeLocked()
        return UsbResult.Unavailable("USB/CAT failed: ${error.message ?: error.javaClass.simpleName}")
    }

    private fun closeLocked() {
        port?.let { active -> runCatching { deassertControlLines(active) }; runCatching { active.close() } }
        connection?.close()
        port = null; connection = null; selected = null
    }
}
