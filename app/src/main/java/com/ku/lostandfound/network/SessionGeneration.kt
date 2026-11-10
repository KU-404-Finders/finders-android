package com.ku.lostandfound.network

/** Identifies one authenticated lifetime and invalidates asynchronous work from older ones. */
internal class SessionGeneration {
    private var value = 0L

    @Synchronized
    fun capture(): Long = value

    @Synchronized
    fun advance(): Long = ++value

    @Synchronized
    fun isCurrent(expected: Long): Boolean = value == expected
}
