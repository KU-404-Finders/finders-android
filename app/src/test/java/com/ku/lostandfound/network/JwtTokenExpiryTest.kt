package com.ku.lostandfound.network

import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JwtTokenExpiryTest {
    @Test
    fun `future expiry is valid`() {
        assertFalse(JwtTokenExpiry.isExpired(jwtWithExpiry(2_000_000_000L), nowMillis = 1_000_000_000_000L))
    }

    @Test
    fun `past or malformed token is expired`() {
        assertTrue(JwtTokenExpiry.isExpired(jwtWithExpiry(1_000_000_000L), nowMillis = 1_000_000_000_000L))
        assertTrue(JwtTokenExpiry.isExpired("not-a-jwt"))
        assertTrue(JwtTokenExpiry.isExpired(null))
    }

    private fun jwtWithExpiry(expirySeconds: Long): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("{\"alg\":\"none\"}".toByteArray())
        val payload = encoder.encodeToString("{\"exp\":$expirySeconds}".toByteArray())
        return "$header.$payload.signature"
    }
}
