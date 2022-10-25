package org.japancv.sample.ui.main

import android.content.Intent
import android.os.Bundle
import org.japancv.sample.databinding.ActivityFailureBinding
import org.japancv.sample.ui.BaseActivity
import org.japancv.sample.ui.ktx.viewBinding

class FailureActivity : BaseActivity() {
    private val binding by viewBinding(ActivityFailureBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.retryBtn.setOnClickListener {
            startActivity(Intent(this@FailureActivity, CameraActivity::class.java))
            finish()
        }
    }
}
