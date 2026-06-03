package com.ku.lostandfound.network

data class FoundItemListResponse(
    val success: Boolean,
    val message: String,
    val data: List<FoundItemSummaryData>?,
    val errors: Map<String, String>? = null,
)

data class FoundItemSummaryData(
    val id: Long,
    val title: String,
    val kind: String,
    val imageUrl: String?,
    val itemStatus: ItemStatus,
    val locationType: FoundItemLocationType,
    val associatedBuildingNames: List<String>,
    val createdAt: String,
)

data class FoundItemDetailResponse(
    val success: Boolean,
    val message: String,
    val data: FoundItemDetailData?,
    val errors: Map<String, String>? = null,
)

data class FoundItemDetailData(
    val id: Long,
    val userId: Long,
    val authorName: String? = null,
    val title: String,
    val kind: String,
    val content: String,
    val imageUrl: String?,
    val itemStatus: ItemStatus,
    val locationType: FoundItemLocationType,
    val buildingName: String?,
    val floor: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val associatedBuildingNames: List<String>,
    val createdAt: String,
    val updatedAt: String,
)

data class FoundItemCreateRequest(
    val title: String,
    val kind: String,
    val content: String,
    val location: FoundItemLocationRequest,
)

data class FoundItemLocationRequest(
    val type: FoundItemLocationType,
    val buildingName: String? = null,
    val floor: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

enum class FoundItemLocationType {
    INDOOR,
    OUTDOOR,
}

enum class ItemStatus {
    SEARCHING,
    RETURNED,
}

enum class MatchStatus {
    CALCULATING,
    COMPLETED,
    FAILED,
}

data class FoundItemMatchesResponse(
    val success: Boolean,
    val message: String,
    val data: FoundItemMatchesResult?,
    val errors: Map<String, String>? = null,
)

data class FoundItemMatchesResult(
    val status: MatchStatus,
    val matches: List<FoundItemMatchData> = emptyList(),
)

data class FoundItemMatchData(
    val locationScore: Double,
    val lostItem: LostItemMatchedSummaryData,
)

data class LostItemMatchedSummaryData(
    val id: Long,
    val title: String,
    val kind: String,
    val imageUrl: String?,
    val itemStatus: ItemStatus,
    val createdAt: String,
)

data class FoundItemErrorResponse(
    val success: Boolean,
    val message: String,
    val data: Nothing? = null,
    val errors: Map<String, String>? = null,
)
