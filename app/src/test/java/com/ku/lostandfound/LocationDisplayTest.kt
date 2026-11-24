package com.ku.lostandfound

import com.google.gson.Gson
import com.ku.lostandfound.network.*
import com.ku.lostandfound.ui.profile.screen.locationDisplayText
import com.ku.lostandfound.ui.profile.screen.locationTypeLabel
import org.junit.Assert.*
import org.junit.Test

class LocationDisplayTest {
    @Test fun supportedContractValuesHaveKoreanLabels() {
        assertEquals("실내", locationTypeLabel("INDOOR"))
        assertEquals("실외", locationTypeLabel("OUTDOOR"))
        assertEquals("실외 · 학생회관, 인문학관", locationDisplayText("OUTDOOR", listOf("학생회관", "인문학관")))
    }
    @Test fun missingAndUnsupportedTypesDoNotLeakWireValues() {
        for (type in listOf(null, "", " ", "UNKNOWN", "null")) {
            assertEquals("학생회관", locationDisplayText(type, listOf("학생회관")))
            assertEquals("", locationDisplayText(type, null))
        }
    }
    @Test fun missingNamesDoNotAddSeparatorsOrNullText() {
        assertEquals("실내", locationDisplayText("INDOOR", null))
        assertEquals("실외", locationDisplayText("OUTDOOR", listOf(null, "", " ")))
        assertEquals("실내 · 학생회관", locationDisplayText("INDOOR", listOf(null, " ", " 학생회관 ", "")))
        assertEquals("", locationDisplayText(null, emptyList()))
    }
    @Test fun foundCreateAndEditLocationSerializationRetainsOriginalEnums() {
        val gson = Gson()
        for (type in FoundItemLocationType.entries) {
            val location = FoundItemLocationRequest(type = type, buildingName = "학생회관")
            locationDisplayText(type.name, listOf(location.buildingName))
            val request = FoundItemCreateRequest("제목", "종류", "내용", location)
            assertEquals(type.name, gson.toJsonTree(request).asJsonObject
                .getAsJsonObject("location").get("type").asString)
            assertEquals(type, gson.fromJson(gson.toJson(request), FoundItemCreateRequest::class.java).location.type)
        }
    }
    @Test fun lostRequestRetainsIndoorSpotsAndOutdoorPointsContract() {
        val request = LostItemCreateRequest("제목", "종류", "내용",
            listOf(LostItemIndoorRequest("학생회관", 1)), listOf(LostItemOutdoorRequest(37.5, 127.0)))
        val json = Gson().toJsonTree(request).asJsonObject
        assertEquals("학생회관", json.getAsJsonArray("indoorSpots")[0].asJsonObject.get("buildingName").asString)
        assertEquals(37.5, json.getAsJsonArray("outdoorPoints")[0].asJsonObject.get("latitude").asDouble, 0.0)
        assertFalse(json.has("locationType"))
    }
}
