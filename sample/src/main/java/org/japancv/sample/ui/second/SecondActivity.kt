package org.japancv.sample.ui.second

import android.os.Bundle
import org.japancv.sample.databinding.ActivitySecondBinding
import org.japancv.sample.ui.BaseActivity
import org.japancv.sample.ui.ktx.viewBinding

class SecondActivity : BaseActivity() {
    private val binding by viewBinding(ActivitySecondBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
    }
}
