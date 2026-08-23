package app.rigweave.mobile.radio.hamlib

internal interface HamlibNativeApi {
    fun libraryInfo(): String
    fun models(): String
    fun create(modelId: Int): Long
    fun destroy(handle: Long)
    fun readOnly(handle: Long, enabled: Boolean): Int
    fun configureSerial(handle: Long, profile: HamlibSerialProfile): Int
    fun configureNetwork(handle: Long, profile: HamlibNetworkProfile): Int
    fun open(handle: Long): Int
    fun close(handle: Long): Int
    fun bridgeRead(handle: Long, maximum: Int, timeoutMs: Int): ByteArray
    fun bridgeWrite(handle: Long, data: ByteArray): Int
    fun snapshot(handle: Long): String
    fun apply(handle: Long, action: HamlibAction): Int
    fun error(status: Int): String
}

internal object NativeHamlib : HamlibNativeApi {
    init { System.loadLibrary("rigweave") }

    external fun libraryInfoNative(): String
    external fun modelsNative(): String
    external fun sessionCreateNative(modelId: Int): Long
    external fun sessionDestroyNative(handle: Long)
    external fun sessionSetReadOnlyNative(handle: Long, enabled: Boolean): Int
    external fun sessionConfigureSerialNative(
        handle: Long, baud: Int, dataBits: Int, stopBits: Int, parity: Int,
        handshake: Int, timeoutMs: Int, rts: Int, dtr: Int,
    ): Int
    external fun sessionConfigureNetworkNative(handle: Long, host: String, port: Int, timeoutMs: Int): Int
    external fun sessionOpenNative(handle: Long): Int
    external fun sessionCloseNative(handle: Long): Int
    external fun bridgeReadNative(handle: Long, maximum: Int, timeoutMs: Int): ByteArray
    external fun bridgeWriteNative(handle: Long, data: ByteArray): Int
    external fun sessionSnapshotNative(handle: Long): String
    external fun setFrequencyNative(handle: Long, vfo: String, frequency: Long): Int
    external fun setVfoNative(handle: Long, vfo: String): Int
    external fun setModeNative(handle: Long, vfo: String, mode: String, passbandHz: Int): Int
    external fun setSplitNative(handle: Long, enabled: Boolean, txVfo: String): Int
    external fun setRitNative(handle: Long, offsetHz: Int): Int
    external fun setXitNative(handle: Long, offsetHz: Int): Int
    external fun setLevelNative(handle: Long, level: String, value: Double): Int
    external fun setFunctionNative(handle: Long, function: String, enabled: Boolean): Int
    external fun setParameterNative(handle: Long, parameter: String, value: Double): Int
    external fun setPttNative(handle: Long, enabled: Boolean): Int
    external fun tuneNative(handle: Long): Int
    external fun errorNative(status: Int): String

    override fun libraryInfo() = libraryInfoNative()
    override fun models() = modelsNative()
    override fun create(modelId: Int) = sessionCreateNative(modelId)
    override fun destroy(handle: Long) = sessionDestroyNative(handle)
    override fun readOnly(handle: Long, enabled: Boolean) = sessionSetReadOnlyNative(handle, enabled)
    override fun configureSerial(handle: Long, profile: HamlibSerialProfile) = sessionConfigureSerialNative(
        handle, profile.baud, profile.dataBits, profile.stopBits, profile.parity,
        profile.handshake, profile.timeoutMs, profile.rts, profile.dtr,
    )
    override fun configureNetwork(handle: Long, profile: HamlibNetworkProfile) =
        sessionConfigureNetworkNative(handle, profile.host, profile.port, profile.timeoutMs)
    override fun open(handle: Long) = sessionOpenNative(handle)
    override fun close(handle: Long) = sessionCloseNative(handle)
    override fun bridgeRead(handle: Long, maximum: Int, timeoutMs: Int) = bridgeReadNative(handle, maximum, timeoutMs)
    override fun bridgeWrite(handle: Long, data: ByteArray) = bridgeWriteNative(handle, data)
    override fun snapshot(handle: Long) = sessionSnapshotNative(handle)
    override fun error(status: Int) = errorNative(status)
    override fun apply(handle: Long, action: HamlibAction): Int = when (action) {
        is HamlibAction.SetFrequency -> setFrequencyNative(handle, action.vfo, action.hz)
        is HamlibAction.SetVfo -> setVfoNative(handle, action.vfo)
        is HamlibAction.SetMode -> setModeNative(handle, action.vfo, action.mode, action.passbandHz)
        is HamlibAction.SetSplit -> setSplitNative(handle, action.enabled, action.txVfo)
        is HamlibAction.SetRit -> setRitNative(handle, action.hz)
        is HamlibAction.SetXit -> setXitNative(handle, action.hz)
        is HamlibAction.SetLevel -> setLevelNative(handle, action.name, action.value)
        is HamlibAction.SetFunction -> setFunctionNative(handle, action.name, action.enabled)
        is HamlibAction.SetParameter -> setParameterNative(handle, action.name, action.value)
        is HamlibAction.SetPtt -> setPttNative(handle, action.enabled)
        HamlibAction.Tune -> tuneNative(handle)
    }
}
