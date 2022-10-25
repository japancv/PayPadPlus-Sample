package org.japancv.sample.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.japancv.sample.ui.BaseActivity
import org.japancv.sample.ui.ktx.launchSecondScreen
import timber.log.Timber

class MainActivity : BaseActivity() {
    private val contentHasLoaded = MutableStateFlow<Result<Boolean>?>(null)

    init {
        lifecycleScope.launchWhenStarted {
            launchSecondScreen()
            requestCameraPermission()
            contentHasLoaded.emit(Result.success(true))
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach {
            val permissionName = it.key
            val isGranted = it.value
            if (isGranted) {
                Timber.i( "${permissionName}: Permission granted")
                navigateToCameraActivity()
            } else {
                Timber.i("${permissionName}: Permission denied")
                Toast.makeText(
                    this@MainActivity,
                    "Please grand the camera permission in Settings",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun hasPermissions(vararg permissions: String?): Int {
        var isGranted = PackageManager.PERMISSION_GRANTED

        for (permission in permissions) {
            isGranted = isGranted or ContextCompat.checkSelfPermission(
                this,
                permission!!
            )
        }
        return isGranted
    }

    private fun requestCameraPermission() {
        when {
            hasPermissions(
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED -> {
                Timber.i("Permission previously granted")
                navigateToCameraActivity()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            ) -> {
                Timber.i("Show camera permissions dialog")
                requestPermissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA)
                )
            }

            else -> requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA)
            )
        }
    }

    private fun navigateToCameraActivity() {
        startActivity(Intent(this, CameraActivity::class.java))
        finish()
    }
}
