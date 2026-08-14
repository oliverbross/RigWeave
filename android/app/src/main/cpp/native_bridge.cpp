#include <jni.h>
#include <cstdint>
#include <sstream>
#include <string>
#include "rigweave/core.h"

namespace {
rw_context *context(jlong handle) { return reinterpret_cast<rw_context *>(static_cast<intptr_t>(handle)); }
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
