package com.ku.lostandfound

import android.content.*
import android.net.Uri
import com.ku.lostandfound.ui.profile.screen.SupportContact
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class SupportContactTest {
    private fun launch(failure: RuntimeException? = null): Boolean {
        val context = mock(Context::class.java)
        val uri = mock(Uri::class.java)
        mockStatic(Uri::class.java).use { uris ->
            uris.`when`<Uri> { Uri.parse("mailto:michelle03212@gmail.com") }.thenReturn(uri)
            mockConstruction(Intent::class.java) { intent, construction ->
                assertEquals(listOf(Intent.ACTION_SENDTO, uri), construction.arguments())
                if (failure != null) doThrow(failure).`when`(context).startActivity(intent)
            }.use { intents ->
                val opened = SupportContact.openEmail(context)
                val intent = intents.constructed().single()
                verify(context).startActivity(intent)
                verify(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return opened
            }
        }
    }
    @Test fun officialContactUsesSendToMailtoInsteadOfGenericSharing() {
        assertEquals("michelle03212@gmail.com", SupportContact.EMAIL)
        assertTrue(launch())
    }
    @Test fun missingMailApplicationReturnsFailureForUserNotice() {
        assertFalse(launch(mock(ActivityNotFoundException::class.java)))
    }
    @Test fun mailLaunchDeniedByDevicePolicyReturnsFailureForUserNotice() {
        assertFalse(launch(SecurityException("Launch denied")))
    }
    @Test fun copyWritesOfficialEmailAsPlainText() {
        val context = mock(Context::class.java)
        val clipboard = mock(ClipboardManager::class.java)
        val clip = mock(ClipData::class.java)
        `when`(context.getSystemService(Context.CLIPBOARD_SERVICE)).thenReturn(clipboard)
        mockStatic(ClipData::class.java).use { clips ->
            clips.`when`<ClipData> { ClipData.newPlainText("문의 이메일", "michelle03212@gmail.com") }.thenReturn(clip)
            assertTrue(SupportContact.copyEmail(context))
            verify(clipboard).setPrimaryClip(clip)
        }
    }
    @Test fun missingClipboardReturnsFailureInsteadOfReportingSuccessfulCopy() {
        assertFalse(SupportContact.copyEmail(mock(Context::class.java)))
    }
    @Test fun copyFeedbackUsesSystemConfirmationStartingAtAndroid13() {
        assertEquals("이메일 주소를 복사했습니다.", SupportContact.copyFeedback(true, 32))
        assertNull(SupportContact.copyFeedback(true, 33))
        assertNull(SupportContact.copyFeedback(true, 36))
    }
    @Test fun copyFailureAlwaysHasUserFeedback() {
        for (sdk in listOf(26, 32, 33, 36)) {
            assertEquals("이메일 주소를 복사할 수 없습니다.", SupportContact.copyFeedback(false, sdk))
        }
    }
}
