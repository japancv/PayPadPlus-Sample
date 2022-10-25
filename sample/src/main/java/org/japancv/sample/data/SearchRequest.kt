package org.japancv.sample.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchRequest(
    @SerialName("model")       val model:String,
    @SerialName("maxEntities") val maxEntities: Int,
    @SerialName("threshold")   val threshold: Double,
    @SerialName("collection")  val collection: String,
    @SerialName("image")       val image: Image
)
