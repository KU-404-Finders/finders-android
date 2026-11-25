package com.ku.lostandfound.ui.profile.screen

/** Display only: never use these labels in API requests or search filters. */
internal fun locationTypeLabel(type: String?): String = when (type?.trim()) {
    "INDOOR" -> "실내"
    "OUTDOOR" -> "실외"
    else -> ""
}

internal fun locationDisplayText(type: String?, buildingNames: List<String?>?): String {
    val names = buildingNames.orEmpty().mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
    return listOf(locationTypeLabel(type), names.joinToString(", "))
        .filter(String::isNotEmpty)
        .joinToString(" · ")
}
