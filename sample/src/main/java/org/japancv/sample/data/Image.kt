package org.japancv.sample.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Image(
    @SerialName("autoRotate")    val autoRotate: Boolean = true,
    @SerialName("returnDetails") val details: Details,
    @SerialName("data")          val faceDataInBase64: String,
)