package com.mycloset.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mycloset.app.AppViewModel
import com.mycloset.app.data.Category
import com.mycloset.app.data.Item
import com.mycloset.app.data.Occasion
import com.mycloset.app.data.Season
import com.mycloset.app.data.csv
import com.mycloset.app.data.toCsv
import com.mycloset.app.logic.AiAnalysis
import com.mycloset.app.logic.AiException
import com.mycloset.app.logic.Colors
import com.mycloset.app.logic.ImageProc
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Stage { CAPTURE, ANALYZING, REVIEW }
private enum class Step { WAIT, RUN, DONE, SKIP }
private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN)

/**
 * Aggiunta/modifica di un capo, in tre passi:
 *  1. foto (fotocamera o galleria)  2. analisi automatica (sfondo + AI)  3. conferma: la scheda e' gia' compilata, si corregge solo se serve.
 * [startBitmap] permette di partire da una foto gia' scelta (anche per i test).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(vm: AppViewModel, itemId: Long, startWishlist: Boolean, onClose: () -> Unit, startBitmap: Bitmap? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val allItems by vm.items.collectAsState()
    val existing = remember(itemId, allItems) { allItems.firstOrNull { it.id == itemId } }
    val currency by vm.currency.collectAsState()

    var stage by remember { mutableStateOf(if (itemId != 0L) Stage.REVIEW else Stage.CAPTURE) }
    var loaded by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var processed by remember { mutableStateOf<Bitmap?>(null) }
    var useCutout by remember { mutableStateOf(true) }
    var stepCut by remember { mutableStateOf(Step.WAIT) }
    var stepAi by remember { mutableStateOf(Step.WAIT) }
    var source by remember { mutableStateOf("") }      // "ai" | "local"
    var aiError by remember { mutableStateOf<String?>(null) }
    var loadingPhoto by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.TOP) }
    var colorHex by remember { mutableStateOf("#808080") }
    var brand by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var purchase by remember { mutableStateOf<LocalDate?>(null) }
    var seasons by remember { mutableStateOf(setOf<String>()) }
    var occasions by remember { mutableStateOf(setOf<String>()) }
    var material by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var wishlist by remember { mutableStateOf(startWishlist) }
    var moreOpen by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun resetForm() {
        original = null; processed = null; useCutout = true; source = ""; aiError = null
        name = ""; category = Category.TOP; colorHex = "#808080"; brand = ""; price = ""; purchase = null
        seasons = emptySet(); occasions = emptySet(); material = ""; pattern = ""; notes = ""; moreOpen = false
        stepCut = Step.WAIT; stepAi = Step.WAIT; stage = Stage.CAPTURE; saving = false
    }

    LaunchedEffect(existing) {
        if (!loaded && existing != null) {
            loaded = true
            name = existing.name; category = Category.of(existing.category); colorHex = existing.colorHex
            brand = existing.brand; price = if (existing.price > 0) existing.price.toString().removeSuffix(".0") else ""
            purchase = existing.purchaseDate?.let { LocalDate.ofEpochDay(it) }
            seasons = existing.seasons.csv(); occasions = existing.occasions.csv()
            material = existing.material; pattern = existing.pattern; notes = existing.notes
            wishlist = existing.status == "WISHLIST"
        }
    }

    fun applyAi(a: AiAnalysis) {
        if (a.name.isNotBlank()) name = a.name
        category = Category.of(a.category)
        if (a.colorHex.matches(Regex("#[0-9A-Fa-f]{6}"))) colorHex = a.colorHex
        if (a.brand.isNotBlank()) brand = a.brand
        if (a.seasons.isNotEmpty()) seasons = a.seasons.toSet()
        if (a.occasions.isNotEmpty()) occasions = a.occasions.toSet()
        if (a.material.isNotBlank()) material = a.material
        if (a.pattern.isNotBlank()) pattern = a.pattern
        if (a.notes.isNotBlank()) notes = a.notes
    }

    /** Passo 2: sfondo (sul telefono) e riconoscimento (AI, con ripiego sul telefono) in parallelo. */
    fun analyze(bmp: Bitmap) {
        original = bmp; processed = null; aiError = null; source = ""
        stepCut = Step.RUN; stepAi = if (vm.ai.configured) Step.RUN else Step.SKIP
        stage = Stage.ANALYZING
        scope.launch {
            coroutineScope {
                val cutJob = async { ImageProc.removeBackground(bmp) }
                val labelJob = async { ImageProc.suggestCategory(bmp) }
                val aiJob = async {
                    if (!vm.ai.configured) null else try {
                        withTimeoutOrNull(25_000) { vm.ai.analyze(bmp) }.also { if (it == null) aiError = "L'AI ha impiegato troppo tempo." }
                    } catch (e: AiException) { aiError = e.message; null } catch (e: Exception) { aiError = "AI non raggiungibile."; null }
                }
                val cut = cutJob.await()
                processed = cut; useCutout = cut != null; stepCut = Step.DONE
                val ai = aiJob.await()
                if (stepAi != Step.SKIP) stepAi = Step.DONE
                val localCat = labelJob.await()
                if (ai != null) { applyAi(ai); source = "ai" }
                else {
                    category = localCat ?: Category.TOP
                    colorHex = Colors.dominant(cut ?: bmp)
                    name = category.label
                    source = "local"
                }
            }
            stage = Stage.REVIEW
        }
    }

    LaunchedEffect(Unit) { if (startBitmap != null && itemId == 0L) analyze(startBitmap) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch { loadingPhoto = true; ImageProc.load(ctx, uri)?.let { analyze(it) }; loadingPhoto = false }
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) scope.launch { loadingPhoto = true; ImageProc.load(ctx, uri)?.let { analyze(it) }; loadingPhoto = false }
    }
    fun launchCamera() {
        val f = File(ctx.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        cameraUri = u; camera.launch(u)
    }

    val bmpToSave: Bitmap? = if (original == null) null else if (useCutout) processed ?: original else original

    fun save(another: Boolean) {
        if (saving) return
        saving = true
        val item = (existing ?: Item()).copy(
            name = name.ifBlank { category.label }, category = category.name, colorHex = colorHex,
            brand = brand.trim(), price = price.replace(',', '.').toDoubleOrNull() ?: 0.0,
            purchaseDate = purchase?.toEpochDay(), seasons = seasons.toCsv(), occasions = occasions.toCsv(),
            material = material.trim(), pattern = pattern.trim(), notes = notes.trim(),
            status = if (wishlist) "WISHLIST" else "OWNED",
        )
        vm.saveItem(item, bmpToSave) { if (another) resetForm() else onClose() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = { Text(when {
                    existing != null -> "Modifica capo"
                    stage == Stage.REVIEW -> "Conferma"
                    wishlist -> "Nuovo desiderio"
                    else -> "Nuovo capo"
                }) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = { if (existing != null) IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, "Elimina") } },
            )
        },
        bottomBar = {
            if (stage == Stage.REVIEW) Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    BigButton(if (saving) "Salvo…" else if (existing != null) "Salva modifiche" else "Conferma e salva", enabled = !saving) { save(false) }
                    if (existing == null) TextButton(onClick = { save(true) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                        Text("Salva e aggiungi un altro")
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (stage) {
                Stage.CAPTURE -> CapturePage(loadingPhoto, vm.ai.configured, onCamera = { launchCamera() }, onGallery = {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                Stage.ANALYZING -> AnalyzingPage(original, stepCut, stepAi)
                Stage.REVIEW -> Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    // --- foto + esito del riconoscimento ---
                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).aspectRatio(1.7f).clip(RoundedCornerShape(28.dp)).background(Paper),
                        contentAlignment = Alignment.Center) {
                        val shown = if (original != null) (if (useCutout) processed ?: original else original) else null
                        when {
                            shown != null -> Image(shown.asImageBitmap(), null, Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit)
                            existing != null -> ItemImage(existing, Modifier.fillMaxSize().padding(12.dp))
                        }
                    }
                    when (source) {
                        "ai" -> Banner("✨ Riconosciuto dall'AI: controlla e correggi se serve.", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        "local" -> Banner(
                            (if (aiError != null) "AI non disponibile (${aiError}). " else "") + "Riconosciuto sul telefono: controlla bene i dati.",
                            MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillAction("Rifai foto", AppIcons.Camera) { launchCamera() }
                        if (original != null && processed != null) PillAction(if (useCutout) "Mostra originale" else "Senza sfondo", null) { useCutout = !useCutout }
                        if (original != null && vm.ai.configured) PillAction("✨ Rianalizza", null) { original?.let { analyze(it) } }
                    }

                    // --- scheda (gia' compilata) ---
                    Label("Che cos'è?")
                    OutlinedTextField(name, { name = it }, singleLine = true, shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                    Label("Dove lo metto? (categoria)")
                    ChipRow { Category.entries.forEach { c -> Chip(c.label, category == c) { category = c } } }
                    Label("Colore · ${Colors.name(colorHex)}")
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (Colors.palette.none { it.second.equals(colorHex, true) }) Box(Modifier.size(38.dp).clip(CircleShape).background(Color(Colors.parse(colorHex)))
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape))
                        Colors.palette.forEach { (_, hex) ->
                            val sel = hex.equals(colorHex, true)
                            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(Colors.parse(hex)))
                                .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                                .clickable { colorHex = hex })
                        }
                    }
                    Label("Stagioni")
                    ChipRow { Season.entries.forEach { s -> Chip(s.label, s.name in seasons) { seasons = if (s.name in seasons) seasons - s.name else seasons + s.name } } }
                    Label("Occasioni")
                    ChipRow { Occasion.entries.forEach { o -> Chip(o.label, o.name in occasions) { occasions = if (o.name in occasions) occasions - o.name else occasions + o.name } } }

                    TextButton(onClick = { moreOpen = !moreOpen }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(if (moreOpen) "Meno dettagli" else "Più dettagli (marca, prezzo, materiale…)")
                    }
                    if (moreOpen) Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(brand, { brand = it }, label = { Text("Marca") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f))
                            OutlinedTextField(price, { price = it }, label = { Text("Prezzo ($currency)") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { showDate = true }, shape = RoundedCornerShape(50)) {
                                Text(purchase?.format(dateFmt)?.let { "Acquistato il $it" } ?: "Data d'acquisto") }
                            if (purchase != null) TextButton(onClick = { purchase = null }) { Text("Rimuovi") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(material, { material = it }, label = { Text("Materiale") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f))
                            OutlinedTextField(pattern, { pattern = it }, label = { Text("Fantasia") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(notes, { notes = it }, label = { Text("Note") }, minLines = 2, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Non ce l'ho ancora (lista desideri)", Modifier.weight(1f)); Switch(wishlist, { wishlist = it })
                        }
                    }
                }
            }
        }
    }

    if (showDate) DatePickerDlg(purchase, { showDate = false }) { purchase = it; showDate = false }
    if (showDelete && existing != null) ConfirmDialog("Eliminare il capo?",
        "Verrà tolto anche da outfit, calendario e valigie.", onDismiss = { showDelete = false }) { vm.deleteItem(existing); onClose() }
}

@Composable
private fun PillAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp)) }
        Text(label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Label(text: String) =
    Text(text, Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall)

@Composable
private fun Banner(text: String, bg: Color, fg: Color) {
    Text(text, Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(12.dp),
        color = fg, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun CapturePage(loading: Boolean, aiOn: Boolean, onCamera: () -> Unit, onGallery: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(150.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Camera, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(24.dp))
        Text("Fotografa il capo", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(if (aiOn) "Ci pensa l'AI: riconosce il tipo di capo, il colore e compila la scheda al posto tuo. Tu devi solo confermare."
            else "Riconosco il tipo di capo e il colore e compilo la scheda: tu devi solo confermare.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Meglio su uno sfondo semplice (letto, muro, appendiabiti) e con il capo tutto in inquadratura.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        BigButton("Scatta foto", enabled = !loading, onClick = onCamera)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onGallery, enabled = !loading, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(27.dp)) {
            Icon(AppIcons.Gallery, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Scegli dalla galleria")
        }
        if (loading) { Spacer(Modifier.height(16.dp)); CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
    }
}

@Composable
private fun AnalyzingPage(photo: Bitmap?, cut: Step, ai: Step) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.1f).clip(RoundedCornerShape(28.dp)).background(Paper), contentAlignment = Alignment.Center) {
            if (photo != null) Image(photo.asImageBitmap(), null, Modifier.fillMaxSize().padding(12.dp).alpha(pulse), contentScale = ContentScale.Fit)
        }
        Spacer(Modifier.height(24.dp))
        Text("Sto analizzando il capo…", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(14.dp))
        Column(Modifier.width(280.dp)) {
            StepRow("Tolgo lo sfondo", cut)
            if (ai != Step.SKIP) StepRow("L'AI riconosce tipo e colore", ai)
            else StepRow("Riconosco tipo e colore", cut)
        }
        Text("Al primo utilizzo può servire qualche secondo in più.", Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StepRow(label: String, step: Step) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (step) {
                Step.DONE -> Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(24.dp)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape).padding(4.dp))
                Step.RUN -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp)
                else -> Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (step == Step.WAIT) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
    }
}
