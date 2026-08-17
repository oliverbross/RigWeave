#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <sstream>
#include <string>
#include "rigweave/core.h"

namespace {
rw_context *context(jlong handle) { return reinterpret_cast<rw_context *>(static_cast<intptr_t>(handle)); }
rw_feature_context *features(jlong handle) { return reinterpret_cast<rw_feature_context *>(static_cast<intptr_t>(handle)); }
rw_panadapter_context *panadapter(jlong handle) { return reinterpret_cast<rw_panadapter_context *>(static_cast<intptr_t>(handle)); }
std::string utf(JNIEnv *env, jstring value) {
    if (!value) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_rigweave_mobile_NativeCore_create(JNIEnv *, jobject) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(rw_context_create()));
}

extern "C" JNIEXPORT void JNICALL
Java_app_rigweave_mobile_NativeCore_destroy(JNIEnv *, jobject, jlong handle) {
    rw_context_destroy(context(handle));
}

extern "C" JNIEXPORT jint JNICALL
Java_app_rigweave_mobile_NativeCore_feed(JNIEnv *env, jobject, jlong handle, jbyteArray data) {
    if (!data) return 0;
    const jsize length = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    const int applied = rw_context_feed(context(handle), reinterpret_cast<const char *>(bytes), static_cast<size_t>(length));
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return applied;
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_state(JNIEnv *env, jobject, jlong handle) {
    const auto state = rw_context_state(context(handle));
    std::ostringstream out;
    out << state.identity << '|' << state.model << '|' << state.mode << '|' << state.vfo_a_hz << '|'
        << state.vfo_b_hz << '|' << state.connected << '|' << state.transmitting << '|' << state.meter << '|'
        << state.swr_tenths << '|' << state.rf_output_tenths << '|' << state.af_gain << '|' << state.rf_gain << '|'
        << state.bandwidth_hz << '|' << state.power_w << '|' << state.preamp << '|' << state.attenuator << '|'
        << state.rit << '|' << state.xit << '|' << state.rx_vfo << '|' << state.tx_vfo << '|' << state.split << '|'
        << state.agc_mode << '|' << state.cwt << '|' << state.monitor_level << '|' << state.mic_gain << '|'
        << state.keyer_speed << '|' << state.if_shift_hz << '|' << state.revision;
    out << '|' << state.rit_xit_offset_hz << '|' << state.effective_rx_hz << '|'
        << state.effective_tx_hz << '|' << state.data_submode << '|' << state.updated_monotonic_ms;
    return env->NewStringUTF(out.str().c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_app_rigweave_mobile_NativeCore_classify(JNIEnv *env, jobject, jstring command) {
    const auto value = utf(env, command);
    return static_cast<jint>(rw_classify_command(value.c_str()));
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_qsoIdentity(JNIEnv *env, jobject, jstring callsign, jstring timestamp,
                                                 jlong frequency, jstring mode) {
    const auto call = utf(env, callsign); const auto time = utf(env, timestamp); const auto modeValue = utf(env, mode);
    char output[32]{};
    rw_qso_identity(output, sizeof(output), call.c_str(), time.c_str(), static_cast<uint64_t>(frequency), modeValue.c_str());
    return env->NewStringUTF(output);
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_adif(JNIEnv *env, jobject, jstring identity, jstring callsign,
                                         jstring date, jstring time, jlong frequency, jstring mode,
                                         jstring sent, jstring received) {
    const auto id = utf(env, identity); const auto call = utf(env, callsign); const auto day = utf(env, date);
    const auto clock = utf(env, time); const auto modeValue = utf(env, mode); const auto s = utf(env, sent); const auto r = utf(env, received);
    char output[768]{};
    rw_adif_serialize(output, sizeof(output), id.c_str(), call.c_str(), day.c_str(), clock.c_str(),
                      static_cast<uint64_t>(frequency), modeValue.c_str(), s.c_str(), r.c_str());
    return env->NewStringUTF(output);
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_version(JNIEnv *env, jobject) {
    return env->NewStringUTF(rw_core_version());
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_rigweave_mobile_NativeCore_featureCreate(JNIEnv *, jobject) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(rw_feature_context_create()));
}

extern "C" JNIEXPORT void JNICALL
Java_app_rigweave_mobile_NativeCore_featureDestroy(JNIEnv *, jobject, jlong handle) {
    rw_feature_context_destroy(features(handle));
}

extern "C" JNIEXPORT void JNICALL
Java_app_rigweave_mobile_NativeCore_featureWatchlist(JNIEnv *env, jobject, jlong handle, jstring value) {
    const auto text = utf(env, value); rw_feature_set_watchlist(features(handle), text.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_rigweave_mobile_NativeCore_featureClusterLine(JNIEnv *env, jobject, jlong handle, jstring value, jlong epoch) {
    const auto text = utf(env, value);
    return rw_feature_ingest_cluster_line(features(handle), text.c_str(), static_cast<int64_t>(epoch)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_featureDxSnapshot(JNIEnv *env, jobject, jlong handle, jlong epoch) {
    std::string output(131072, '\0');
    const int size = rw_feature_dx_snapshot_json(features(handle), output.data(), output.size(), static_cast<int64_t>(epoch));
    return env->NewStringUTF(size > 0 ? output.c_str() : "{}");
}

extern "C" JNIEXPORT void JNICALL
Java_app_rigweave_mobile_NativeCore_featureSolar(JNIEnv *, jobject, jlong handle, jfloat flux, jfloat a, jfloat kp, jlong epoch) {
    rw_feature_set_solar(features(handle), flux, a, kp, static_cast<int64_t>(epoch));
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_rigweave_mobile_NativePanadapter_create(JNIEnv *, jobject) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(rw_panadapter_context_create()));
}

extern "C" JNIEXPORT void JNICALL
Java_app_rigweave_mobile_NativePanadapter_destroy(JNIEnv *, jobject, jlong handle) {
    rw_panadapter_context_destroy(panadapter(handle));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_rigweave_mobile_NativePanadapter_configure(
    JNIEnv *, jobject, jlong handle, jint sampleRate, jint fftSize, jint overlap, jint window,
    jfloat floorDb, jfloat topDb, jfloat attack, jfloat release, jint averageFrames,
    jboolean peakHold, jfloat peakDecay, jboolean flatness, jboolean swapIq,
    jboolean invertI, jboolean invertQ, jboolean conjugate, jfloat iTrim, jfloat qTrim,
    jint zoomDecimation, jfloat zoomOffset) {
    rw_panadapter_config config{};
    config.sample_rate = static_cast<uint32_t>(sampleRate);
    config.fft_size = static_cast<uint32_t>(fftSize);
    config.overlap_percent = static_cast<uint32_t>(overlap);
    config.window = static_cast<uint32_t>(window);
    config.display_floor_db = floorDb; config.display_top_db = topDb;
    config.attack = attack; config.release = release;
    config.average_frames = static_cast<uint32_t>(averageFrames);
    config.peak_hold = peakHold; config.peak_decay_db_per_second = peakDecay;
    config.generic_kx3_flatness = flatness; config.swap_iq = swapIq;
    config.invert_i = invertI; config.invert_q = invertQ; config.conjugate = conjugate;
    config.i_trim = iTrim; config.q_trim = qTrim;
    config.zoom_decimation = static_cast<uint32_t>(zoomDecimation);
    config.zoom_offset_hz = zoomOffset;
    return rw_panadapter_configure(panadapter(handle), &config) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_rigweave_mobile_NativePanadapter_push(JNIEnv *env, jobject, jlong handle,
                                                jshortArray samples, jint sampleCount,
                                                jboolean discontinuity) {
    if (samples == nullptr || sampleCount <= 0) return JNI_FALSE;
    const jsize available = env->GetArrayLength(samples);
    const jsize count = std::min(available, sampleCount);
    jshort *values = env->GetShortArrayElements(samples, nullptr);
    const int ready = rw_panadapter_push(panadapter(handle),
        reinterpret_cast<const uint8_t *>(values), static_cast<size_t>(count) * sizeof(jshort),
        2U, sizeof(jshort), 16U, discontinuity ? 1 : 0);
    env->ReleaseShortArrayElements(samples, values, JNI_ABORT);
    return ready ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_rigweave_mobile_NativePanadapter_snapshot(JNIEnv *env, jobject, jlong handle,
    jlongArray meta, jfloatArray metrics, jfloatArray trace, jfloatArray waterfall,
    jfloatArray peakHold) {
    if (meta == nullptr || metrics == nullptr || trace == nullptr || waterfall == nullptr || peakHold == nullptr ||
        env->GetArrayLength(meta) < 9 || env->GetArrayLength(metrics) < 14) return 0;
    const jsize capacity = std::min({env->GetArrayLength(trace), env->GetArrayLength(waterfall),
                                    env->GetArrayLength(peakHold)});
    jfloat *traceValues = env->GetFloatArrayElements(trace, nullptr);
    jfloat *waterfallValues = env->GetFloatArrayElements(waterfall, nullptr);
    jfloat *peakValues = env->GetFloatArrayElements(peakHold, nullptr);
    rw_panadapter_snapshot value{};
    const int count = rw_panadapter_copy_frame(panadapter(handle), &value, traceValues,
        waterfallValues, peakValues, static_cast<size_t>(capacity));
    env->ReleaseFloatArrayElements(trace, traceValues, 0);
    env->ReleaseFloatArrayElements(waterfall, waterfallValues, 0);
    env->ReleaseFloatArrayElements(peakHold, peakValues, 0);
    if (count <= 0) return 0;
    const jlong metaValues[9] = {
        static_cast<jlong>(value.sequence), static_cast<jlong>(value.input_frames),
        static_cast<jlong>(value.transforms), static_cast<jlong>(value.discontinuities),
        value.sample_rate, value.effective_sample_rate, value.fft_size, value.hop_size,
        value.zoom_decimation
    };
    const jfloat metricValues[14] = {
        value.zoom_offset_hz, value.enbw_bins, value.rbw_hz, value.peak_db, value.floor_db,
        value.i_rms_db, value.q_rms_db, value.iq_correlation, value.clipped_fraction,
        value.duplicate_correlation, value.raw_floor_db, value.stabilized_floor_db,
        value.valid_bin_fraction, static_cast<jfloat>(value.valid_bin_count)
    };
    env->SetLongArrayRegion(meta, 0, 9, metaValues);
    env->SetFloatArrayRegion(metrics, 0, 14, metricValues);
    return count;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_rigweave_mobile_NativePanadapter_setIqCorrection(JNIEnv *, jobject, jlong handle,
    jfloat aReal, jfloat aImag, jfloat bReal, jfloat bImag, jboolean enabled) {
    return rw_panadapter_set_iq_correction(panadapter(handle), aReal, aImag, bReal, bImag,
                                            enabled ? 1 : 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_app_rigweave_mobile_NativePanadapter_resetPeakHold(JNIEnv *, jobject, jlong handle) {
    rw_panadapter_reset_peak_hold(panadapter(handle));
}
