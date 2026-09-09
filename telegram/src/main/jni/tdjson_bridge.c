/*
 * tdjson_bridge.c — thin JNI shim between Master Control (Kotlin) and the
 * TDLib JSON interface (libtdjson.so).
 *
 * We deliberately do NOT depend on TDLib's generated Java bindings
 * (TdApi.java): this shim forwards JSON strings to the official C API
 * (td_json_client_*) which needs no code generation (no PHP/gperf required).
 *
 * Function declarations match third_party/tdlib/td/telegram/td_json_client.h.
 * The native library is built by scripts/build-tdlib.sh and packaged under
 * telegram/src/main/jniLibs/<abi>/.
 *
 * Compiled with the Android NDK only (scripts/build-tdlib.sh); never compiled
 * by Gradle at app build time.
 */

#include <jni.h>
#include <stdint.h>

/* ---- TDLib JSON C API (subset used here) ---- */
typedef void *(*td_json_client_create_fn)(void);
typedef void (*td_json_client_destroy_fn)(void *client);
typedef void (*td_json_client_send_fn)(void *client, const char *request);
typedef const char *(*td_json_client_receive_fn)(void *client, double timeout);
typedef const char *(*td_json_client_execute_fn)(void *client, const char *request);
typedef void (*td_log_message_callback_fn)(int verbosity_level, const char *message);
typedef void (*td_set_log_message_callback_fn)(int max_verbosity_level, td_log_message_callback_fn callback);

static td_json_client_create_fn       g_create;
static td_json_client_destroy_fn      g_destroy;
static td_json_client_send_fn         g_send;
static td_json_client_receive_fn      g_receive;
static td_json_client_execute_fn      g_execute;
static td_set_log_message_callback_fn g_set_log_callback;

static JavaVM *g_vm = NULL;

/* Resolve a symbol from libtdjson.so. On Android both libraries are loaded
 * into the app linker namespace, so RTLD_DEFAULT lookup works.
 */
#if defined(__ANDROID__)
#include <dlfcn.h>
static void *resolve_symbol(const char *name) {
    void *sym = dlsym(RTLD_DEFAULT, name);
    return sym;
}
#else
#error "tdjson_bridge.c must be compiled for Android (NDK)."
#endif

/* ---- log callback -> Kotlin bridge ---- */
static void td_log_bridge(int verbosity_level, const char *message) {
    if (g_vm == NULL) return;
    JNIEnv *env = NULL;
    jint attached = (*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6);
    if (attached == JNI_EDETACHED) {
        if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) != JNI_OK) return;
    }
    if (env == NULL) return;
    jstring jMsg = (*env)->NewStringUTF(env, message != NULL ? message : "");
    jclass clazz = (*env)->FindClass(env, "com/mastercontrol/app/telegram/tdlib/jni/TdJsonJni");
    if (clazz != NULL) {
        jmethodID method = (*env)->GetStaticMethodID(env, clazz, "onNativeLog", "(ILjava/lang/String;)V");
        if (method != NULL) {
            (*env)->CallStaticVoidMethod(env, clazz, method, verbosity_level, jMsg);
        }
    }
    if (jMsg != NULL) (*env)->DeleteLocalRef(env, jMsg);
    if (attached == JNI_EDETACHED) {
        (*g_vm)->DetachCurrentThread(g_vm);
    }
}

/* ---- JNI methods ---- */

static jlong jni_create(JNIEnv *env, jobject thiz) {
    (void)env; (void)thiz;
    return (jlong)(intptr_t)g_create();
}

static void jni_destroy(JNIEnv *env, jobject thiz, jlong handle) {
    (void)env; (void)thiz;
    if (handle != 0) g_destroy((void *)(intptr_t)handle);
}

static void jni_send(JNIEnv *env, jobject thiz, jlong handle, jstring request) {
    (void)thiz;
    if (handle == 0 || request == NULL) return;
    const char *utf = (*env)->GetStringUTFChars(env, request, NULL);
    if (utf != NULL) {
        g_send((void *)(intptr_t)handle, utf);
        (*env)->ReleaseStringUTFChars(env, request, utf);
    }
}

static jstring jni_receive(JNIEnv *env, jobject thiz, jlong handle, jdouble timeout) {
    (void)thiz;
    if (handle == 0) return NULL;
    const char *result = g_receive((void *)(intptr_t)handle, timeout);
    if (result == NULL) return NULL;
    return (*env)->NewStringUTF(env, result);
}

static jstring jni_execute(JNIEnv *env, jobject thiz, jlong handle, jstring request) {
    (void)thiz;
    if (handle == 0 || request == NULL) return NULL;
    const char *utf = (*env)->GetStringUTFChars(env, request, NULL);
    const char *result = NULL;
    if (utf != NULL) {
        result = g_execute((void *)(intptr_t)handle, utf);
        (*env)->ReleaseStringUTFChars(env, request, utf);
    }
    if (result == NULL) return NULL;
    return (*env)->NewStringUTF(env, result);
}

static void jni_set_log_verbosity(JNIEnv *env, jobject thiz, jlong handle, jint verbosity) {
    (void)env; (void)thiz; (void)handle;
    if (g_set_log_callback != NULL) {
        g_set_log_callback(verbosity, td_log_bridge);
    }
}

static const JNINativeMethod kMethods[] = {
    {"nativeCreate",         "()J",                              (void *)jni_create},
    {"nativeDestroy",        "(J)V",                             (void *)jni_destroy},
    {"nativeSend",           "(JLjava/lang/String;)V",           (void *)jni_send},
    {"nativeReceive",        "(JD)Ljava/lang/String;",           (void *)jni_receive},
    {"nativeExecute",        "(JLjava/lang/String;)Ljava/lang/String;", (void *)jni_execute},
    {"nativeSetLogVerbosity","(JI)V",                            (void *)jni_set_log_verbosity},
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    g_vm = vm;

    g_create            = (td_json_client_create_fn)resolve_symbol("td_json_client_create");
    g_destroy           = (td_json_client_destroy_fn)resolve_symbol("td_json_client_destroy");
    g_send              = (td_json_client_send_fn)resolve_symbol("td_json_client_send");
    g_receive           = (td_json_client_receive_fn)resolve_symbol("td_json_client_receive");
    g_execute           = (td_json_client_execute_fn)resolve_symbol("td_json_client_execute");
    g_set_log_callback  = (td_set_log_message_callback_fn)resolve_symbol("td_set_log_message_callback");

    if (g_create == NULL || g_destroy == NULL || g_send == NULL || g_receive == NULL) {
        /* The app surfaces "TDLib native library missing" when this happens. */
        return JNI_VERSION_1_6;
    }

    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass clazz = (*env)->FindClass(env, "com/mastercontrol/app/telegram/tdlib/jni/TdJsonJni");
    if (clazz == NULL) return JNI_ERR;
    if ((*env)->RegisterNatives(env, clazz, kMethods, sizeof(kMethods) / sizeof(kMethods[0])) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
