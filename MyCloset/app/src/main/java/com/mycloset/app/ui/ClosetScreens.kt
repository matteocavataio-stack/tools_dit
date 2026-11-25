package com.mycloset.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.mycloset.app.ClosetFilter
import com.mycloset.app.data.Category
import com.mycloset.app.data.Item
import com.mycloset.app.data.Occasion
import com.mycloset.app.data.Season
import com.mycloset.app.data.csv
import com.mycloset.app.data.toCsv
import com.mycloset.app.logic.Colors
import com.mycloset.app.logic.ImageProc
import com.mycloset.app.logic.Stats
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN)

// =====================================================================================
//  ARMADIO
// =====================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosetScreen(vm: AppViewModel, onOpen: (Long) -> Unit, onAdd: (wishlist: Boolean) -> Unit) {
    val items by vm.items.collectAsState()
    val logs by vm.wearLogs.collectAsState()
    val f by vm.filter.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val stats = remember(items, logs) { Stats(items, logs) }
    var sort by remember { mutableStateOf("recent") }
    var bulkBusy by remember { mutableStateOf<String?>(null) }

    val bulkPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            uris.forEachIndexed { i, uri ->
                bulkBusy = "Elaboro foto ${i + 1} di ${uris.size}…"
                val bmp = ImageProc.load(ctx, uri) ?: return@forEachIndexed
                val cut = ImageProc.removeBackground(bmp) ?: bmp
                val cat = ImageProc.suggestCategory(bmp) ?: Category.TOP
                val hex = Colors.dominant(cut)
                val name = ImageProc.saveItemImage(ctx, cut)
                vm.dao.insert(Item(name = cat.label, imagePath = name, category = cat.name, colorHex = hex,
                    status = if (f.wishlist) "WISHLIST" else "OWNED"))
            }
            bulkBusy = null
        }
    }

    val shown = items.filter { it.status == (if (f.wishlist) "WISHLIST" else "OWNED") }.filter {
        (f.category == null || it.category == f.category!!.name) &&
            (f.colorHex == null || Colors.name(it.colorHex) == f.colorHex) &&
            (f.brand == null || it.brand.trim() == f.brand) &&
            (!f.onlyFavorites || it.favorite) &&
            (!f.neverWorn || stats.wears(it) == 0) &&
            (f.query.isBlank() || it.name.contains(f.query, true) || it.brand.contains(f.query, true) ||
                it.material.contains(f.query, true) || it.notes.contains(f.query, true))
    }.let { list ->
        when (sort) {
            "worn" -> list.sortedByDescending { stats.wears(it) }
            "price" -> list.sortedByDescending { it.price }
            "name" -> list.sortedBy { it.name.lowercase() }
            else -> list
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (f.wishlist) "Lista desideri" else "Il mio armadio") }, actions = {
                TextButton(onClick = {
                    bulkPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Importa foto") }
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAdd(f.wishlist) }) { Icon(Icons.Filled.Add, "Aggiungi capo") }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(f.query, { vm.filter.value = f.copy(query = it) }, singleLine = true,
                placeholder = { Text("Cerca per nome, marca, materiale…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Armadio", !f.wishlist) { vm.filter.value = f.copy(wishlist = false) }
                Chip("Desideri", f.wishlist) { vm.filter.value = f.copy(wishlist = true) }
                Chip("♥ Preferiti", f.onlyFavorites) { vm.filter.value = f.copy(onlyFavorites = !f.onlyFavorites) }
                Chip("Mai indossati", f.neverWorn) { vm.filter.value = f.copy(neverWorn = !f.neverWorn) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Tutte", f.category == null) { vm.filter.value = f.copy(category = null) }
                Category.entries.forEach { c ->
                    Chip(c.label, f.category == c) { vm.filter.value = f.copy(category = if (f.category == c) null else c) }
                }
            }
            if (f.colorHex != null || f.brand != null) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Filtro: ${f.colorHex ?: f.brand}", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { vm.filter.value = f.copy(colorHex = null, brand = null) }) { Text("Rimuovi") }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${shown.size} capi", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                listOf("recent" to "Recenti", "worn" to "Più usati", "price" to "Prezzo", "name" to "Nome").forEach { (k, l) ->
                    TextButton(onClick = { sort = k }) {
                        Text(l, fontWeight = if (sort == k) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (bulkBusy != null) Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp)); Text(bulkBusy!!)
            }
            if (shown.isEmpty() && bulkBusy == null) {
                EmptyState(
                    if (f.wishlist) "Nessun desiderio" else "Armadio vuoto",
                    "Tocca + per fotografare un capo, oppure \"Importa foto\" per aggiungerne molti in una volta. " +
                        "Lo sfondo viene rimosso e categoria e colore riconosciuti in automatico.",
                )
            }
            LazyVerticalGrid(GridCells.Adaptive(110.dp), contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 88.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { it2 ->
                    ItemTile(it2, badge = if (f.wishlist) null else stats.wears(it2).takeIf { n -> n > 0 }?.let { n -> "${n}×" }) {
                        onOpen(it2.id)
                    }
                }
            }
        }
    }
}

// =====================================================================================
//  AGGIUNGI / MODIFICA CAPO
// =====================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(vm: AppViewModel, itemId: Long, startWishlist: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val allItems by vm.items.collectAsState()
    val existing = remember(itemId, allItems) { allItems.firstOrNull { it.id == itemId } }
    val currency by vm.currency.collectAsState()

    var loaded by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var processed by remember { mutableStateOf<Bitmap?>(null) }
    var useCutout by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<String?>(null) }
    var cutoutFailed by remember { mutableStateOf(false) }
    val aiOn by vm.aiEnabled.collectAsState()
    var aiNote by remember { mutableStateOf<String?>(null) }

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
    var showDate by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

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

    /** Compila la scheda con il server AI (se attivo). In caso di errore restano i dati rilevati sul telefono. */
    fun runAi(photo: Bitmap) {
        scope.launch {
            busy = "L'AI sta guardando il capo…"; aiNote = null
            try {
                val a = vm.ai.analyze(photo)
                if (a.name.isNotBlank()) name = a.name
                category = Category.of(a.category)
                if (a.colorHex.matches(Regex("#[0-9A-Fa-f]{6}"))) colorHex = a.colorHex
                if (a.brand.isNotBlank()) brand = a.brand
                if (a.seasons.isNotEmpty()) seasons = a.seasons.toSet()
                if (a.occasions.isNotEmpty()) occasions = a.occasions.toSet()
                if (a.material.isNotBlank()) material = a.material
                if (a.pattern.isNotBlank()) pattern = a.pattern
                if (a.notes.isNotBlank() && notes.isBlank()) notes = a.notes
                aiNote = "Scheda compilata dall'AI: controlla e correggi se serve."
            } catch (e: com.mycloset.app.logic.AiException) {
                aiNote = "AI non disponibile (${e.message}) — uso il riconoscimento sul telefono."
            } catch (e: Exception) { aiNote = "AI non disponibile." }
            busy = null
        }
    }

    fun process(bmp: Bitmap) {
        original = bmp; processed = null; cutoutFailed = false
        scope.launch {
            busy = "Rimuovo lo sfondo…"
            val cut = ImageProc.removeBackground(bmp)
            processed = cut; cutoutFailed = cut == null; useCutout = cut != null
            busy = "Riconosco il capo…"
            val shown = cut ?: bmp
            colorHex = Colors.dominant(shown)
            ImageProc.suggestCategory(bmp)?.let { if (existing == null) category = it }
            if (name.isBlank()) name = category.label
            busy = null
            if (vm.ai.configured && existing == null) runAi(bmp)
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch { busy = "Carico la foto…"; ImageProc.load(ctx, uri)?.let { process(it) }; busy = null }
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) scope.launch { busy = "Carico la foto…"; ImageProc.load(ctx, uri)?.let { process(it) }; busy = null }
    }
    fun launchCamera() {
        val f = File(ctx.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        cameraUri = u; camera.launch(u)
    }

    val bmpToSave: Bitmap? = if (original == null) null else if (useCutout) processed ?: original else original
    val canSave = (existing != null || bmpToSave != null) && busy == null

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (existing != null) "Modifica capo" else if (wishlist) "Nuovo desiderio" else "Nuovo capo") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            actions = {
                if (existing != null) IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, "Elimina") }
                Button(enabled = canSave, onClick = {
                    val base = existing ?: Item()
                    val item = base.copy(
                        name = name.ifBlank { category.label }, category = category.name, colorHex = colorHex,
                        brand = brand.trim(), price = price.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        purchaseDate = purchase?.toEpochDay(), seasons = seasons.toCsv(), occasions = occasions.toCsv(),
                        material = material.trim(), pattern = pattern.trim(), notes = notes.trim(),
                        status = if (wishlist) "WISHLIST" else "OWNED",
                    )
                    vm.saveItem(item, bmpToSave) { onClose() }
                }) { Text("Salva") }
                Spacer(Modifier.width(8.dp))
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            // ---- Foto ----
            Box(Modifier.fillMaxWidth().padding(16.dp).aspectRatio(1.2f).clip(RoundedCornerShape(20.dp)).background(Paper),
                contentAlignment = Alignment.Center) {
                val shownBmp = if (original != null) (if (useCutout) processed ?: original else original) else null
                when {
                    shownBmp != null -> androidx.compose.foundation.Image(shownBmp.asImageBitmap(), null,
                        Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                    existing != null -> ItemImage(existing, Modifier.fillMaxSize().padding(8.dp))
                    else -> Text("Scatta una foto o scegline una dalla galleria", color = Color.Gray,
                        modifier = Modifier.padding(24.dp))
                }
                if (busy != null) Box(Modifier.fillMaxSize().background(Color(0x99FFFFFF)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(); Spacer(Modifier.height(8.dp))
                        Text(busy!!, color = Color.Black)
                        Text("Al primo utilizzo può servire qualche secondo in più", color = Color.DarkGray,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { launchCamera() }, Modifier.weight(1f)) { Text("Scatta foto") }
                OutlinedButton(onClick = {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, Modifier.weight(1f)) { Text("Galleria") }
            }
            if (original != null && aiOn) {
                OutlinedButton(onClick = { original?.let { runAi(it) } }, enabled = busy == null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("✨ Analizza con AI") }
            }
            aiNote?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary) }
            if (original != null) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (cutoutFailed) "Sfondo non rimovibile automaticamente" else "Sfondo rimosso",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = useCutout, onCheckedChange = { useCutout = it }, enabled = processed != null)
                }
            }

            // ---- Dati ----
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            SectionTitle("Categoria")
            ChipRow { Category.entries.forEach { c -> Chip(c.label, category == c) { category = c } } }
            SectionTitle("Colore · ${Colors.name(colorHex)}")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Colors.palette.forEach { (_, hex) ->
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(Colors.parse(hex)))
                        .border(if (hex.equals(colorHex, true)) 3.dp else 1.dp,
                            if (hex.equals(colorHex, true)) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                        .clickable { colorHex = hex })
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ColorDot(colorHex, 22); Spacer(Modifier.width(8.dp))
                Text("Selezionato (rilevato automaticamente dalla foto)", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(brand, { brand = it }, label = { Text("Marca") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(price, { price = it }, label = { Text("Prezzo ($currency)") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f))
            }
            Row(Modifier.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showDate = true }) {
                    Text(purchase?.format(dateFmt)?.let { "Acquistato il $it" } ?: "Data d'acquisto")
                }
                if (purchase != null) TextButton(onClick = { purchase = null }) { Text("Rimuovi") }
            }
            SectionTitle("Stagioni")
            ChipRow { Season.entries.forEach { s ->
                Chip(s.label, s.name in seasons) { seasons = if (s.name in seasons) seasons - s.name else seasons + s.name }
            } }
            SectionTitle("Occasioni")
            ChipRow { Occasion.entries.forEach { o ->
                Chip(o.label, o.name in occasions) { occasions = if (o.name in occasions) occasions - o.name else occasions + o.name }
            } }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(material, { material = it }, label = { Text("Materiale") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(pattern, { pattern = it }, label = { Text("Fantasia") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("Note") }, minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(16.dp))
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Metti nella lista desideri (non lo possiedo ancora)", Modifier.weight(1f))
                Switch(wishlist, { wishlist = it })
            }
        }
    }

    if (showDate) DatePickerDlg(purchase, { showDate = false }) { purchase = it; showDate = false }
    if (showDelete && existing != null) ConfirmDialog("Eliminare il capo?",
        "Verrà tolto anche da outfit, calendario e valigie.", onDismiss = { showDelete = false }) {
        vm.deleteItem(existing); onClose()
    }
}

// =====================================================================================
//  DETTAGLIO CAPO
// =====================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(vm: AppViewModel, itemId: Long, onBack: () -> Unit, onEdit: () -> Unit, onOpenOutfit: (Long) -> Unit) {
    val items by vm.items.collectAsState()
    val logs by vm.wearLogs.collectAsState()
    val outfits by vm.outfits.collectAsState()
    val outfitItems by vm.outfitItems.collectAsState()
    val currency by vm.currency.collectAsState()
    val item = items.firstOrNull { it.id == itemId }
    val stats = remember(items, logs) { Stats(items, logs) }
    var showDate by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    Scaffold(topBar = {
        TopAppBar(title = { Text(item?.name ?: "") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            actions = {
                if (item != null) {
                    IconButton(onClick = { vm.toggleFavorite(item) }) {
                        Icon(if (item.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Preferito",
                            tint = if (item.favorite) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Modifica") }
                }
            })
    }) { pad ->
        if (item == null) { Box(Modifier.padding(pad)) { EmptyState("Capo non trovato", "") }; return@Scaffold }
        val wears = stats.wears(item)
        val inOutfits = outfits.filter { o -> outfitItems.any { it.outfitId == o.id && it.itemId == item.id } }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Box(Modifier.fillMaxWidth().padding(16.dp).aspectRatio(1.2f).clip(RoundedCornerShape(20.dp)).background(Paper)) {
                ItemImage(item, Modifier.fillMaxSize().padding(8.dp))
            }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                ColorDot(item.colorHex, 20); Spacer(Modifier.width(8.dp))
                Text("${Category.of(item.category).label} · ${Colors.name(item.colorHex)}" +
                    (if (item.brand.isNotBlank()) " · ${item.brand}" else ""), style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(12.dp))
            if (item.status == "OWNED") {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox("Indossato", "$wears ×", Modifier.weight(1f))
                    StatBox("Costo/uso", if (item.price > 0) money(stats.costPerWear(item), currency) else "—", Modifier.weight(1f))
                    StatBox("Prezzo", if (item.price > 0) money(item.price, currency) else "—", Modifier.weight(1f))
                }
                stats.lastWorn(item)?.let {
                    Text("Ultima volta: ${it.format(dateFmt)}", Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.bodyMedium)
                } ?: Text("Non l'hai ancora indossato.", Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.wear(today, listOf(item.id)) }, Modifier.weight(1f)) { Text("Indossato oggi") }
                    OutlinedButton(onClick = { showDate = true }, Modifier.weight(1f)) { Text("Scegli data") }
                }
            } else {
                Button(onClick = { vm.markPurchased(item) }, Modifier.padding(16.dp).fillMaxWidth()) { Text("L'ho acquistato!") }
            }
            val details = listOfNotNull(
                item.seasons.csv().takeIf { it.isNotEmpty() }?.joinToString { Season.valueOf(it).label }?.let { "Stagioni" to it },
                item.occasions.csv().takeIf { it.isNotEmpty() }?.joinToString { Occasion.valueOf(it).label }?.let { "Occasioni" to it },
                item.material.takeIf { it.isNotBlank() }?.let { "Materiale" to it },
                item.pattern.takeIf { it.isNotBlank() }?.let { "Fantasia" to it },
                item.purchaseDate?.let { "Acquistato il" to LocalDate.ofEpochDay(it).format(dateFmt) },
                item.notes.takeIf { it.isNotBlank() }?.let { "Note" to it },
            )
            if (details.isNotEmpty()) {
                SectionTitle("Dettagli")
                details.forEach { (k, v) ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                        Text(k, Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v)
                    }
                }
            }
            if (inOutfits.isNotEmpty()) {
                SectionTitle("Presente in ${inOutfits.size} outfit")
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    inOutfits.forEach { o ->
                        OutfitImage(o, Modifier.width(110.dp).aspectRatio(0.75f).clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenOutfit(o.id) })
                    }
                }
            }
        }
    }
    if (showDate) DatePickerDlg(today, { showDate = false }) { vm.wear(it, listOf(itemId)); showDate = false }
}

@Composable
fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
