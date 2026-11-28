package com.ku.lostandfound.ui.signup.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.ku.lostandfound.ui.component.BottomFixedButton

@Composable
internal fun SignupFormButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    BottomFixedButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = if (compactHeight) 8.dp else 20.dp),
    )
}
