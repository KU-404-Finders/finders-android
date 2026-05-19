package com.ku.lostandfound.serialization

import com.ku.lostandfound.data.BoardPost
import com.ku.lostandfound.data.FoundLocationSelection
import com.ku.lostandfound.data.GeoPoint
import com.ku.lostandfound.data.IndoorPlace
import com.ku.lostandfound.data.LostLocationSelection
import com.ku.lostandfound.data.OutdoorPin
import com.ku.lostandfound.data.PostType
import org.json.JSONArray
import org.json.JSONObject

/**
 * 게시글 저장용 JSON mapper.
 * 요구사항: count 필드는 저장하지 않음.
 */
object PostJsonMapper {

    fun toJson(post: BoardPost): JSONObject = JSONObject().apply {
        put("id", post.id)
        put("type", post.type.name)
        put("status", post.status.name)
        put("title", post.title)
        put("category", post.category)
        put("content", post.content)
        post.imageUri?.let { put("imageUri", it) }
        put("authorName", post.authorName)
        put("authorEmail", post.authorEmail)
        put("createdAtText", post.createdAtText)

        when (post.type) {
            PostType.LOST -> put("location", post.lostLocation.orEmpty().toJson())
            PostType.FOUND -> put("location", post.foundLocation.orEmpty().toJson())
        }
    }

    private fun LostLocationSelection.toJson(): JSONObject = JSONObject().apply {
        // 경로 1개만 사용. 단, count는 넣지 않음.
        put("outdoorRoute", JSONArray().apply {
            outdoorPins.sortedBy { it.order }.forEach { put(it.toJson()) }
        })
        put("indoorPlaces", JSONArray().apply {
            indoorPlaces.forEach { put(it.toJson()) }
        })
    }

    private fun FoundLocationSelection.toJson(): JSONObject = JSONObject().apply {
        outdoorPin?.let { put("outdoorPin", it.toJson()) }
        indoorPlace?.let { put("indoorPlace", it.toJson()) }
    }

    private fun OutdoorPin.toJson(): JSONObject = JSONObject().apply {
        put("order", order)
        put("longitude", point.longitude)
        put("latitude", point.latitude)
    }

    private fun IndoorPlace.toJson(): JSONObject = JSONObject().apply {
        put("buildingId", buildingId)
        put("buildingName", buildingName)
        put("floor", floor)
    }

    private fun LostLocationSelection?.orEmpty(): LostLocationSelection = this ?: LostLocationSelection()
    private fun FoundLocationSelection?.orEmpty(): FoundLocationSelection = this ?: FoundLocationSelection()
}

fun GeoPoint.toJsonObject(): JSONObject = JSONObject().apply {
    put("longitude", longitude)
    put("latitude", latitude)
}
