package com.ku.lostandfound.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshSessionPolicyTest {
    @Test
    fun `expired or revoked refresh credentials are confirmed invalid`() {
        assertTrue(RefreshSessionPolicy.isConfirmedInvalid(401))
        assertTrue(RefreshSessionPolicy.isConfirmedInvalid(403))
    }

    @Test
    fun `temporary server failures do not invalidate the saved session`() {
        assertFalse(RefreshSessionPolicy.isConfirmedInvalid(429))
        assertFalse(RefreshSessionPolicy.isConfirmedInvalid(500))
        assertFalse(RefreshSessionPolicy.isConfirmedInvalid(200))
    }
}
