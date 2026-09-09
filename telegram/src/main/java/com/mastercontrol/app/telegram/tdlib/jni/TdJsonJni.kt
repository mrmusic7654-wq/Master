package com.mastercontrol.app.telegram.tdlib.jni

/**
 * JNI entry points into libtdjson_bridge.so (a thin C shim around libtdjson.so).
 *
 * The bridge registers its native methods via JNI_OnLoad, so the package name
 * can change without touching C code. Native libraries live under
 * telegram/src/main/jniLibs/<abi>/ and are produced by scripts/build-tdlib.sh.
 */
object TdJsonJni {

    init {
        System.loadLibrary("tdjson_bridge")
    }

    /** Creates a TDLib JSON client; returns an opaque handle (0 on failure). */
    external fun nativeCreate(): Long

    external fun nativeDestroy(handle: Long)

    external fun nativeSend(handle: Long, requestJson: String)

    /** Blocking receive with a timeout in seconds; null when the timeout expired. */
    external fun nativeReceive(handle: Long, timeoutSeconds: Double): String?

    external fun nativeExecute(handle: Long, requestJson: String): String?

    /** Verbosity for the log callback bridge (values 0..5; >5 for diagnostics). */
    external fun nativeSetLogVerbosity(handle: Long, verbosity: Int)

    /** Called from C when TDLib emits an internal log line (never on the main thread). */
    @JvmStatic
    fun onNativeLog(verbosityLevel: Int, message: String) {
        TdNativeLogSink.onLog(verbosityLevel, message)
    }
}

/** Sink installed by the TdLibManager; forwards TDLib logs through the app logger. */
object TdNativeLogSink {
    @Volatile
    var onLog: (Int, String) -> Unit = { _, _ -> }
}
