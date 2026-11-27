package com.traceon.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.traceon.MainActivity
import com.traceon.core.ui.components.BarChart
import com.traceon.core.ui.components.EmptyState
import com.traceon.core.ui.components.StatTile
import com.traceon.core.ui.components.StatusBadge
import com.traceon.core.ui.theme.TraceonTheme
import com.traceon.domain.model.DataStatus
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun emptyStateExplainsAndActionWorks() {
        var clicked = false
        rule.setContent { TraceonTheme { EmptyState("Nothing here", "Because no data was recorded", actionLabel = "Import", onAction = { clicked = true }) } }
        rule.onNodeWithText("Nothing here").assertIsDisplayed()
        rule.onNodeWithText("Because no data was recorded").assertIsDisplayed()
        rule.onNodeWithText("Import").performClick()
        assertTrue(clicked)
    }

    @Test fun statTileShowsTheExactValue() {
        rule.setContent { TraceonTheme { StatTile("Reels", "42", sub = "57 views") } }
        rule.onNodeWithText("42").assertIsDisplayed()
        rule.onNodeWithText("57 views").assertIsDisplayed()
    }

    @Test fun demoRecordsAreLabelledDemo() {
        rule.setContent { TraceonTheme { StatusBadge(DataStatus.SAMPLE) } }
        rule.onNodeWithText("DEMO").assertIsDisplayed()
    }

    @Test fun chartsRenderWithAllZeroData() {
        rule.setContent { TraceonTheme { BarChart(listOf(0L, 0L, 0L), listOf("a", "b", "c")) } }
        rule.onNodeWithText("a").assertIsDisplayed()
    }
}

/** Launches the real activity: exercises DI, Room creation and navigation wiring end to end. */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun launchesWithAllFiveTabs() {
        rule.onAllNodesWithText("Home").assertCountEquals(2) // top bar title + tab label
        for (label in listOf("Content Map", "Activity", "Analytics", "Search")) rule.onNodeWithText(label).assertIsDisplayed()
    }

    @Test fun tabsNavigateToTheirScreens() {
        rule.onNodeWithText("Search").performClick()
        rule.onAllNodesWithText("Search").assertCountEquals(2)
        rule.onNodeWithText("Analytics").performClick()
        rule.onNodeWithText("Overview").assertIsDisplayed()
        rule.onNodeWithText("Activity").performClick()
        rule.onNodeWithText("Last 7 days").assertIsDisplayed()
    }
}
