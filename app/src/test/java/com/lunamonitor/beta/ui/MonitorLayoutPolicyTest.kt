package com.lunamonitor.beta.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorLayoutPolicyTest {
    @Test fun panelNeverExceedsSafeAvailableWidth() {
        assertEquals(280f, MonitorLayoutPolicy.panelWidth(280f), 0f)
        assertEquals(310f, MonitorLayoutPolicy.panelWidth(720f), 0f)
    }

    @Test fun narrowAndShortPhonesUseCompactChrome() {
        assertTrue(MonitorLayoutPolicy.compactToolbar(599f))
        assertTrue(MonitorLayoutPolicy.compactHeight(359f))
        assertFalse(MonitorLayoutPolicy.compactToolbar(600f))
    }

    @Test fun statusFieldsReduceInStableSteps() {
        assertFalse(MonitorLayoutPolicy.showBitrate(539f))
        assertTrue(MonitorLayoutPolicy.showBitrate(540f))
        assertFalse(MonitorLayoutPolicy.showFullStats(719f))
        assertTrue(MonitorLayoutPolicy.showFullStats(720f))
        assertTrue(MonitorLayoutPolicy.hideBrand(459f))
    }
}
