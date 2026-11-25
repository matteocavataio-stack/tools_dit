package com.mycloset.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.mycloset.app.data.AppDatabase
import com.mycloset.app.data.Category
import com.mycloset.app.data.Item
import com.mycloset.app.data.Occasion
import com.mycloset.app.data.Outfit
import com.mycloset.app.data.OutfitItem
import com.mycloset.app.data.WearLog
import com.mycloset.app.logic.Backup
import com.mycloset.app.logic.Colors
import com.mycloset.app.logic.ImageProc
import com.mycloset.app.logic.Stats
import com.mycloset.app.logic.Stylist
import com.mycloset.app.logic.WeatherApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LogicTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun item(id: Long, cat: Category, hex: String, price: Double = 50.0, seasons: String = "") =
        Item(id = id, name = "i$id", imagePath = "x.png", category = cat.name, colorHex = hex, price = price, seasons = seasons)

    @Test fun colorNames() {
        assertEquals("Nero", Colors.name("#111111"))
        assertEquals("Bianco", Colors.name("#F5F5F5"))
        assertEquals("Rosso", Colors.name("#D32F2F"))
        assertEquals("Blu navy", Colors.name("#14213D"))
        assertEquals("Verde", Colors.name("#388E3C"))
        assertTrue(Colors.isNeutral("#111111"))
        assertTrue(Colors.harmony("#111111", "#D32F2F") > Colors.harmony("#D32F2F", "#388E3C"))
    }

    @Test fun dominantColorIgnoresTransparentBackground() {
        val b = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (x in 30..70) for (y in 30..70) b.setPixel(x, y, Color.rgb(200, 30, 30))
        val hex = Colors.dominant(b)
        assertEquals("Rosso", Colors.name(hex))
    }

    @Test fun floodFillRemovesUniformBackground() {
        val b = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.WHITE)
        for (x in 30..70) for (y in 30..70) b.setPixel(x, y, Color.rgb(20, 60, 160))
        val out = ImageProc.floodFillBackground(b)
        assertNotNull(out)
        assertEquals(0, Color.alpha(out!!.getPixel(2, 2)))
        assertEquals(255, Color.alpha(out.getPixel(50, 50)))
        val c = ImageProc.cropToContent(out)
        assertTrue(c.width < 60 && c.height < 60)
    }

    @Test fun floodFillRefusesBusyBackground() {
        val b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        for (x in 0 until 60) for (y in 0 until 60) b.setPixel(x, y, Color.rgb((x * 4) % 255, (y * 4) % 255, (x * y) % 255))
        assertNull(ImageProc.floodFillBackground(b))
    }

    @Test fun labelsMapToCategories() {
        assertEquals(Category.BOTTOM, ImageProc.mapLabels(listOf("Jeans", "Denim")))
        assertEquals(Category.SHOES, ImageProc.mapLabels(listOf("Footwear", "Sneakers")))
        assertEquals(Category.TOP, ImageProc.mapLabels(listOf("T-shirt", "Sleeve")))
        assertNull(ImageProc.mapLabels(listOf("Table")))
    }

    @Test fun stylistBuildsCompleteOutfitForCold() {
        val items = listOf(
            item(1, Category.TOP, "#111111", seasons = "WINTER"), item(2, Category.TOP, "#F5F5F5", seasons = "SUMMER"),
            item(3, Category.BOTTOM, "#4A6FA5"), item(4, Category.SHOES, "#111111"),
            item(5, Category.OUTERWEAR, "#14213D", seasons = "WINTER"),
        )
        val s = Stylist.generate(items, 5.0, rainy = true, occasion = null, recentlyWorn = emptySet(), seed = 1)
        assertNotNull(s)
        val ids = s!!.items.map { it.id }
        assertTrue("niente capo estivo con 5°C", 2L !in ids)
        assertTrue(5L in ids)       // capospalla con il freddo
        assertTrue(s.note.contains("ombrello"))
        assertNull(Stylist.generate(listOf(item(9, Category.SHOES, "#111111")), 20.0, false, Occasion.CASUAL, emptySet(), 1))
    }

    @Test fun matchAndPurchase() {
        val a = item(1, Category.TOP, "#111111"); val b = item(2, Category.BOTTOM, "#4A6FA5")
        assertTrue(Stylist.match(listOf(a, b)).score >= 70)
        val r = Stylist.purchaseCheck(listOf(a, a.copy(id = 3), b), Category.TOP, "#111111")
        assertEquals(2, r.similar.size)
        assertTrue(r.verdict.contains("evitare"))
    }

    @Test fun weatherParses() {
        val raw = """{"current":{"temperature_2m":12.5,"apparent_temperature":10.0,"weather_code":61},
            "daily":{"time":["2026-11-25","2026-11-26"],"temperature_2m_min":[4,5],"temperature_2m_max":[13,14],
            "precipitation_probability_max":[70,10],"weather_code":[61,1]}}"""
        val w = WeatherApi.parse("Roma", raw)
        assertEquals(12.5, w.temp, 0.01); assertTrue(w.rainy); assertEquals(2, w.days.size)
    }

    @Test fun databaseStatsAndBackupRoundTrip() = runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.dao()
        // immagine reale per verificare anche la copia delle foto
        val bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val img = ImageProc.saveItemImage(ctx, bmp)
        val id = dao.insert(Item(name = "Giacca", imagePath = img, category = Category.OUTERWEAR.name, price = 100.0,
            purchaseDate = LocalDate.now().toEpochDay()))
        val oid = dao.insert(Outfit(name = "Test", imagePath = ""))
        dao.insertOutfitItems(listOf(OutfitItem(oid, id, 0.5f, 0.5f, 1f, 0f, 0)))
        val today = LocalDate.now().toEpochDay()
        dao.insertWearLogs(listOf(WearLog(date = today, itemId = id, outfitId = oid), WearLog(date = today - 1, itemId = id),
            WearLog(date = today + 5, itemId = id)))  // futuro: pianificato, non conta
        val stats = Stats(dao.itemsOnce(), dao.wearLogsOnce())
        val it0 = dao.itemsOnce().first()
        assertEquals(2, stats.wears(it0)); assertEquals(50.0, stats.costPerWear(it0), 0.001)

        val zip = File(ctx.cacheDir, "b.zip")
        val n = Backup.export(ctx, dao, Uri.fromFile(zip))
        assertTrue(n >= 1 && zip.length() > 0)
        ImageProc.itemFile(ctx, img).delete(); dao.clearItems(); dao.clearOutfits()
        assertEquals(0, dao.itemsOnce().size)
        Backup.import(ctx, dao, Uri.fromFile(zip))
        assertEquals(1, dao.itemsOnce().size); assertEquals(1, dao.outfitsOnce().size)
        assertEquals(1, dao.outfitItemsOnce().size); assertEquals(3, dao.wearLogsOnce().size)
        assertTrue(ImageProc.itemFile(ctx, img).exists())
        db.close()
    }
}

