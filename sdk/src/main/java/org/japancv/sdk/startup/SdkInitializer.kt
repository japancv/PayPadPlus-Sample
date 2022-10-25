@file:JvmName("SdkInitializer")

package org.japancv.sdk.startup

import android.content.Context
import androidx.startup.Initializer
import com.nebula.irdc.sdk.ApiStatus
import com.nebula.irdc.sdk.NativeFaceSDK
import org.japancv.sdk.FileUtils
import org.japancv.sdk.SdkModule
import timber.log.Timber
import java.lang.IllegalStateException

/**
 * SdkInitializer helps to load SDK license and initiate SDK module for usage.
 * This initializer can be used independently
 * or integrated with [App Startup](https://developer.android.com/jetpack/androidx/releases/startup)
 */
class SdkInitializer : Initializer<SdkModule> {
    init {
        System.loadLibrary("nllvm")
    }

    /**
     * Load SDK License and check if running on the specific device
     */
    private fun loadLicense(context: Context): ApiStatus? {
        val result = FileUtils.getAssertFilePath(context, LICENSE_ASSET_PATH)?.let {
            var ret = NativeFaceSDK.NativeAddLicense(it)
            Timber.d("Add license status: %s", ret.code.toString())
            ret = NativeFaceSDK.NativeSetDevice(0)
            Timber.d("Set device status %s", ret.code.toString())

            ret
        }

        return result
    }

    private fun initSdk(context: Context) {
        SdkModule.instance.initSDK(context)
    }

    /**
     * Initializes and a component given the application [Context]
     *
     * @param context The application context.
     */
    override fun create(context: Context): SdkModule {
        if (loadLicense(context)?.isSuccess != true) {
            throw IllegalStateException("Load SDK license failed")
        }
        initSdk(context)

        return SdkModule.instance
    }
    override fun dependencies(): List<Class<out Initializer<*>>> {
        // No dependencies on other libraries.
        return emptyList()
    }

    companion object {
        private const val LICENSE_ASSET_PATH = "LICENSE.lic"
    }
}