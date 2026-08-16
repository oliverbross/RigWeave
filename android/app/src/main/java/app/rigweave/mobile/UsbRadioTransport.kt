package app.rigweave.mobile

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbDeviceConnection
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface UsbResult {
    data class Connected(val frames: ByteArray, val detail: String, val cwFrames: ByteArray = byteArrayOf()) : UsbResult
    data class PermissionRequired(val detail: String) : UsbResult
    data class Unavailable(val detail: String) : UsbResult
}

class UsbRadioTransport(private val context: Context) {
    companion object { const val ACTION_USB_PERMISSION = "app.rigweave.mobile.USB_PERMISSION" }
    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val mutex = Mutex()
    private var connection: UsbDeviceConnection? = null
    private var port: UsbSerialPort? = null
    private val fastQueries = listOf("FA;", "FB;", "IF;", "TQ;", "SM;", "SW;", "PO;")
    private val slowQueries = listOf("MD;", "DS;", "GT;", "AG;", "RG;", "BW;", "PC;", "ML;", "MG;", "KS;", "IS;",
        "PA;", "RA;", "RT;", "XT;", "FR;", "FT;")
    private val instrumentQueries = fastQueries + slowQueries
    private val connectQueries = listOf("K3;", "OM;", "ID;", "K31;", "AI2;") + instrumentQueries
    private var pollCount = 0

    fun discovered(): List<String> = UsbSerialProber.getDefaultProber().findAllDrivers(manager).map {
        "VID:%04X PID:%04X".format(it.device.vendorId, it.device.productId)
    }

    suspend fun connect(): UsbResult = withContext(Dispatchers.IO) { mutex.withLock {
        if (port?.isOpen == true) {
            return@withLock UsbResult.Connected(byteArrayOf(), "Elecraft KX3 is already connected")
        }
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(manager).firstOrNull()
            ?: return@withLock UsbResult.Unavailable("No supported USB serial adapter detected")
        if (!awaitPermission(driver.device)) {
            return@withLock UsbResult.PermissionRequired("USB permission was not granted · tap Connect to try again")
        }
        closeLocked()
        val openedConnection = manager.openDevice(driver.device)
            ?: return@withLock UsbResult.Unavailable("USB device could not be opened")
        val openedPort = driver.ports.firstOrNull() ?: run {
            openedConnection.close()
            return@withLock UsbResult.Unavailable("No serial port exposed")
        }
        try {
            openedPort.open(openedConnection)
            openedPort.setParameters(38_400, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            connection = openedConnection
            port = openedPort
            pollCount = 0
            val frames = exchange(connectQueries)
            UsbResult.Connected(frames, "Connected at 38,400 baud · VID:%04X PID:%04X".format(driver.device.vendorId, driver.device.productId))
        } catch (error: Exception) {
            runCatching { openedPort.close() }
            openedConnection.close()
            connection = null
            port = null
            UsbResult.Unavailable("USB/CAT failed: ${error.message ?: error.javaClass.simpleName}")
        }
    } }

    suspend fun send(command: String): UsbResult = withContext(Dispatchers.IO) { mutex.withLock {
        val active = port ?: return@withLock UsbResult.Unavailable("Connect the USB serial adapter first")
        val normalized = command.trim().let { if (it.endsWith(';')) it else "$it;" }
        if (normalized == ";") return@withLock UsbResult.Unavailable("CAT command is empty")
        try {
            active.write(normalized.toByteArray(Charsets.US_ASCII), 1_000)
            val frames = readFrames(active) + exchange(instrumentQueries)
            UsbResult.Connected(frames, "Sent $normalized")
        } catch (error: Exception) {
            closeLocked()
            UsbResult.Unavailable("USB/CAT failed: ${error.message ?: error.javaClass.simpleName}")
        }
    } }

    suspend fun poll(): UsbResult? = withContext(Dispatchers.IO) { mutex.withLock {
        if (port == null) return@withLock null
        try {
            val queries = if (pollCount++ % 3 == 0) instrumentQueries else fastQueries
            UsbResult.Connected(exchange(queries), "Live CAT state")
        } catch (error: Exception) {
            closeLocked()
            UsbResult.Unavailable("USB/CAT failed: ${error.message ?: error.javaClass.simpleName}")
        }
    } }

    suspend fun pollCwText(): UsbResult? = withContext(Dispatchers.IO) { mutex.withLock {
        if (port == null) return@withLock null
        try {
            val frames = exchange(listOf("DB;"), initialTimeout = 120, trailingTimeout = 40)
            UsbResult.Connected(frames, "Live CW text", frames)
        } catch (error: Exception) {
            closeLocked()
            UsbResult.Unavailable("USB/CAT failed: ${error.message ?: error.javaClass.simpleName}")
        }
    } }

    suspend fun disconnect() = withContext(Dispatchers.IO) { mutex.withLock { closeLocked() } }

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
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return try {
            val permissionIntent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                device.deviceId,
                permissionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.requestPermission(device, pendingIntent)
            result.await()
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private fun exchange(commands: List<String>, initialTimeout: Int = 350, trailingTimeout: Int = 35): ByteArray {
        val active = port ?: return byteArrayOf()
        if (commands.isEmpty()) return byteArrayOf()
        active.write(commands.joinToString("").toByteArray(Charsets.US_ASCII), 1_000)
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

    private fun closeLocked() {
        runCatching { port?.close() }
        connection?.close()
        port = null
        connection = null
    }
}
