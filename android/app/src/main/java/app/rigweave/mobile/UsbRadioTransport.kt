package app.rigweave.mobile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbDeviceConnection
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface UsbResult {
    data class Connected(val frames: ByteArray, val detail: String) : UsbResult
    data class PermissionRequired(val detail: String) : UsbResult
    data class Unavailable(val detail: String) : UsbResult
}

class UsbRadioTransport(private val context: Context) {
    companion object { const val ACTION_USB_PERMISSION = "app.rigweave.mobile.USB_PERMISSION" }
    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val mutex = Mutex()
    private var connection: UsbDeviceConnection? = null
    private var port: UsbSerialPort? = null
    private val instrumentQueries = listOf("K3;", "OM;", "ID;", "FA;", "FB;", "MD;", "IF;", "TQ;", "SM;", "SW;", "PO;",
        "AG;", "RG;", "BW;", "PC;", "PA;", "RA;", "RT;", "XT;", "FR;", "FT;")

    fun discovered(): List<String> = UsbSerialProber.getDefaultProber().findAllDrivers(manager).map {
        "VID:%04X PID:%04X".format(it.device.vendorId, it.device.productId)
    }

    fun requestPermission(): UsbResult {
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(manager).firstOrNull()
            ?: return UsbResult.Unavailable("No supported USB serial adapter detected")
        if (manager.hasPermission(driver.device)) return UsbResult.PermissionRequired("USB permission already granted; connect again")
        val intent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
        manager.requestPermission(driver.device, intent)
        return UsbResult.PermissionRequired("USB permission requested")
    }

    suspend fun connect(): UsbResult = withContext(Dispatchers.IO) { mutex.withLock {
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(manager).firstOrNull()
            ?: return@withLock UsbResult.Unavailable("No supported USB serial adapter detected")
        if (!manager.hasPermission(driver.device)) return@withLock requestPermission()
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
            val frames = exchange(instrumentQueries)
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
            val frames = readFrames(active) + exchange(instrumentQueries.drop(1))
            UsbResult.Connected(frames, "Sent $normalized")
        } catch (error: Exception) {
            closeLocked()
            UsbResult.Unavailable("USB/CAT failed: ${error.message ?: error.javaClass.simpleName}")
        }
    } }

    suspend fun poll(): UsbResult? = withContext(Dispatchers.IO) { mutex.withLock {
        if (port == null) return@withLock null
        try {
            UsbResult.Connected(exchange(instrumentQueries.drop(1)), "Live CAT state")
        } catch (error: Exception) {
            closeLocked()
            UsbResult.Unavailable("USB/CAT failed: ${error.message ?: error.javaClass.simpleName}")
        }
    } }

    suspend fun disconnect() = withContext(Dispatchers.IO) { mutex.withLock { closeLocked() } }

    private fun exchange(commands: List<String>): ByteArray {
        val active = port ?: return byteArrayOf()
        val output = ArrayList<Byte>()
        commands.forEach { command ->
            active.write(command.toByteArray(Charsets.US_ASCII), 1_000)
            readFrames(active).forEach { output += it }
        }
        return output.toByteArray()
    }

    private fun readFrames(active: UsbSerialPort): ByteArray {
        val output = ArrayList<Byte>()
        repeat(4) {
            val buffer = ByteArray(512)
            val count = active.read(buffer, if (it == 0) 500 else 80).coerceAtLeast(0)
            repeat(count) { index -> output += buffer[index] }
            if (count == 0) return@repeat
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
