package org.japancv.sample.ui.second

import android.graphics.Color
import android.os.Bundle
import org.japancv.sample.databinding.ActivitySecondWelcomeBinding
import org.japancv.sample.ui.BaseActivity
import org.japancv.sample.ui.ktx.linearGradient
import org.japancv.sample.ui.ktx.viewBinding

class WelcomeActivity : BaseActivity() {
    private val binding by viewBinding(ActivitySecondWelcomeBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.tvWelcome.linearGradient(
            Color.parseColor("#4187F1"),
            Color.parseColor("#1AC1C1")
        )
    }
}
