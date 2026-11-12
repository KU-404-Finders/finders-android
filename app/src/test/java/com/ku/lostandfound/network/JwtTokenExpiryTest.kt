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

    @Test
    fun `only the top-level expiry claim is used`() {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("{\"alg\":\"none\"}".toByteArray())
        val nestedOnly = encoder.encodeToString("{\"metadata\":{\"exp\":2000000000}}".toByteArray())
        val topLevel = encoder.encodeToString("{\"exp\":2000000000,\"metadata\":{\"exp\":1}}".toByteArray())

        assertTrue(JwtTokenExpiry.isExpired("$header.$nestedOnly.signature", nowMillis = 1_000_000_000_000L))
        assertFalse(JwtTokenExpiry.isExpired("$header.$topLevel.signature", nowMillis = 1_000_000_000_000L))
    }

    private fun jwtWithExpiry(expirySeconds: Long): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("{\"alg\":\"none\"}".toByteArray())
        val payload = encoder.encodeToString("{\"exp\":$expirySeconds}".toByteArray())
        return "$header.$payload.signature"
    }
}
