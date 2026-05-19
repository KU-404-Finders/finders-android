package com.ku.lostandfound.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ku.lostandfound.data.FoundLocationSelection
import com.ku.lostandfound.data.GeoPoint
import com.ku.lostandfound.data.IndoorPlace
import com.ku.lostandfound.data.LostLocationSelection
import com.ku.lostandfound.data.OutdoorPin
import com.ku.lostandfound.data.PostType

class PostWriteViewModel {
    var postType by mutableStateOf(PostType.FOUND)
        private set
    var title by mutableStateOf("")
    var category by mutableStateOf("")
    var content by mutableStateOf("")
    var imageUri by mutableStateOf<String?>(null)

    var lostLocation by mutableStateOf(LostLocationSelection())
        private set
    var foundLocation by mutableStateOf(FoundLocationSelection())
        private set

    fun changePostType(type: PostType) {
        postType = type
    }

    fun setLostOutdoorPins(points: List<GeoPoint>) {
        lostLocation = lostLocation.copy(
            outdoorPins = points.take(10).mapIndexed { index, point ->
                OutdoorPin(order = index + 1, point = point)
            }
        )
    }

    fun setLostIndoorPlaces(places: List<IndoorPlace>) {
        lostLocation = lostLocation.copy(
            indoorPlaces = places
                .distinctBy { it.buildingId to it.floor }
                .take(3)
        )
    }

    fun setFoundOutdoorPin(point: GeoPoint?) {
        foundLocation = foundLocation.copy(
            outdoorPin = point?.let { OutdoorPin(order = 1, point = it) },
            indoorPlace = null,
        )
    }

    fun setFoundIndoorPlace(place: IndoorPlace?) {
        foundLocation = foundLocation.copy(
            outdoorPin = null,
            indoorPlace = place,
        )
    }

    fun hasLocation(): Boolean = when (postType) {
        PostType.LOST -> lostLocation.outdoorPins.isNotEmpty() || lostLocation.indoorPlaces.isNotEmpty()
        PostType.FOUND -> foundLocation.outdoorPin != null || foundLocation.indoorPlace != null
    }

    fun canSubmit(): Boolean =
        title.isNotBlank() && category.isNotBlank() && content.isNotBlank() && hasLocation()

    fun reset() {
        postType = PostType.FOUND
        title = ""
        category = ""
        content = ""
        imageUri = null
        lostLocation = LostLocationSelection()
        foundLocation = FoundLocationSelection()
    }
}
