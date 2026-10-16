package org.matrix.rustsdk

import android.content.Context

/**
 * Hands the Android [Context] to the native Matrix SDK library, which needs it for TLS certificate checks
 * against the system trust store. The class and method names must match the JNI symbol inside libmatrix_sdk_ffi.
 */
object Android {
    init {
        System.loadLibrary("matrix_sdk_ffi")
    }

    @JvmStatic
    external fun init(context: Context)
}
