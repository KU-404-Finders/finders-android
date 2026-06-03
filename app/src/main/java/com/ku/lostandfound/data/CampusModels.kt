package com.ku.lostandfound.data

/**
 * GeoJSON coordinate order: [longitude, latitude]
 */
data class GeoPoint(
    val longitude: Double,
    val latitude: Double,
)

data class CampusBoundary(
    val polygon: List<GeoPoint>,
)

data class CampusBuilding(
    val id: String,
    val name: String,
    val levels: Int,
    val undergroundLevels: Int,
    /** Polygon rings. ring[0] = exterior, ring[1..] = holes. */
    val rings: List<List<GeoPoint>>,
) {
    val outerRing: List<GeoPoint>
        get() = rings.firstOrNull().orEmpty()
}

/**
 * 학교 내부 보행 경로/도로 참고선.
 * 사용자가 선택하는 대상이 아니라 지도 위에 흐리게 깔리는 프론트 전용 참고 데이터다.
 */
data class CampusPath(
    val id: String,
    val sourceId: String,
    val geometryType: String,
    val highway: String,
    val closed: Boolean,
    val coordinate: List<GeoPoint>,
)

enum class PostType(val label: String) {
    LOST("분실물"),
    FOUND("습득물"),
}

enum class PostStatus(val label: String) {
    OPEN("미해결"),
    RESOLVED("해결됨"),
}

enum class LocationTab(val label: String) {
    OUTDOOR("외부"),
    INDOOR("내부"),
}

data class OutdoorPin(
    val order: Int,
    val point: GeoPoint,
)

data class IndoorPlace(
    val buildingId: String,
    val buildingName: String,
    val floor: Int,
)

data class LostLocationSelection(
    /** 분실물 외부 경로: 최대 10개, 순서 저장 O, 경로 1개 */
    val outdoorPins: List<OutdoorPin> = emptyList(),
    /** 분실물 내부 건물/층: 최대 3개, 순서 저장 X, 경로 X */
    val indoorPlaces: List<IndoorPlace> = emptyList(),
)

data class FoundLocationSelection(
    /** 습득물 외부 위치: 핀 1개 */
    val outdoorPin: OutdoorPin? = null,
    /** 습득물 내부 위치: 건물/층 1개 */
    val indoorPlace: IndoorPlace? = null,
)

data class BoardPost(
    val id: String,
    val type: PostType,
    val status: PostStatus = PostStatus.OPEN,
    val authorUserId: Long? = null,
    val title: String,
    val category: String,
    val content: String,
    val imageUri: String? = null,
    val authorName: String = "익명",
    val authorEmail: String = "",
    val createdAtText: String = "2026-03-22",
    val lostLocation: LostLocationSelection? = null,
    val foundLocation: FoundLocationSelection? = null,
    val associatedBuildingNames: List<String> = emptyList(),
)

data class BoardComment(
    val id: String,
    val postId: String,
    val authorUserId: Long? = null,
    val authorName: String,
    val authorEmail: String,
    val content: String,
    val createdAtText: String = "방금 전",
)
