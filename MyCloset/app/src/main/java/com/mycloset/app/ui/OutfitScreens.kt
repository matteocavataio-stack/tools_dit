package com.mycloset.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mycloset.app.AppViewModel
import com.mycloset.app.data.Category
import com.mycloset.app.data.Item
import com.mycloset.app.data.Occasion
import com.mycloset.app.data.Outfit
import com.mycloset.app.data.OutfitItem
import com.mycloset.app.data.Season
import com.mycloset.app.data.csv
import com.mycloset.app.data.toCsv
import com.mycloset.app.logic.Stylist
import java.time.LocalDate
import kotlin.math.roundToInt

// =====================================================================================
//  ELENCO OUTFIT
// =====================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitsScreen(vm: AppViewModel, onOpen: (Long) -> Unit, onCreate: () -> Unit) {
    val outfits by vm.outfits.collectAsState()
    var occ by remember { mutableStateOf<Occasion?>(null) }
    var favOnly by remember { mutableStateOf(false) }
    val shown = outfits.filter { (occ == null || it.occasion == occ!!.name) && (!favOnly || it.favorite) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, 
        topBar = { AppTopBar(title = { Text("Outfit") }) },
        floatingActionButton = { FloatingActionButton(onClick = onCreate, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) { Icon(Icons.Filled.Add, "Crea outfit") } },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Tutti", occ == null && !favOnly) { occ = null; favOnly = false }
                Chip("♥ Preferiti", favOnly) { favOnly = !favOnly }
                Occasion.entries.forEach { o -> Chip(o.label, occ == o) { occ = if (occ == o) null else o } }
            }
            if (shown.isEmpty()) EmptyState("Nessun outfit",
                "Crea il tuo primo outfit: scegli i capi dall'armadio e disponili sulla tela, oppure lasciati suggerire un abbinamento.")
            LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(shown, key = { it.id }) { o ->
                    Card(onClick = { onOpen(o.id) }) {
                        Box {
                            OutfitImage(o, Modifier.fillMaxWidth().aspectRatio(0.75f))
                            if (o.favorite) Icon(Icons.Filled.Favorite, null, tint = Color(0xFFE11D48),
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp))
                        }
                        if (o.name.isNotBlank()) Text(o.name, Modifier.padding(8.dp), maxLines = 1,
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

// =====================================================================================
//  DETTAGLIO OUTFIT
// =====================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitDetailScreen(vm: AppViewModel, outfitId: Long, onBack: () -> Unit, onEdit: () -> Unit, onOpenItem: (Long) -> Unit) {
    val outfits by vm.outfits.collectAsState()
    val outfitItems by vm.outfitItems.collectAsState()
    val items by vm.items.collectAsState()
    val outfit = outfits.firstOrNull { it.id == outfitId }
    var showDate by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val parts = outfitItems.filter { it.outfitId == outfitId }.mapNotNull { p -> items.firstOrNull { it.id == p.itemId } }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        AppTopBar(title = { Text(outfit?.name?.ifBlank { "Outfit" } ?: "") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            actions = {
                if (outfit != null) {
                    IconButton(onClick = { vm.toggleFavorite(outfit) }) {
                        Icon(if (outfit.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Preferito",
                            tint = if (outfit.favorite) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, "Elimina") }
                }
            })
    }) { pad ->
        if (outfit == null) return@Scaffold
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            OutfitImage(outfit, Modifier.padding(16.dp).fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(20.dp)))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.wear(LocalDate.now(), parts.map { it.id }, outfitId) }, Modifier.weight(1f), enabled = parts.isNotEmpty()) { Text("Indossato oggi") }
                OutlinedButton(onClick = { showDate = true }, Modifier.weight(1f), enabled = parts.isNotEmpty()) { Text("Pianifica") }
            }
            OutlinedButton(onClick = onEdit, Modifier.padding(16.dp, 8.dp).fillMaxWidth()) { Text("Modifica outfit") }
            SectionTitle("Capi (${parts.size})")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                parts.forEach { it2 ->
                    Box(Modifier.width(100.dp).aspectRatio(0.85f).clip(RoundedCornerShape(12.dp)).background(Paper)
                        .clickable { onOpenItem(it2.id) }) { ItemImage(it2, Modifier.fillMaxSize().padding(6.dp)) }
                }
            }
        }
    }
    if (showDate) DatePickerDlg(LocalDate.now(), { showDate = false }) {
        vm.wear(it, parts.map { p -> p.id }, outfitId); showDate = false
    }
    if (showDelete && outfit != null) ConfirmDialog("Eliminare l'outfit?", "I capi restano nell'armadio.",
        onDismiss = { showDelete = false }) { vm.deleteOutfit(outfit); onBack() }
}


// =====================================================================================
//  EDITOR (tela libera)
// =====================================================================================
private data class Placed(val item: Item, val x: Float, val y: Float, val scale: Float, val rotation: Float, val z: Int)

internal fun defaultSlot(c: Category): Triple<Float, Float, Float> = when (c) { // x, y, scala
    Category.TOP -> Triple(0.50f, 0.27f, 1.15f)
    Category.BOTTOM -> Triple(0.50f, 0.58f, 1.25f)
    Category.DRESS -> Triple(0.50f, 0.42f, 1.5f)
    Category.OUTERWEAR -> Triple(0.27f, 0.30f, 1.15f)
    Category.SHOES -> Triple(0.50f, 0.86f, 0.7f)
    Category.BAGS -> Triple(0.80f, 0.62f, 0.7f)
    Category.ACCESSORIES -> Triple(0.20f, 0.78f, 0.5f)
    Category.OTHER -> Triple(0.50f, 0.50f, 1f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitEditorScreen(vm: AppViewModel, outfitId: Long, onClose: (Long?) -> Unit) {
    val items by vm.items.collectAsState()
    val outfits by vm.outfits.collectAsState()
    val allPlacements by vm.outfitItems.collectAsState()
    val logs by vm.wearLogs.collectAsState()
    val weather by vm.weather.collectAsState()
    val existing = outfits.firstOrNull { it.id == outfitId }
    val owned = items.filter { it.status == "OWNED" }

    val board = remember { mutableStateListOf<Placed>() }
    var selected by remember { mutableStateOf<Long?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var occasion by remember { mutableStateOf<String?>(null) }
    var seasons by remember { mutableStateOf(setOf<String>()) }
    var loaded by remember { mutableStateOf(outfitId == 0L) }
    var seed by remember { mutableStateOf(System.nanoTime()) }
    var saving by remember { mutableStateOf(false) }
    var zCounter by remember { mutableStateOf(0) }

    LaunchedEffect(existing, items, allPlacements) {
        if (!loaded && existing != null && items.isNotEmpty()) {
            loaded = true
            name = existing.name; occasion = existing.occasion.ifBlank { null }; seasons = existing.seasons.csv()
            allPlacements.filter { it.outfitId == existing.id }.sortedBy { it.z }.forEach { p ->
                items.firstOrNull { it.id == p.itemId }?.let { board += Placed(it, p.x, p.y, p.scale, p.rotation, zCounter++) }
            }
        }
    }

    fun addItem(i: Item) {
        if (board.any { it.item.id == i.id }) return
        val (x, y, s) = defaultSlot(Category.of(i.category))
        // se la posizione e' occupata da un capo della stessa categoria, lo sostituisce
        board.removeAll { Category.of(it.item.category) == Category.of(i.category) &&
            Category.of(i.category) in setOf(Category.TOP, Category.BOTTOM, Category.DRESS, Category.SHOES) }
        board += Placed(i, x, y, s, 0f, zCounter++)
        selected = i.id
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        AppTopBar(
            title = { Text(if (outfitId == 0L) "Nuovo outfit" else "Modifica outfit") },
            navigationIcon = { IconButton(onClick = { onClose(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            actions = { Button(enabled = board.isNotEmpty() && !saving, onClick = { showSave = true }) { Text("Salva") }; Spacer(Modifier.width(8.dp)) },
        )
    }) { pad ->
        Column(Modifier.padding(pad)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                // tela 3:4 che sta nello spazio disponibile
                val h = if (maxWidth / maxHeight > 0.75f) maxHeight else maxWidth / 0.75f
                val w = h * 0.75f
                val density = LocalDensity.current
                val wPx = with(density) { w.toPx() }; val hPx = with(density) { h.toPx() }
                val baseDp = w * 0.42f
                val basePx = with(density) { baseDp.toPx() }
                Box(Modifier.width(w).height(h).clip(RoundedCornerShape(16.dp)).background(Paper)
                    .pointerInput(Unit) { detectTapGestures { selected = null } }) {
                    if (board.isEmpty()) Text("Aggiungi dei capi con il pulsante qui sotto", Modifier.align(Alignment.Center).padding(24.dp),
                        color = Color.Gray)
                    board.sortedBy { it.z }.forEach { p ->
                        val id = p.item.id
                        Box(
                            Modifier
                                .offset { IntOffset((p.x * wPx - basePx / 2).roundToInt(), (p.y * hPx - basePx / 2).roundToInt()) }
                                .size(baseDp)
                                .pointerInput(id) { detectTapGestures(onTap = { selected = id }) }
                                .pointerInput(id) {
                                    detectTransformGestures { _, pan, zoom, rot ->
                                        val i = board.indexOfFirst { it.item.id == id }
                                        if (i >= 0) {
                                            val c = board[i]
                                            board[i] = c.copy(
                                                x = (c.x + pan.x / wPx).coerceIn(0.02f, 0.98f),
                                                y = (c.y + pan.y / hPx).coerceIn(0.02f, 0.98f),
                                                scale = (c.scale * zoom).coerceIn(0.2f, 4f),
                                                rotation = c.rotation + rot,
                                            )
                                            selected = id
                                        }
                                    }
                                }
                                .graphicsLayer(rotationZ = p.rotation, scaleX = p.scale, scaleY = p.scale)
                                .then(if (selected == id) Modifier.background(Color(0x1A7C3AED), RoundedCornerShape(8.dp)) else Modifier)
                        ) { ItemImage(p.item, Modifier.fillMaxSize()) }
                    }
                }
            }
            // ---- barra strumenti ----
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showPicker = true }) { Text("+ Capi") }
                OutlinedButton(onClick = {
                    val recent = logs.filter { it.date >= LocalDate.now().toEpochDay() - 3 }.map { it.itemId }.toSet()
                    val s = Stylist.generate(owned, weather?.feelsLike, weather?.rainy ?: false, null, recent, seed++)
                    if (s != null) { board.clear(); zCounter = 0; s.items.forEach { addItem(it) }; selected = null }
                }, enabled = owned.isNotEmpty()) { Text("Suggerisci") }
                val sel = board.firstOrNull { it.item.id == selected }
                if (sel != null) {
                    OutlinedButton(onClick = {
                        val i = board.indexOfFirst { it.item.id == selected }
                        if (i >= 0) board[i] = board[i].copy(z = zCounter++)
                    }) { Text("Porta avanti") }
                    OutlinedButton(onClick = { board.removeAll { it.item.id == selected }; selected = null }) { Text("Rimuovi") }
                }
                if (board.isNotEmpty()) TextButton(onClick = { board.clear(); selected = null }) { Text("Svuota") }
            }
            Text("Trascina per spostare · pizzica per ingrandire e ruotare", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showPicker) ItemPickerDialog(owned, "Aggiungi all'outfit", initial = board.map { it.item.id }.toSet(),
        onDismiss = { showPicker = false }) { picked ->
        showPicker = false
        board.removeAll { b -> picked.none { it.id == b.item.id } }
        picked.forEach { addItem(it) }
    }

    if (showSave) AlertDialog(
        onDismissRequest = { showSave = false },
        title = { Text("Salva outfit") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome (facoltativo)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp)); Text("Occasione", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Occasion.entries.forEach { o -> Chip(o.label, occasion == o.name) { occasion = if (occasion == o.name) null else o.name } }
                }
                Spacer(Modifier.height(8.dp)); Text("Stagioni", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Season.entries.forEach { s -> Chip(s.label, s.name in seasons) {
                        seasons = if (s.name in seasons) seasons - s.name else seasons + s.name } }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                saving = true
                val placements = board.sortedBy { it.z }.mapIndexed { idx, p ->
                    p.item to OutfitItem(0, p.item.id, p.x, p.y, p.scale, p.rotation, idx)
                }
                val base = existing ?: Outfit()
                vm.saveOutfit(base.copy(name = name.trim(), occasion = occasion ?: "", seasons = seasons.toCsv()), placements) { id ->
                    saving = false; showSave = false; onClose(id)
                }
            }) { Text(if (saving) "Salvo…" else "Salva") }
        },
        dismissButton = { TextButton(onClick = { showSave = false }) { Text("Annulla") } },
    )
}

