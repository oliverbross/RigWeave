#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <functional>
#include <sstream>
#include <string>
#include "rigweave/core.h"
#include "rigweave_flex.h"

namespace {
rw_context *context(jlong handle) { return reinterpret_cast<rw_context *>(static_cast<intptr_t>(handle)); }
rw_feature_context *features(jlong handle) { return reinterpret_cast<rw_feature_context *>(static_cast<intptr_t>(handle)); }
rw_panadapter_context *panadapter(jlong handle) { return reinterpret_cast<rw_panadapter_context *>(static_cast<intptr_t>(handle)); }
rw_flex_context *flex(jlong handle) { return reinterpret_cast<rw_flex_context *>(static_cast<intptr_t>(handle)); }
rw_digi_context *digi(jlong handle) { return reinterpret_cast<rw_digi_context *>(static_cast<intptr_t>(handle)); }
std::string utf(JNIEnv *env, jstring value) {
    if (!value) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_rigweave_mobile_NativeCore_flexCreate(JNIEnv *, jobject) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(rw_flex_context_create()));
}

extern "C" JNIEXPORT void JNICALL
Java_app_rigweave_mobile_NativeCore_flexDestroy(JNIEnv *, jobject, jlong handle) {
    rw_flex_context_destroy(flex(handle));
}

extern "C" JNIEXPORT jint JNICALL
Java_app_rigweave_mobile_NativeCore_flexFeed(JNIEnv *env, jobject, jlong handle, jbyteArray data) {
    if (!data) return -1;
    const jsize length = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    const int applied = rw_flex_context_feed(flex(handle), reinterpret_cast<const uint8_t *>(bytes), static_cast<size_t>(length));
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return applied;
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_flexState(JNIEnv *env, jobject, jlong handle) {
    std::string output(65536, '\0');
    const int size = rw_flex_state_json(flex(handle), output.data(), output.size());
    return env->NewStringUTF(size >= 0 ? output.c_str() : "{}");
}

namespace {
jstring flex_text(JNIEnv *env, const std::function<int(char *, size_t)> &builder) {
    char output[512]{};
    return env->NewStringUTF(builder(output, sizeof(output)) >= 0 ? output : "");
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_flexIdentity(JNIEnv *env, jobject, jstring program) {
    const auto value = utf(env, program); return flex_text(env, [&](char *out, size_t size) { return rw_flex_client_identity(value.c_str(), out, size); });
}
extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_flexSubscriptions(JNIEnv *env, jobject) { return flex_text(env, rw_flex_subscriptions); }
extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_flexKeepalive(JNIEnv *env, jobject) { return flex_text(env, rw_flex_keepalive); }
extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_flexFrequency(JNIEnv *env, jobject, jint slice, jlong hz) {
    return flex_text(env, [&](char *out, size_t size) { return rw_flex_frequency(static_cast<uint32_t>(slice), static_cast<uint64_t>(hz), out, size); });
}
extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_flexMode(JNIEnv *env, jobject, jint slice, jstring mode) {
    const auto value = utf(env, mode); return flex_text(env, [&](char *out, size_t size) { return rw_flex_mode(static_cast<uint32_t>(slice), value.c_str(), out, size); });
}
extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_flexFilter(JNIEnv *env, jobject, jstring letter, jint low, jint high) {
    const auto value = utf(env, letter); return flex_text(env, [&](char *out, size_t size) { return rw_flex_filter(value.c_str(), low, high, out, size); });
}
extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_flexParseDiscovery(JNIEnv *env, jobject, jbyteArray data) {
    if (!data) return env->NewStringUTF("");
    const jsize length = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    char output[4096]{};
    const int size = rw_flex_parse_discovery(reinterpret_cast<const uint8_t *>(bytes), static_cast<size_t>(length), output, sizeof(output));
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return env->NewStringUTF(size >= 0 ? output : "");
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_rigweave_mobile_NativeCore_digiCreate(JNIEnv *, jobject, jint sampleRate, jfloat pitch, jboolean reverse) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(
        rw_digi_context_create(static_cast<uint32_t>(sampleRate), pitch, reverse == JNI_TRUE)));
}

extern "C" JNIEXPORT void JNICALL
Java_app_rigweave_mobile_NativeCore_digiDestroy(JNIEnv *, jobject, jlong handle) {
    rw_digi_context_destroy(digi(handle));
}

jstring digi_feed(JNIEnv *env, jlong handle, jfloatArray data,
                  const std::function<int(rw_digi_context *, const float *, size_t, char *, size_t)> &feed) {
    if (!data || !handle) return env->NewStringUTF("{}");
    const jsize length = env->GetArrayLength(data);
    jfloat *samples = env->GetFloatArrayElements(data, nullptr);
    std::string output(16384, '\0');
    const int size = feed(digi(handle), samples, static_cast<size_t>(length), output.data(), output.size());
    env->ReleaseFloatArrayElements(data, samples, JNI_ABORT);
    return env->NewStringUTF(size >= 0 ? output.c_str() : "{}");
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_digiFeedCw(JNIEnv *env, jobject, jlong handle, jfloatArray data) {
    return digi_feed(env, handle, data, rw_digi_feed_cw);
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_digiFeedRtty(JNIEnv *env, jobject, jlong handle, jfloatArray data) {
    return digi_feed(env, handle, data, rw_digi_feed_rtty);
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_digiFeedSstv(JNIEnv *env, jobject, jlong handle, jfloatArray data) {
    return digi_feed(env, handle, data, rw_digi_feed_sstv);
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_digiDecodeSlot(JNIEnv *env, jobject, jint mode, jfloatArray data, jint sampleRate) {
    if (!data) return env->NewStringUTF("{\"error\":\"No audio\",\"decodes\":[]}");
    const jsize length = env->GetArrayLength(data);
    jfloat *samples = env->GetFloatArrayElements(data, nullptr);
    std::string output(262144, '\0');
    const int size = rw_digi_decode_slot(mode, samples, static_cast<size_t>(length),
                                         static_cast<uint32_t>(sampleRate), output.data(), output.size());
    env->ReleaseFloatArrayElements(data, samples, JNI_ABORT);
    return env->NewStringUTF(size >= 0 ? output.c_str() : "{\"error\":\"Decode failed\",\"decodes\":[]}");
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_digiDecodePsk31(JNIEnv *env, jobject, jfloatArray data) {
    if (!data) return env->NewStringUTF("{}");
    const jsize length = env->GetArrayLength(data);
    jfloat *samples = env->GetFloatArrayElements(data, nullptr);
    std::string output(65536, '\0');
    const int size = rw_digi_decode_psk31(samples, static_cast<size_t>(length), output.data(), output.size());
    env->ReleaseFloatArrayElements(data, samples, JNI_ABORT);
    return env->NewStringUTF(size >= 0 ? output.c_str() : "{}");
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_app_rigweave_mobile_NativeCore_digiSstvImage(JNIEnv *env, jobject, jlong handle) {
    const int size = rw_digi_copy_sstv_image(digi(handle), nullptr, 0);
    if (size <= 0) return env->NewByteArray(0);
    jbyteArray result = env->NewByteArray(size);
    jbyte *bytes = env->GetByteArrayElements(result, nullptr);
    rw_digi_copy_sstv_image(digi(handle), reinterpret_cast<uint8_t *>(bytes), static_cast<size_t>(size));
    env->ReleaseByteArrayElements(result, bytes, 0);
    return result;
}

jfloatArray digi_samples(JNIEnv *env, const std::function<int(float *, size_t)> &encode) {
    const int size = encode(nullptr, 0);
    if (size <= 0) return env->NewFloatArray(0);
    jfloatArray result = env->NewFloatArray(size);
    jfloat *samples = env->GetFloatArrayElements(result, nullptr);
    encode(samples, static_cast<size_t>(size));
    env->ReleaseFloatArrayElements(result, samples, 0);
    return result;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_app_rigweave_mobile_NativeCore_digiEncodeCw(JNIEnv *env, jobject, jstring text, jint wpm, jfloat pitch, jint sampleRate) {
    const auto value = utf(env, text);
    return digi_samples(env, [&](float *out, size_t count) {
        return rw_digi_encode_cw(value.c_str(), static_cast<uint32_t>(wpm), pitch,
                                 static_cast<uint32_t>(sampleRate), out, count);
    });
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_app_rigweave_mobile_NativeCore_digiEncodeRtty(JNIEnv *env, jobject, jstring text, jint sampleRate, jboolean reverse) {
    const auto value = utf(env, text);
    return digi_samples(env, [&](float *out, size_t count) {
        return rw_digi_encode_rtty(value.c_str(), static_cast<uint32_t>(sampleRate),
                                   reverse == JNI_TRUE, out, count);
    });
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_app_rigweave_mobile_NativeCore_digiEncodeSlot(JNIEnv *env, jobject, jint mode, jstring text, jfloat baseHz) {
    const auto value = utf(env, text);
    return digi_samples(env, [&](float *out, size_t count) {
        return rw_digi_encode_slot(mode, value.c_str(), baseHz, out, count);
    });
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_app_rigweave_mobile_NativeCore_digiEncodePsk31(JNIEnv *env, jobject, jstring text, jfloat carrierHz) {
    const auto value = utf(env, text);
    return digi_samples(env, [&](float *out, size_t count) {
        return rw_digi_encode_psk31(value.c_str(), carrierHz, out, count);
    });
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_app_rigweave_mobile_NativeCore_digiEncodeSstv(JNIEnv *env, jobject, jint mode, jbyteArray rgb,
                                                    jint width, jint height, jint sampleRate) {
    if (!rgb) return env->NewFloatArray(0);
    jbyte *bytes = env->GetByteArrayElements(rgb, nullptr);
    auto encode = [&](float *out, size_t count) {
        return rw_digi_encode_sstv(mode, reinterpret_cast<const uint8_t *>(bytes),
                                   static_cast<uint32_t>(width), static_cast<uint32_t>(height),
                                   static_cast<uint32_t>(sampleRate), out, count);
    };
    jfloatArray result = digi_samples(env, encode);
    env->ReleaseByteArrayElements(rgb, bytes, JNI_ABORT);
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
Java_app_rigweave_mobile_NativeCore_featureLoadCty(JNIEnv *env, jobject, jlong handle, jstring value) {
    const auto text = utf(env, value);
    return rw_feature_load_cty_text(features(handle), text.c_str()) ? JNI_TRUE : JNI_FALSE;
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

extern "C" JNIEXPORT jboolean JNICALL
Java_app_rigweave_mobile_NativeCore_featureBeginWorkedSync(JNIEnv *, jobject, jlong handle) {
    return rw_feature_begin_worked_sync(features(handle)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_rigweave_mobile_NativeCore_featureAddWorkedQso(JNIEnv *env, jobject, jlong handle,
        jstring callsign, jstring entity, jstring band, jstring mode, jstring submode,
        jlong epoch, jboolean from_wavelog) {
    const auto call_text = utf(env, callsign);
    const auto entity_text = utf(env, entity);
    const auto band_text = utf(env, band);
    const auto mode_text = utf(env, mode);
    const auto submode_text = utf(env, submode);
    return rw_feature_add_worked_qso(features(handle), call_text.c_str(), entity_text.c_str(),
        band_text.c_str(), mode_text.c_str(), submode_text.c_str(), static_cast<int64_t>(epoch),
        from_wavelog == JNI_TRUE ? 1 : 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_rigweave_mobile_NativeCore_featureEndWorkedSync(JNIEnv *, jobject, jlong handle) {
    return rw_feature_end_worked_sync(features(handle)) ? JNI_TRUE : JNI_FALSE;
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
