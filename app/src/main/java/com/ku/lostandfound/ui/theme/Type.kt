package com.ku.lostandfound.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.R

val PretendardFontFamily = FontFamily(
    Font(R.font.pretendard_light, FontWeight.Light),
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold)
)

private val DefaultTypography = Typography()

val Typography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(fontFamily = PretendardFontFamily),
    displayMedium = DefaultTypography.displayMedium.copy(fontFamily = PretendardFontFamily),
    displaySmall = DefaultTypography.displaySmall.copy(fontFamily = PretendardFontFamily),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontFamily = PretendardFontFamily),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontFamily = PretendardFontFamily),
    headlineSmall = DefaultTypography.headlineSmall.copy(fontFamily = PretendardFontFamily),
    titleLarge = DefaultTypography.titleLarge.copy(fontFamily = PretendardFontFamily),
    titleMedium = DefaultTypography.titleMedium.copy(fontFamily = PretendardFontFamily),
    titleSmall = DefaultTypography.titleSmall.copy(fontFamily = PretendardFontFamily),
    bodyLarge = DefaultTypography.bodyLarge.copy(fontFamily = PretendardFontFamily),
    bodyMedium = DefaultTypography.bodyMedium.copy(fontFamily = PretendardFontFamily),
    bodySmall = DefaultTypography.bodySmall.copy(fontFamily = PretendardFontFamily),
    labelLarge = DefaultTypography.labelLarge.copy(fontFamily = PretendardFontFamily),
    labelMedium = DefaultTypography.labelMedium.copy(fontFamily = PretendardFontFamily),
    labelSmall = DefaultTypography.labelSmall.copy(fontFamily = PretendardFontFamily),
)

object PretendardTextStyles {
    val PRETENDARD_20_BOLD = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    )
    val PRETENDARD_20_SEMI_BOLD = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    )
    val PRETENDARD_20_MEDIUM = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp
    )
    val PRETENDARD_20_REGULAR = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp
    )
    val PRETENDARD_20_LIGHT = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 20.sp,
        lineHeight = 28.sp
    )

    val PRETENDARD_18_BOLD = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 26.sp
    )
    val PRETENDARD_18_SEMI_BOLD = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp
    )
    val PRETENDARD_18_MEDIUM = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 26.sp
    )
    val PRETENDARD_18_REGULAR = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp
    )
    val PRETENDARD_18_LIGHT = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 18.sp,
        lineHeight = 26.sp
    )

    val PRETENDARD_16_BOLD = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
    val PRETENDARD_16_SEMI_BOLD = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
    val PRETENDARD_16_MEDIUM = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
    val PRETENDARD_16_REGULAR = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
    val PRETENDARD_16_LIGHT = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )

    val PRETENDARD_14_BOLD = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 22.sp
    )
    val PRETENDARD_14_SEMI_BOLD = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 22.sp
    )
    val PRETENDARD_14_MEDIUM = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 22.sp
    )
    val PRETENDARD_14_REGULAR = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    )
    val PRETENDARD_14_LIGHT = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 14.sp,
        lineHeight = 22.sp
    )

    val PRETENDARD_12_BOLD = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 18.sp
    )
    val PRETENDARD_12_SEMI_BOLD = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 18.sp
    )
    val PRETENDARD_12_MEDIUM = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp
    )
    val PRETENDARD_12_REGULAR = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    )
    val PRETENDARD_12_LIGHT = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 12.sp,
        lineHeight = 18.sp
    )

    val PRETENDARD_10_BOLD = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 16.sp
    )
    val PRETENDARD_10_SEMI_BOLD = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 16.sp
    )
    val PRETENDARD_10_MEDIUM = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 16.sp
    )
    val PRETENDARD_10_REGULAR = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 16.sp
    )
    val PRETENDARD_10_LIGHT = TextStyle(
        fontFamily = PretendardFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 10.sp,
        lineHeight = 16.sp
    )
}
