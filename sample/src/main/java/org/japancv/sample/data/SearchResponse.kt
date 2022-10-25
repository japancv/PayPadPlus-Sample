package org.japancv.sample.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    @SerialName("model")       val model: String,
    @SerialName("count")       val count: Int,
    @SerialName("maxEntities") val maxEntities: Int,
    @SerialName("threshold")   val threshold: Double,
    @SerialName("entitiesFound") val entitiesFound: List<Entity>,
)