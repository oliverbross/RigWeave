# Android Local Receiver Architecture

```text
Existing I/Q source owner
  TCI float32 | QMX/QMX+ converted float | KX3/KX2/generic proven stereo I/Q | debug replay
        |
        v
LocalReceiverController (one application owner, queue 8, max RX A/B)
        |
        +--> checked NativeHandleOwner per active receiver
        |      NCO -> complex FIR -> rate conversion -> mode demod -> metadata
        |
        +--> ReceiveTimeShiftController audio pre-roll (existing owner)
        +--> ReceiverRecordingStore / atomic PCM16 WAV
        +--> TciRxAudioController -> NativeRxDsp -> one stereo AudioTrack
```

The controller does not own radio transport, the Panadapter renderer, Scanner, time shift or Android audio output. Source/rate changes create a new native configuration generation and retire incompatible filter state. Input and output are copied into bounded queues; no DSP runs on the main thread and no small-stage thread fan-out exists.

All JNI handles use `NativeHandleOwner`; no C++ pointer is exposed to general Kotlin code. Native arrays are capped at four million samples. Output is fixed at 48 kHz mono, or interleaved stereo for WFM. Spectrum mode returns metadata and no audio.

Global Stop and every disconnect/profile/background owner path clear queues, recording, listening and acquisition. Safe preferences persist per mode, while active state never restores.
