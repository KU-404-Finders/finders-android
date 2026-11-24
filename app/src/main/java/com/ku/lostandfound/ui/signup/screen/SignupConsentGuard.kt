package com.ku.lostandfound.ui.signup.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ku.lostandfound.ui.signup.viewmodel.SignupViewmodel

/** Consent is intentionally not restored from saved UI state after process death. */
@Composable
internal fun SignupConsentGuard(vm: SignupViewmodel, onConsentRequired: () -> Unit, content: @Composable () -> Unit) {
    if (vm.hasRequiredConsent) content()
    else LaunchedEffect(Unit) { onConsentRequired() }
}
