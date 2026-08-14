#include <jni.h>
#include <cstdint>
#include <sstream>
#include <string>
#include "rigweave/core.h"

namespace {
rw_context *context(jlong handle) { return reinterpret_cast<rw_context *>(static_cast<intptr_t>(handle)); }
rw_feature_context *features(jlong handle) { return reinterpret_cast<rw_feature_context *>(static_cast<intptr_t>(handle)); }
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
        << state.connected << '|' << state.transmitting << '|' << state.meter << '|' << state.revision;
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

extern "C" JNIEXPORT jfloatArray JNICALL
Java_app_rigweave_mobile_NativeCore_featurePanadapter(JNIEnv *env, jobject, jlong handle, jbyteArray pcm,
                                                       jint channels, jint subframeBytes, jint bits) {
    if (!pcm) return env->NewFloatArray(0);
    const auto length = env->GetArrayLength(pcm); auto *bytes = env->GetByteArrayElements(pcm, nullptr);
    const int accepted = rw_panadapter_push_pcm(features(handle), reinterpret_cast<const uint8_t *>(bytes),
        static_cast<size_t>(length), static_cast<unsigned>(channels), static_cast<unsigned>(subframeBytes), static_cast<unsigned>(bits));
    env->ReleaseByteArrayElements(pcm, bytes, JNI_ABORT);
    if (!accepted) return env->NewFloatArray(0);
    float bins[1024]{}; const auto count = rw_panadapter_copy_db_bins(features(handle), bins, 1024);
    auto result = env->NewFloatArray(static_cast<jsize>(count));
    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(count), bins);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_rigweave_mobile_NativeCore_featureWsjtx(JNIEnv *env, jobject, jbyteArray datagram) {
    if (!datagram) return env->NewStringUTF("{\"valid\":false,\"error\":\"EMPTY DATAGRAM\"}");
    const auto length = env->GetArrayLength(datagram); auto *bytes = env->GetByteArrayElements(datagram, nullptr);
    char output[65536]{}; rw_wsjtx_parse_json(output, sizeof(output), reinterpret_cast<const uint8_t *>(bytes), static_cast<size_t>(length));
    env->ReleaseByteArrayElements(datagram, bytes, JNI_ABORT); return env->NewStringUTF(output);
}
