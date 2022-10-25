package org.japancv.sample.ui.ktx

import android.graphics.LinearGradient
import android.graphics.Shader
import android.widget.TextView

fun TextView.linearGradient(vararg colors: Int) {
    val width = paint.measureText(text.toString())
    val textShader = LinearGradient(0f, 0f, width, this.textSize, colors, null, Shader.TileMode.CLAMP)
    paint.shader = textShader
}