package com.ku.lostandfound.ui.component

import androidx.compose.runtime.Composable

/** iOS FinderNavBar with an empty or plain title; shares BackTitleBar's 48dp target and hairline. */
@Composable
@Suppress("UNUSED_PARAMETER") // isWhite: every iOS counterpart uses the white bar.
fun GetBackTopAppBar(isWhite: Boolean = true, title: String = "", onClick: () -> Unit = {}) {
    BackTitleBar(title = title, onBackClick = onClick)
}
