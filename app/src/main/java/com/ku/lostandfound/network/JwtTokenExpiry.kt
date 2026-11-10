package com.ku.lostandfound.network

import com.google.gson.JsonParser
import java.util.Base64

/** Minimal JWT expiry reader. Signature validation remains the server's responsibility. */
object JwtTokenExpiry {
    fun isExpired(token: String?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (token.isNullOrBlank()) return true
        return runCatching {
            val payload = token.split('.')[1]
            val json = String(Base64.getUrlDecoder().decode(payload))
            val expirySeconds = JsonParser.parseString(json)
                .asJsonObject
                .get("exp")
                ?.asLong
                ?: return true
            expirySeconds <= nowMillis / 1_000
        }.getOrDefault(true)
    }
}
