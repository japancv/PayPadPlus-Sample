package org.japancv.sample.ui

import android.view.View
import androidx.activity.ComponentActivity
import org.japancv.sample.BuildConfig
import timber.log.Timber


open class BaseActivity: ComponentActivity() {

    init {
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }

    override fun onResume() {
        super.onResume()
    }

    private fun hideActionBar() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
//        supportActionBar?.hide()
    }

}