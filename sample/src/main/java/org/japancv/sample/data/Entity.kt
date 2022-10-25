package org.japancv.sample.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Entity(
    @SerialName("uuid")       val UUID: String,
    @SerialName("model")      val model: String,
    @SerialName("collection") val collection: String,
    @SerialName("key")        val key: String,
    @SerialName("createdAt")  val createdAt: String,
    @SerialName("updatedAt")  val updatedAt: String,
    @SerialName("expiresAt")  val expiresAt: String,
)