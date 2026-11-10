package com.ku.lostandfound.network

/** Rules for deciding whether a refresh response proves that credentials are invalid. */
internal object RefreshSessionPolicy {
    fun isConfirmedInvalid(statusCode: Int): Boolean = statusCode == 401 || statusCode == 403
}
