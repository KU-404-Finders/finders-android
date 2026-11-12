package com.ku.lostandfound.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGenerationTest {
    @Test
    fun `logout invalidates a refresh result started before logout`() {
        val generation = SessionGeneration()
        val refreshRequestGeneration = generation.capture()

        generation.advance() // logout

        assertFalse(generation.isCurrent(refreshRequestGeneration))
        assertTrue(generation.isCurrent(generation.capture()))
    }

    @Test
    fun `late refresh response cannot be applied after logout and a new login`() {
        val generation = SessionGeneration()
        val oldRefreshGeneration = generation.capture()

        generation.advance() // logout
        generation.advance() // new login

        assertFalse(generation.isCurrent(oldRefreshGeneration))
    }

    @Test
    fun `account B session accepts its own results but rejects account A results`() {
        val generation = SessionGeneration()
        val accountAGeneration = generation.capture()

        generation.advance() // account A logout
        val accountBGeneration = generation.advance() // account B login

        assertFalse(generation.isCurrent(accountAGeneration))
        assertTrue(generation.isCurrent(accountBGeneration))
    }
}
