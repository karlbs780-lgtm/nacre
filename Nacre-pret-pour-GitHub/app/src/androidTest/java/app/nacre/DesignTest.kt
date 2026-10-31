package app.nacre

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nacre.data.Settings
import app.nacre.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignTest {
    @get:Rule val compose = createComposeRule()
    @Test fun emptyStateActionIsAccessibleAndClickable() {
        var imported = false
        compose.setContent {
            NacreTheme(Settings()) { EmptyState("Bibliothèque vide", "Vos fichiers restent locaux.", "Importer", { imported = true }) }
        }
        compose.onNodeWithText("Bibliothèque vide").assertIsDisplayed()
        compose.onNodeWithText("Importer").performClick()
        org.junit.Assert.assertTrue(imported)
    }
}
