package org.japancv.sample.ui.main

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import org.japancv.sample.databinding.ActivityWelcomeBinding
import org.japancv.sample.ui.BaseActivity
import org.japancv.sample.ui.ktx.launchSecondScreen
import org.japancv.sample.ui.ktx.linearGradient
import org.japancv.sample.ui.ktx.viewBinding
import org.japancv.sample.ui.second.WelcomeActivity

class WelcomeActivity : BaseActivity() {
    private val binding by viewBinding(ActivityWelcomeBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        launchSecondScreen(WelcomeActivity::class.java)
        binding.tvWelcome.linearGradient(
            Color.parseColor("#4187F1"),
            Color.parseColor("#1AC1C1")
        )
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && keyCode == KeyEvent.KEYCODE_BACK) {
            startActivity(Intent(this, CameraActivity::class.java))
            finish()
            return true
        }

        return super.onKeyUp(keyCode, event)
    }
}
