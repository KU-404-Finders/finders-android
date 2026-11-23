package com.ku.lostandfound.ui.signup.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.ui.component.BottomFixedButton
import com.ku.lostandfound.ui.component.GetBackTopAppBar
import com.ku.lostandfound.ui.signup.viewmodel.SignupViewmodel

object LegalLinks {
    const val TERMS = "https://cypress-monkey-570.notion.site/41f29506e32280d9ad75c85fc66dc6e7"
    const val PRIVACY = "https://cypress-monkey-570.notion.site/41129506e322803fafc1eae07ce4925c"
}

@Composable
fun SignupTermsScreen(vm: SignupViewmodel, onBack: () -> Unit, onContinue: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxSize().background(Color.White)) {
        GetBackTopAppBar(onClick = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Spacer(Modifier.height(40.dp))
            Text("약관에 동의해주세요", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(15.dp))
            Text("찾을건대를 이용하려면 아래 내용에 동의해야 해요.")
            Spacer(Modifier.height(30.dp))
            TermsCheck("전체 동의", vm.hasRequiredConsent, {
                vm.termsAccepted = it
                vm.privacyAccepted = it
            })
            HorizontalDivider()
            TermsCheck("[필수] 이용약관에 동의합니다", vm.termsAccepted, { vm.termsAccepted = it }) {
                uriHandler.openUri(LegalLinks.TERMS)
            }
            TermsCheck("[필수] 개인정보 처리방침에 동의합니다", vm.privacyAccepted, { vm.privacyAccepted = it }) {
                uriHandler.openUri(LegalLinks.PRIVACY)
            }
            Spacer(Modifier.height(24.dp))
            Text("부적절한 게시글이나 다른 이용자를 괴롭히는 행위는 허용되지 않으며, 신고되면 게시글 삭제와 이용 제한이 이루어질 수 있습니다.", color = Color.Gray)
        }
        BottomFixedButton("동의하고 계속하기", { if (vm.hasRequiredConsent) onContinue() },
            Modifier.padding(20.dp), enabled = vm.hasRequiredConsent)
    }
}

@Composable
private fun TermsCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit, onView: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Text(label, Modifier.weight(1f))
        onView?.let { TextButton(onClick = it) { Text("보기") } }
    }
}
