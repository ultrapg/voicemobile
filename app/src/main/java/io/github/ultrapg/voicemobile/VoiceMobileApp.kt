package io.github.ultrapg.voicemobile

import android.app.Application
import android.system.Os

class VoiceMobileApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Pre-configure Qualcomm Hexagon FastRPC / CDSP search path at process startup so
        // libggml-htp-v73..v85.so inside nativeLibraryDir are immediately visible to libcdsprpc.so.
        runCatching {
            val nativeDir = applicationInfo.nativeLibraryDir
            val dspSearchPath = "$nativeDir;/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/system/vendor/lib/rfsa/adsp"
            Os.setenv("ADSP_LIBRARY_PATH", dspSearchPath, true)
            Os.setenv("CDSP_LIBRARY_PATH", dspSearchPath, true)
            Os.setenv("QNN_HTP_ENABLE_PDR", "1", true)
            Os.setenv("FASTRPC_PERF_KERNEL", "1", true)
        }
    }
}
