package com.mycloset.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.lifecycle.ViewModelProvider
import com.mycloset.app.data.Category
import com.mycloset.app.data.Item
import com.mycloset.app.logic.ImageProc
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class UiSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitText(t: String) = rule.waitUntilExactlyOneExists(androidx.compose.ui.test.hasText(t), 8000)

    /** I file dell'app (database) possono restare tra un test e l'altro nello stesso processo: si riparte da zero. */
    @org.junit.Before fun cleanDb() {
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        runBlocking { vm.dao.clearItems(); vm.dao.clearOutfits(); vm.dao.clearTrips() }
    }

    @Test fun navigateAndBuildOutfit() {
        waitText("Cosa mi metto oggi?")
        rule.onNodeWithText("Cosa mi metto oggi?").assertIsDisplayed()

        rule.onNodeWithText("Armadio").performClick()
        waitText("Armadio vuoto")

        // 4 capi di esempio con foto vere
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        val ctx = rule.activity
        runBlocking {
            fun png(c: Int) = Bitmap.createBitmap(80, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(c) }
            listOf(
                Triple("Maglia nera", Category.TOP, Color.BLACK), Triple("Jeans", Category.BOTTOM, Color.rgb(74, 111, 165)),
                Triple("Sneakers", Category.SHOES, Color.WHITE), Triple("Giacca", Category.OUTERWEAR, Color.rgb(20, 33, 61)),
            ).forEach { (n, c, col) ->
                val img = ImageProc.saveItemImage(ctx, png(col))
                vm.dao.insert(Item(name = n, imagePath = img, category = c.name, colorHex = Colors(col), price = 60.0))
            }
        }
        waitText("4 capi")

        rule.onNodeWithText("Outfit").performClick()
        waitText("Nessun outfit")
        rule.onNodeWithContentDescription("Crea outfit").performClick()
        waitText("Nuovo outfit")
        rule.onNodeWithText("Suggerisci").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTextCount("Aggiungi dei capi con il pulsante qui sotto") == 0 }
        rule.onNodeWithText("Salva").performClick()      // apre il dialogo
        rule.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasText("Salva outfit"), 5000)
        rule.onAllNodesWithTextList("Salva").onLast().performClick()
        waitText("Indossato oggi")                          // dettaglio outfit
        rule.waitUntil(5000) { runBlocking { vm.dao.outfitItemsOnce().isNotEmpty() } }
        rule.waitForIdle()
        rule.onNodeWithText("Indossato oggi").assertIsEnabled()
        rule.onNodeWithText("Indossato oggi").performScrollTo().performClick()
        for (k in 1..40) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(50) }
        val logs = runBlocking { vm.dao.wearLogsOnce() }
        assertTrue("attesi 3 capi indossati, trovati ${logs.size}", logs.size == 3 && logs.all { it.outfitId != null })
        assertTrue(runBlocking { vm.dao.outfitsOnce().size } == 1)

        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.onNodeWithText("Calendario").performClick()
        waitText("Aggiungi outfit")
        rule.onNodeWithText("Statistiche").performClick()
        rule.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasText("Per categoria"), 5000)
        rule.onNodeWithText("Oggi").performClick()
        waitText("Cosa mi metto oggi?")
    }

    @Test fun itemScreensAndTools() {
        waitText("Cosa mi metto oggi?")
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        runBlocking {
            val img = ImageProc.saveItemImage(rule.activity, Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) })
            vm.dao.insert(Item(name = "Maglione rosso", imagePath = img, category = Category.TOP.name, colorHex = "#D32F2F", price = 80.0))
        }
        rule.onNodeWithText("Armadio").performClick()
        rule.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasContentDescription("Maglione rosso"), 8000)
        rule.onNodeWithContentDescription("Maglione rosso").performClick()
        waitText("Costo/uso")
        rule.onNodeWithText("Non l'hai ancora indossato.").assertIsDisplayed()
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.onNodeWithContentDescription("Aggiungi capo").performClick()
        waitText("Nuovo capo")
        rule.onNodeWithText("Scatta foto").assertIsDisplayed()
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.onNodeWithText("Oggi").performClick()
        for ((card, title) in listOf(
            "Questo abbinamento va bene?" to "Scegli i capi",
            "Devo comprarlo?" to "Scegli foto",
            "Valigia per i viaggi" to "Nessun viaggio",
        )) {
            rule.onNodeWithText(card).performScrollTo().performClick()
            waitText(title)
            rule.activity.onBackPressedDispatcher.onBackPressed()
            waitText("Cosa mi metto oggi?")
        }
        rule.onNodeWithText("Impostazioni").performClick()
        waitText("Città per il meteo")
    }
}

private object Colors { operator fun invoke(c: Int) = String.format("#%06X", 0xFFFFFF and c) }
private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextCount(t: String) =
    onAllNodes(androidx.compose.ui.test.hasText(t)).fetchSemanticsNodes().size
private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextList(t: String) =
    onAllNodes(androidx.compose.ui.test.hasText(t))
