package org.japancv.sample.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateRequest(
    @SerialName("model")      val model: String,
    @SerialName("class")      val clazz: String,
    @SerialName("collection") val collection: String,
    @SerialName("key")        val key: String,
    @SerialName("ttl")        val timeToLive: Long,
    @SerialName("metadata")   val metadata: List<Metadata>,
    @SerialName("image")      val image: Image,
)
