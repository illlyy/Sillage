package com.termux.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeGoalPlanRestoreTest {
    @Test
    fun initialRouteRestoreLoadsStoredPlan() {
        assertTrue(shouldRestoreStoredPlan(restorePlanFromPreferences = true, currentPlanJson = "[]"))
        assertTrue(shouldRestoreStoredPlan(restorePlanFromPreferences = true, currentPlanJson = "[{\"step\":\"history\"}]"))
    }

    @Test
    fun onReadyDoesNotOverwriteHistoryPlanWithEmptyPreferences() {
        assertFalse(shouldRestoreStoredPlan(
            restorePlanFromPreferences = false,
            currentPlanJson = "[{\"step\":\"from rollout\"}]",
        ))
        assertTrue(shouldRestoreStoredPlan(restorePlanFromPreferences = false, currentPlanJson = "[]"))
    }
}
