package com.ku.lostandfound

import com.ku.lostandfound.network.TokenManager
import com.ku.lostandfound.ui.navigation.mainTabStateOwner
import org.junit.Assert.*
import org.junit.Test

class TabSessionOwnershipTest : StabilityFixture() {
    private fun owner() = mainTabStateOwner(TokenManager.sessionGeneration())

    @Test fun ordinaryTokenRefreshKeepsTabOwnerWithoutSavingCredentialText() {
        val before = owner()
        assertTrue(TokenManager.saveRefreshedTokens(TokenManager.credentials(), "rotated-access-secret", "rotated-refresh-secret"))
        assertEquals(before, owner())
        assertFalse(owner().contains("rotated-access-secret"))
        assertFalse(owner().contains("rotated-refresh-secret"))
    }
    @Test fun accountChangeLogoutAndSameAccountReloginEachChangeOwner() {
        val first = owner()
        login("b"); val second = owner(); assertNotEquals(first, second)
        TokenManager.clear(); val loggedOut = owner(); assertNotEquals(second, loggedOut)
        login("b"); assertNotEquals(loggedOut, owner()); assertNotEquals(second, owner())
    }
    @Test fun sessionExpiryChangesOwner() {
        val before = owner()
        assertTrue(TokenManager.expireSession(TokenManager.credentials()))
        assertNotEquals(before, owner())
    }
    @Test fun processRestartCannotReuseOwnerEvenWhenGenerationCounterRepeats() {
        val generation = TokenManager.sessionGeneration()
        assertNotEquals(mainTabStateOwner(generation, "process-a"), mainTabStateOwner(generation, "process-b"))
    }
}
