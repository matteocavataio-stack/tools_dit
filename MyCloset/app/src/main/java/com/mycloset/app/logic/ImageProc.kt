package com.mycloset.app.logic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.mycloset.app.data.Category
import com.mycloset.app.data.Item
import com.mycloset.app.data.OutfitItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
}

object ImageProc {
    private const val MAX_SIDE = 1024

    fun dir(context: Context, sub: String) = File(context.filesDir, sub).apply { mkdirs() }
    fun itemFile(context: Context, name: String) = File(dir(context, "items"), name)
    fun outfitFile(context: Context, name: String) = File(dir(context, "outfits"), name)

    /** Carica una foto ridimensionata e con l'orientamento EXIF corretto. */
    suspend fun load(context: Context, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext null
            val rot = context.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            var bmp = raw
            if (rot != 0f) bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rot) }, true)
            val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
            if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
            bmp.copy(Bitmap.Config.ARGB_8888, true)
        } catch (_: Exception) { null }
    }

    /**
     * Rimuove lo sfondo. Prima prova ML Kit (modello su dispositivo, al primo uso lo scarica via Play Services),
     * altrimenti, se lo sfondo e' uniforme, usa un riempimento dai bordi. Ritorna null se non riesce.
     */
    suspend fun removeBackground(bmp: Bitmap): Bitmap? = withContext(Dispatchers.Default) {
        val ml = try {
            val seg = SubjectSegmentation.getClient(
                SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
            )
            val res = seg.process(InputImage.fromBitmap(bmp, 0)).await()
            seg.close()
            res.foregroundBitmap
        } catch (_: Throwable) { null }
        val out = ml ?: floodFillBackground(bmp)
        out?.let { cropToContent(it) }
    }

    /** Sfondo uniforme (bianco, muro...): rende trasparenti i pixel simili al colore dei bordi. */
    fun floodFillBackground(src: Bitmap, tol: Int = 42): Bitmap? {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        fun r(p: Int) = (p shr 16) and 255
        fun g(p: Int) = (p shr 8) and 255
        fun b(p: Int) = p and 255
        // colore medio dei bordi
        var sr = 0L; var sg = 0L; var sb = 0L; var n = 0
        fun add(p: Int) { sr += r(p); sg += g(p); sb += b(p); n++ }
        for (x in 0 until w) { add(px[x]); add(px[(h - 1) * w + x]) }
        for (y in 0 until h) { add(px[y * w]); add(px[y * w + w - 1]) }
        val br = (sr / n).toInt(); val bg = (sg / n).toInt(); val bb = (sb / n).toInt()
        fun close(p: Int) = abs(r(p) - br) + abs(g(p) - bg) + abs(b(p) - bb) < tol * 3 / 2
        // il bordo deve essere quasi tutto uniforme, altrimenti non e' affidabile
        var uniform = 0
        for (x in 0 until w) { if (close(px[x])) uniform++; if (close(px[(h - 1) * w + x])) uniform++ }
        for (y in 0 until h) { if (close(px[y * w])) uniform++; if (close(px[y * w + w - 1])) uniform++ }
        if (uniform < n * 0.9) return null

        val visited = BooleanArray(w * h)
        val q = ArrayDeque<Int>()
        fun push(i: Int) { if (!visited[i] && close(px[i])) { visited[i] = true; q.add(i) } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (q.isNotEmpty()) {
            val i = q.poll()!!
            val x = i % w; val y = i / w
            if (x > 0) push(i - 1); if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w); if (y < h - 1) push(i + w)
        }
        var removed = 0
        for (i in px.indices) if (visited[i]) { px[i] = 0; removed++ }
        if (removed < px.size * 0.05 || removed > px.size * 0.95) return null // nulla da togliere / tolto tutto
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** Ritaglia i bordi trasparenti, lasciando un piccolo margine. */
    fun cropToContent(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 24) {
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        if (maxX < 0) return src
        val pad = (maxOf(maxX - minX, maxY - minY) * 0.03f).toInt()
        val x0 = (minX - pad).coerceAtLeast(0); val y0 = (minY - pad).coerceAtLeast(0)
        val x1 = (maxX + pad).coerceAtMost(w - 1); val y1 = (maxY + pad).coerceAtMost(h - 1)
        return Bitmap.createBitmap(src, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    }

    /** Suggerisce la categoria dal contenuto della foto (modello ML Kit incluso nell'app). */
    suspend fun suggestCategory(bmp: Bitmap): Category? = withContext(Dispatchers.Default) {
        try {
            val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.45f).build())
            val labels = labeler.process(InputImage.fromBitmap(bmp, 0)).await().map { it.text.lowercase() }
            labeler.close()
            mapLabels(labels)
        } catch (_: Throwable) { null }
    }

    fun mapLabels(rawLabels: List<String>): Category? {
        val labels = rawLabels.map { it.lowercase() }
        val rules = listOf(
            Category.SHOES to listOf("shoe", "footwear", "sneaker", "boot", "sandal", "heel", "slipper", "loafer"),
            Category.BAGS to listOf("bag", "handbag", "backpack", "luggage", "purse", "satchel"),
            Category.DRESS to listOf("dress", "gown", "jumpsuit", "romper"),
            Category.OUTERWEAR to listOf("jacket", "coat", "blazer", "parka", "raincoat", "cardigan", "outerwear", "windbreaker"),
            Category.BOTTOM to listOf("jeans", "trousers", "pants", "shorts", "skirt", "leggings", "denim"),
            Category.ACCESSORIES to listOf("hat", "cap", "glasses", "sunglasses", "scarf", "belt", "watch", "necklace",
                "jewellery", "jewelry", "tie", "glove", "earring", "bracelet", "ring"),
            Category.TOP to listOf("shirt", "t-shirt", "sweater", "hoodie", "blouse", "top", "polo", "sleeve", "jersey", "sweatshirt", "undershirt"),
        )
        for ((cat, keys) in rules) if (labels.any { l -> keys.any { k -> l.contains(k) } }) return cat
        return null
    }

    suspend fun saveItemImage(context: Context, bmp: Bitmap): String = withContext(Dispatchers.IO) {
        val name = UUID.randomUUID().toString() + ".png"
        itemFile(context, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        name
    }

    /** Disegna l'outfit sulla tela (stesso criterio dell'editor) e lo salva come PNG. */
    suspend fun renderOutfit(
        context: Context,
        placements: List<Pair<Item, OutfitItem>>,
        w: Int = 900, h: Int = 1200,
        bg: Int = 0xFFF7F4EF.toInt(),
    ): String = withContext(Dispatchers.IO) {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.drawColor(bg)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for ((item, p) in placements.sortedBy { it.second.z }) {
            val bmp = BitmapFactory.decodeFile(itemFile(context, item.imagePath).path) ?: continue
            val base = (w * 0.42f) / maxOf(bmp.width, bmp.height) * p.scale
            val m = Matrix()
            m.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
            m.postScale(base, base)
            m.postRotate(p.rotation)
            m.postTranslate(p.x * w, p.y * h)
            c.drawBitmap(bmp, m, paint)
            bmp.recycle()
        }
        val name = UUID.randomUUID().toString() + ".png"
        outfitFile(context, name).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        name
    }
}
