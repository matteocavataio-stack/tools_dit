package com.mycloset.app

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.mycloset.app.data.Category
import com.mycloset.app.data.Item
import com.mycloset.app.logic.AiClient
import com.mycloset.app.logic.AiException
import com.mycloset.app.logic.ImageProc
import com.mycloset.app.logic.Prefs
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Finto server MyCloset su localhost, per provare il client e l'interfaccia senza internet. */
class FakeServer {
    val requests = java.util.Collections.synchronizedList(mutableListOf<Triple<String, String?, JSONObject>>()) // path, Authorization, body
    var rejectFirstToken = false
    var registerCount = 0
    var chatOutfitIds = listOf(1, 2)
    var analyzeJson: String? = null
    var analyzeDelayMs = 0L
    private val server = okhttp3.mockwebserver.MockWebServer()
    val url get() = server.url("/").toString().trimEnd('/')

    init {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): okhttp3.mockwebserver.MockResponse {
                val path = request.path!!.substringBefore('?')
                val auth = request.getHeader("Authorization")
                requests += Triple(path, auth, runCatching { JSONObject(request.body.readUtf8()) }.getOrDefault(JSONObject()))
                fun send(code: Int, json: String) = okhttp3.mockwebserver.MockResponse().setResponseCode(code)
                    .setHeader("Content-Type", "application/json").setBody(json)
                return when (path) {
                    "/api/health" -> send(200, """{"ok":true,"db":true,"ai":true,"secret":true}""")
                    "/api/register" -> { registerCount++; send(200, """{"token":"tok-$registerCount","dailyLimit":30}""") }
                    else -> if (auth == null || (rejectFirstToken && auth == "Bearer tok-1")) send(401, """{"code":"unauthorized","message":"Identita' non valida"}""")
                    else when (path) {
                        "/api/ai/analyze" -> if (analyzeJson != null) send(200, analyzeJson!!).setBodyDelay(analyzeDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS) else send(200, """{"name":"Giacca di jeans","category":"OUTERWEAR","colorHex":"#4A6FA5","colorName":"Denim","brand":"Levis","seasons":["SPRING","AUTUMN"],"occasions":["CASUAL"],"material":"Cotone","pattern":"","notes":""}""")
                        "/api/ai/chat" -> send(200, """{"reply":"Oggi ti consiglio jeans e maglia nera.","outfits":[{"title":"Casual","itemIds":$chatOutfitIds,"why":"Colori neutri"}]}""")
                        "/api/ai/purchase" -> send(200, """{"name":"Maglia nera","category":"TOP","colorHex":"#111111","colorName":"Nero","verdict":"skip","summary":"Ne hai già una uguale.","reasons":["Hai già la maglia nera"],"similarIds":[1],"pairsWellIds":[2],"outfitIdeas":[]}""")
                        else -> send(404, """{"code":"not_found","message":"Non trovato"}""")
                    }
                }
            }
        }
        server.start()
    }
    fun stop() = server.shutdown()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AiClientTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var srv: FakeServer
    private lateinit var prefs: Prefs
    private lateinit var client: AiClient

    @Before fun setUp() { srv = FakeServer(); prefs = Prefs(ctx); prefs.aiUrl = srv.url; prefs.aiEnabled = true; prefs.aiToken = ""; client = AiClient(prefs) }
    @After fun tearDown() = srv.stop()

    private fun photo() = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(74, 111, 165)) }
    private fun item(id: Long, name: String, status: String = "OWNED") = Item(id = id, name = name, imagePath = "x.png", category = Category.TOP.name,
        colorHex = "#111111", status = status, seasons = "WINTER", occasions = "CASUAL")

    @Test fun analyzeRegistersOnceAndSendsSmallJpeg() = runBlocking {
        val a = client.analyze(photo())
        assertEquals("Giacca di jeans", a.name); assertEquals("OUTERWEAR", a.category); assertEquals(listOf("SPRING", "AUTUMN"), a.seasons)
        client.analyze(photo())
        assertEquals(1, srv.registerCount)                                    // il token si riusa
        val (_, auth, body) = srv.requests.last()
        assertEquals("Bearer tok-1", auth)
        assertEquals("image/jpeg", body.getString("mediaType"))
        val bytes = Base64.decode(body.getString("imageBase64"), Base64.DEFAULT)
        assertTrue("JPEG valido", bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte())
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertTrue("ridimensionata a max 768px", maxOf(bmp.width, bmp.height) <= 768)
        assertTrue(body.getString("imageBase64").length < 1_800_000)
    }

    @Test fun expiredTokenIsRenewedTransparently() = runBlocking {
        srv.rejectFirstToken = true
        assertEquals("Giacca di jeans", client.analyze(photo()).name)
        assertEquals(2, srv.registerCount); assertEquals("tok-2", prefs.aiToken)
    }

    @Test fun chatSendsOnlyOwnedItemsAndHistory() = runBlocking {
        val closet = listOf(item(1, "Maglia"), item(2, "Jeans"), item(3, "Giacca desiderata", status = "WISHLIST"))
        val r = client.chat(listOf("user" to "Cosa metto?", "assistant" to "Boh", "user" to "E con la pioggia?"), closet, { 2 }, "Roma: 10°C")
        assertEquals(listOf(1L, 2L), r.outfits.single().itemIds)
        val body = srv.requests.last().third
        assertEquals(2, body.getJSONArray("closet").length())                // niente capi desiderati
        assertEquals(3, body.getJSONArray("messages").length())
        assertEquals("Roma: 10°C", body.getString("weather"))
        val i0 = body.getJSONArray("closet").getJSONObject(0)
        assertEquals("Nero", i0.getString("color")); assertEquals(2, i0.getInt("wears"))
    }

    @Test fun purchaseReturnsVerdict() = runBlocking {
        val r = client.purchase(photo(), listOf(item(1, "Maglia"), item(2, "Jeans")), { 0 })
        assertEquals("skip", r.verdict); assertEquals(listOf(1L), r.similarIds)
        assertTrue(srv.requests.last().third.has("closet"))
    }

    @Test fun errorsAreMappedToReadableMessages() = runBlocking {
        prefs.aiUrl = srv.url + "/niente"  // 404 con corpo JSON
        val e = runCatching { client.analyze(photo()) }.exceptionOrNull() as AiException
        assertEquals("unauthorized", e.code)  // la risposta d'errore del server arriva con codice e messaggio
        srv.stop()
        val net = runCatching { client.analyze(photo()) }.exceptionOrNull() as AiException
        assertEquals("network", net.code)
        prefs.aiEnabled = false
        assertEquals("disabled", (runCatching { client.analyze(photo()) }.exceptionOrNull() as AiException).code)
    }

    @Test fun healthReportsConfiguration() = runBlocking {
        assertTrue(client.health().contains("AI attiva"))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class AiUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun stylistChatShowsReplyAndOutfitCard() {
        val srv = FakeServer()
        ViewModelProvider(rule.activity)[AppViewModel::class.java].let { v -> runBlocking { v.dao.clearItems(); v.dao.clearOutfits(); v.dao.clearTrips() } }
        try {
            val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
            vm.setAi(true, srv.url)
            val ids = runBlocking {
                listOf("Maglia nera" to Category.TOP, "Jeans" to Category.BOTTOM).map { (n, c) ->
                    val img = ImageProc.saveItemImage(rule.activity, Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) })
                    vm.dao.insert(Item(name = n, imagePath = img, category = c.name, colorHex = "#111111"))
                }
            }
            srv.chatOutfitIds = ids.map { it.toInt() }
            rule.waitUntilExactlyOneExists(hasText("Cosa mi metto oggi?"), 8000)
            rule.onNodeWithText("✨ Chiedi allo stilista AI").performScrollTo().performClick()
            rule.waitUntilExactlyOneExists(hasText("Cosa mi metto oggi?").and(androidx.compose.ui.test.hasClickAction()), 8000)
            rule.onNodeWithText("Cosa mi metto oggi?").performClick()
            rule.waitUntilExactlyOneExists(hasText("Oggi ti consiglio jeans e maglia nera."), 10000)
            rule.waitUntilAtLeastOneExists(hasText("Casual"), 5000)
            rule.onNodeWithText("Lo indosso").performClick()
            for (k in 1..30) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(50) }
            assertEquals(2, runBlocking { vm.dao.wearLogsOnce().size })
            // la richiesta al server ha l'armadio e il messaggio dell'utente
            val chatReq = srv.requests.last { it.first == "/api/ai/chat" }.third
            assertEquals(2, chatReq.getJSONArray("closet").length())
            assertEquals("Cosa mi metto oggi?", chatReq.getJSONArray("messages").getJSONObject(0).getString("content"))
        } finally { srv.stop() }
    }

    @Test fun stylistExplainsWhenServerIsDown() {
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        vm.setAi(true, "http://127.0.0.1:1") // porta chiusa
        vm.sendChat("ciao")
        for (k in 1..60) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(50); if (!vm.chatBusy.value) break }
        val last = vm.chat.value.last()
        assertTrue(last.error); assertTrue(last.text.contains("non raggiungibile"))
        assertFalse(vm.chatBusy.value)
    }
}
