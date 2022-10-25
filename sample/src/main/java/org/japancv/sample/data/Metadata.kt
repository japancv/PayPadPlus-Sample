package org.japancv.sample.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Metadata(
    @SerialName("name")  val name: String,
    @SerialName("type")  val type: String,
    @SerialName("value") val value: String,
)
