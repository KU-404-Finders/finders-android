package com.ku.lostandfound.network

data class UserMeResponse(
    val success: Boolean,
    val message: String,
    val data: UserMeData?,
    val errors: Map<String, String>? = null,
)

data class UserMeData(
    val email: String,
    val name: String,
    val foundItemCount: Int = 0,
    val lostItemCount: Int = 0,
)

data class MyLostItemsResponse(
    val success: Boolean,
    val message: String,
    val data: MyLostItemsData?,
    val errors: Map<String, String>? = null,
)

data class MyLostItemsData(
    val count: Int,
    val items: List<MyLostItemData>,
)

data class MyLostItemData(
    val id: Long,
    val title: String,
    val kind: String,
    val imageUrl: String?,
    val itemStatus: String,
    val createdAt: String,
)

data class MyFoundItemsResponse(
    val success: Boolean,
    val message: String,
    val data: MyFoundItemsData?,
    val errors: Map<String, String>? = null,
)

data class MyFoundItemsData(
    val count: Int,
    val items: List<MyFoundItemData>,
)

data class MyFoundItemData(
    val id: Long,
    val title: String,
    val kind: String,
    val imageUrl: String?,
    val itemStatus: String,
    val locationType: String,
    val associatedBuildingNames: List<String>,
    val createdAt: String,
)
