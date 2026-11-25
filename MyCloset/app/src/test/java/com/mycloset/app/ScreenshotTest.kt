package com.mycloset.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelProvider
import com.mycloset.app.data.Category
import com.mycloset.app.data.Item
import com.mycloset.app.data.Outfit
import com.mycloset.app.data.OutfitItem
import com.mycloset.app.logic.DayForecast
import com.mycloset.app.logic.ImageProc
import com.mycloset.app.logic.Weather
import com.mycloset.app.ui.defaultSlot
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Genera immagini delle schermate per controllare la grafica: SHOT_DIR=/cartella gradle testDebugUnitTest --tests '*ScreenshotTest*' */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@OptIn(ExperimentalTestApi::class)
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val dir = System.getenv("SHOT_DIR")

    private fun shot(name: String) {
        rule.waitForIdle()
        for (k in 1..6) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(40) }
        val v = rule.activity.window.decorView
        println("DECOR " + v.width + "x" + v.height)
        val bmp = Bitmap.createBitmap(maxOf(1, v.width), maxOf(1, v.height), Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun garment(kind: Category, color: Int, variant: Int = 0): Bitmap {
        val b = Bitmap.createBitmap(300, 360, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(40, 0, 0, 0) }
        val path = Path()
        when (kind) {
            Category.TOP -> { path.moveTo(95f, 40f); path.lineTo(40f, 80f); path.lineTo(65f, 130f); path.lineTo(95f, 112f); path.lineTo(95f, 330f)
                path.lineTo(205f, 330f); path.lineTo(205f, 112f); path.lineTo(235f, 130f); path.lineTo(260f, 80f); path.lineTo(205f, 40f)
                path.quadTo(150f, 85f, 95f, 40f); path.close() }
            Category.BOTTOM -> { path.moveTo(85f, 30f); path.lineTo(215f, 30f); path.lineTo(235f, 340f); path.lineTo(160f, 340f); path.lineTo(150f, 130f)
                path.lineTo(140f, 340f); path.lineTo(65f, 340f); path.close() }
            Category.DRESS -> { path.moveTo(110f, 25f); path.lineTo(190f, 25f); path.lineTo(180f, 120f); path.lineTo(250f, 335f); path.lineTo(50f, 335f)
                path.lineTo(120f, 120f); path.close() }
            Category.OUTERWEAR -> { path.moveTo(95f, 35f); path.lineTo(35f, 90f); path.lineTo(50f, 330f); path.lineTo(90f, 330f); path.lineTo(95f, 120f)
                path.lineTo(95f, 335f); path.lineTo(205f, 335f); path.lineTo(205f, 120f); path.lineTo(210f, 330f); path.lineTo(250f, 330f)
                path.lineTo(265f, 90f); path.lineTo(205f, 35f); path.lineTo(150f, 70f); path.close() }
            Category.SHOES -> { path.moveTo(40f, 250f); path.lineTo(40f, 150f); path.quadTo(120f, 150f, 150f, 200f); path.lineTo(250f, 215f)
                path.quadTo(275f, 250f, 255f, 270f); path.lineTo(50f, 270f); path.close() }
            Category.BAGS -> { c.drawRoundRect(RectF(55f, 130f, 245f, 310f), 24f, 24f, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 14f; c.drawArc(RectF(95f, 50f, 205f, 190f), 180f, 180f, false, p); p.style = Paint.Style.FILL }
            else -> path.addCircle(150f, 180f, 90f, Path.Direction.CW)
        }
        if (kind != Category.BAGS) { c.drawPath(path, p); c.save(); c.translate(0f, 6f); c.restore() }
        if (variant == 1) c.drawRect(0f, 160f, 300f, 175f, shade)
        return b
    }

    @Test fun capture() {
        assumeTrue("SHOT_DIR non impostata", dir != null)
        File(dir!!).mkdirs()
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        val today = LocalDate.now()
        runBlocking {
            vm.dao.clearItems(); vm.dao.clearOutfits(); vm.dao.clearTrips()
            data class S(val n: String, val c: Category, val col: Int, val hex: String, val price: Double, val br: String = "")
            val samples = listOf(
                S("T-shirt bianca", Category.TOP, Color.rgb(240, 240, 240), "#F5F5F5", 25.0, "Uniqlo"),
                S("Maglia rossa", Category.TOP, Color.rgb(196, 30, 40), "#D32F2F", 40.0, "Zara"),
                S("Camicia azzurra", Category.TOP, Color.rgb(100, 160, 230), "#64B5F6", 55.0),
                S("Jeans scuri", Category.BOTTOM, Color.rgb(50, 70, 110), "#4A6FA5", 80.0, "Levis"),
                S("Pantaloni beige", Category.BOTTOM, Color.rgb(216, 195, 165), "#D8C3A5", 60.0),
                S("Abito nero", Category.DRESS, Color.rgb(30, 30, 30), "#111111", 120.0),
                S("Giacca navy", Category.OUTERWEAR, Color.rgb(20, 33, 61), "#14213D", 150.0, "Zara"),
                S("Sneakers bianche", Category.SHOES, Color.rgb(235, 235, 235), "#F5F5F5", 90.0, "Adidas"),
                S("Borsa marrone", Category.BAGS, Color.rgb(123, 75, 42), "#7B4B2A", 70.0),
                S("Maglione verde", Category.TOP, Color.rgb(56, 142, 60), "#388E3C", 65.0),
            )
            val ids = samples.map { s ->
                val img = ImageProc.saveItemImage(rule.activity, garment(s.c, s.col))
                vm.dao.insert(Item(name = s.n, imagePath = img, category = s.c.name, colorHex = s.hex, price = s.price, brand = s.br,
                    seasons = "SPRING,AUTUMN", occasions = "CASUAL", favorite = s.n.startsWith("Maglia r"), purchaseDate = today.toEpochDay() - 40))
            }
            val items = vm.dao.itemsOnce()
            // outfit di esempio
            val parts = listOf(items[1], items[3], items[7])
            val placements = parts.mapIndexed { i, it -> val (x, y, sc) = defaultSlot(Category.of(it.category)); it to OutfitItem(0, it.id, x, y, sc, 0f, i) }
            vm.saveOutfit(Outfit(name = "Casual del sabato", occasion = "CASUAL"), placements)
            val parts2 = listOf(items[2], items[4], items[7], items[6])
            vm.saveOutfit(Outfit(name = "Ufficio", occasion = "WORK"), parts2.mapIndexed { i, it -> val (x, y, sc) = defaultSlot(Category.of(it.category)); it to OutfitItem(0, it.id, x, y, sc, 0f, i) })
            vm.dao.insertWearLogs(listOf(com.mycloset.app.data.WearLog(date = today.toEpochDay() - 1, itemId = ids[1]),
                com.mycloset.app.data.WearLog(date = today.toEpochDay() - 1, itemId = ids[3]), com.mycloset.app.data.WearLog(date = today.toEpochDay() - 3, itemId = ids[3]),
                com.mycloset.app.data.WearLog(date = today.toEpochDay() - 5, itemId = ids[7])))
        }
        vm.city.value = "Barcellona"
        vm.weather.value = Weather("Barcellona", 14.0, 12.0, 2, (0 until 7).map { DayForecast(today.plusDays(it.toLong()), 9.0 + it, 15.0 + it, 20, 1) })
        for (k in 1..60) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); if (vm.items.value.size == 10) break; Thread.sleep(50) }
        println("ITEMS=" + vm.items.value.size)
        shot("01-oggi")
        rule.onNodeWithText("Armadio").performClick(); rule.waitUntil(5000) { true }; shot("02-armadio")
        rule.onNodeWithText("Aggiungi", useUnmergedTree = true).performClick(); rule.waitUntilExactlyOneExists(hasText("Fotografa il capo"), 5000); shot("03-nuovo-capo")
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.onNodeWithContentDescription("Maglia rossa").performClick(); rule.waitUntilExactlyOneExists(hasText("Costo/uso"), 5000); shot("04-dettaglio")
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.onNodeWithText("Outfit").performClick(); rule.waitUntilAtLeastOneExists(hasText("Ufficio"), 8000); shot("05-outfit")
        rule.onNodeWithText("Calendario").performClick(); rule.waitUntilExactlyOneExists(hasText("Aggiungi outfit"), 5000); shot("06-calendario")
        rule.onNodeWithText("Statistiche").performClick(); rule.waitUntilAtLeastOneExists(hasText("Per categoria"), 5000); shot("07-statistiche")
        rule.onNodeWithText("Oggi").performClick(); rule.waitUntilExactlyOneExists(hasText("Cosa mi metto oggi?"), 5000)
        rule.onNodeWithContentDescription("Impostazioni").performClick(); rule.waitUntilExactlyOneExists(hasText("Città per il meteo"), 5000); shot("08-impostazioni")
        rule.activity.onBackPressedDispatcher.onBackPressed()
        vm.chat.value = listOf(ChatMsg("user", "Cosa mi metto oggi?"), ChatMsg("assistant", "Con 14° ti consiglio jeans e maglia rossa, più la giacca navy per la sera.",
            listOf(com.mycloset.app.logic.AiOutfit("Casual elegante", vm.items.value.filter { it.name in listOf("Maglia rossa", "Jeans scuri", "Sneakers bianche", "Giacca navy") }.map { it.id }, "Il rosso fa da protagonista, il resto è neutro."))))
        rule.onNodeWithText("✨ Chiedi allo stilista AI").performClick(); rule.waitUntilAtLeastOneExists(hasText("Lo indosso"), 5000); shot("09-stilista")
    }
}


@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@OptIn(ExperimentalTestApi::class)
class AddItemFlowTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val dir = System.getenv("SHOT_DIR")

    private fun shot(name: String) {
        if (dir == null) return
        for (k in 1..8) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(40) }
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(maxOf(1, v.width), maxOf(1, v.height), Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Foto "vera": maglietta rossa su un muro chiaro. */
    private fun photo(): Bitmap {
        val b = Bitmap.createBitmap(900, 1100, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        c.drawColor(Color.rgb(238, 236, 232))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(196, 30, 40) }
        val path = Path().apply { moveTo(300f, 220f); lineTo(130f, 360f); lineTo(210f, 520f); lineTo(300f, 460f); lineTo(300f, 960f)
            lineTo(600f, 960f); lineTo(600f, 460f); lineTo(690f, 520f); lineTo(770f, 360f); lineTo(600f, 220f); quadTo(450f, 340f, 300f, 220f); close() }
        c.drawPath(path, p); return b
    }

    @Test fun addFlow() {
        if (dir != null) File(dir).mkdirs()
        val srv = FakeServer()
        srv.analyzeDelayMs = 4000
        srv.analyzeJson = """{"name":"T-shirt rossa a maniche corte","category":"TOP","colorHex":"#C41E2A","colorName":"Rosso","brand":"","seasons":["SPRING","SUMMER"],"occasions":["CASUAL","HOME"],"material":"Cotone","pattern":"Tinta unita","notes":"","provider":"workers-ai"}"""
        try {
            val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
            val vm = AppViewModel(app)
            vm.setAi(true, srv.url)
            runBlocking { vm.dao.clearItems() }
            rule.setContent { com.mycloset.app.ui.MyClosetTheme { com.mycloset.app.ui.ItemEditScreen(vm, 0, false, onClose = {}, startBitmap = photo()) } }
            rule.waitUntilExactlyOneExists(hasText("Sto analizzando il capo…"), 5000); shot("10-analisi")
            rule.waitUntilExactlyOneExists(hasText("Conferma e salva"), 15000); shot("11-conferma-alto")
            rule.onNodeWithText("Occasioni").performScrollTo(); rule.waitForIdle(); for (k in 1..10) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(40) }; shot("12-conferma-basso")
            rule.onNodeWithText("Conferma e salva").performClick()
            for (k in 1..40) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(50) }
            val saved = runBlocking { vm.dao.itemsOnce() }
            // flusso completo: foto -> analisi (AI) -> scheda compilata -> conferma -> salvato con i dati dell'AI
            org.junit.Assert.assertEquals(1, saved.size)
            val it0 = saved.single()
            org.junit.Assert.assertEquals("T-shirt rossa a maniche corte", it0.name)
            org.junit.Assert.assertEquals("TOP", it0.category); org.junit.Assert.assertEquals("#C41E2A", it0.colorHex)
            org.junit.Assert.assertEquals("SPRING,SUMMER", it0.seasons); org.junit.Assert.assertEquals("CASUAL,HOME", it0.occasions)
            org.junit.Assert.assertTrue("foto salvata", ImageProc.itemFile(rule.activity, it0.imagePath).length() > 0)
        } finally { srv.stop() }
    }
}
