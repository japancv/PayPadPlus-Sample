package org.japancv.sample.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Details(
    @SerialName("position")   val position: Boolean = false,
    @SerialName("angle")      val angle:Boolean = false,
    @SerialName("landmarks")  val landmarks: Boolean = false,
    @SerialName("quality")    val quality: Boolean = true,
    @SerialName("attributes") val attributes: Boolean = true,
)