package com.ku.lostandfound.network

data class LostItemCreateRequest(
    val title: String,
    val kind: String,
    val content: String,
    val indoorSpots: List<LostItemIndoorRequest>,
    val outdoorPoints: List<LostItemOutdoorRequest>,
)

data class LostItemIndoorRequest(
    val buildingName: String,
    val floor: Int,
)

data class LostItemOutdoorRequest(
    val latitude: Double,
    val longitude: Double,
)

data class LostItemListResponse(
    val success: Boolean,
    val message: String,
    val data: List<LostItemSummaryData>?,
    val errors: Map<String, String>? = null,
)

data class LostItemSummaryData(
    val id: Long,
    val title: String,
    val kind: String,
    val imageUrl: String?,
    val itemStatus: String,
    val createdAt: String,
)

data class LostItemCreateResponse(
    val success: Boolean,
    val message: String,
    val data: LostItemData?,
    val errors: Map<String, String>? = null,
)

data class LostItemData(
    val id: Long,
    val userId: Long,
    val authorName: String? = null,
    val title: String,
    val kind: String,
    val content: String,
    val imageUrl: String?,
    val itemStatus: String,
    val indoorSpots: List<LostItemIndoorData>,
    val outdoorPoints: List<LostItemOutdoorData>,
    val createdAt: String,
    val updatedAt: String,
)

data class LostItemIndoorData(
    val buildingName: String,
    val floor: Int,
    val sortOrder: Int? = null,
)

data class LostItemOutdoorData(
    val latitude: Double,
    val longitude: Double,
    val sortOrder: Int,
)

data class LostItemErrorResponse(
    val success: Boolean,
    val message: String,
    val data: Nothing? = null,
    val errors: Map<String, String>? = null,
)

data class LostItemMatchesResponse(
    val success: Boolean,
    val message: String,
    val data: LostItemMatchesResult?,
    val errors: Map<String, String>? = null,
)

data class LostItemMatchesResult(
    val status: MatchStatus,
    val matches: List<LostItemMatchData> = emptyList(),
)

data class LostItemMatchData(
    val locationScore: Double,
    val foundItem: MatchedFoundItemData,
)

data class MatchedFoundItemData(
    val id: Long,
    val title: String,
    val kind: String,
    val imageUrl: String?,
    val itemStatus: String,
    val locationType: String,
    val associatedBuildingNames: List<String>,
    val createdAt: String,
)
