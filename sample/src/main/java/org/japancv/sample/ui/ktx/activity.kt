package org.japancv.sample.ui.ktx

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.flow.StateFlow
import org.japancv.sample.ui.second.SecondActivity

inline fun <T : ViewBinding> ComponentActivity.viewBinding(
    crossinline bindingInflater: (LayoutInflater) -> T
) =
    lazy(LazyThreadSafetyMode.NONE) {
        bindingInflater.invoke(layoutInflater)
    }

@RequiresApi(Build.VERSION_CODES.Q)
internal fun Activity.launchSecondScreen(clazz: Class<*> = SecondActivity::class.java) {
    // DisplayManager manages the properties of attached displays.
    val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    // List displays was attached
    val displays = displayManager.displays

    if (displays.size > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // To display on the second screen that your intent must be set flag to make
        // single task or single instance (combine FLAG_ACTIVITY_CLEAR_TOP and FLAG_ACTIVITY_NEW_TASK)
        // or you also set it in the manifest (see more at the manifest file)
        val intent = Intent(this, clazz)

        if (activityManager.isActivityStartAllowedOnDisplay(this, displays[1].displayId, intent)) {
            // Activity options are used to select the display screen.
            val options = ActivityOptions.makeBasic()
            // Select the display screen that you want to show the second activity
            options.launchDisplayId = displays[1].displayId
            startActivity(
                intent,
                options.toBundle()
            )
        }
    }
}

internal fun Activity.suspendSplashScreenIfNeeded(contentView: View, flow: StateFlow<Result<Boolean>?>) {
    contentView.viewTreeObserver.addOnPreDrawListener(
        object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                return if (flow.value?.isSuccess == true) {
                    contentView.viewTreeObserver.removeOnPreDrawListener(this)
                    true
                } else false
            }
        }
    )
}