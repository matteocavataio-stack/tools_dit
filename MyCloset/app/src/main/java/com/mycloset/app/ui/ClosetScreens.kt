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

    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title = { Text(if (f.wishlist) "Desideri" else "Armadio") }, actions = {
                TextButton(onClick = { bulkPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Importa foto") }
            })
        },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { onAdd(f.wishlist) }, shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Aggiungi", style = MaterialTheme.typography.titleSmall) },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Segmented(listOf("Armadio", "Desideri"), if (f.wishlist) 1 else 0, Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth()) {
                vm.filter.value = f.copy(wishlist = it == 1)
            }
            androidx.compose.material3.TextField(
                value = f.query, onValueChange = { vm.filter.value = f.copy(query = it) }, singleLine = true,
                placeholder = { Text("Cerca un capo, una marca…") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                shape = RoundedCornerShape(50),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Tutti", f.category == null) { vm.filter.value = f.copy(category = null) }
                Category.entries.forEach { c ->
                    Chip(c.label, f.category == c) { vm.filter.value = f.copy(category = if (f.category == c) null else c) }
                }
            }
            Row(Modifier.padding(start = 24.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${shown.size} ${if (shown.size == 1) "capo" else "capi"}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (f.onlyFavorites) FilterTag("♥ Preferiti") { vm.filter.value = f.copy(onlyFavorites = false) }
                if (f.neverWorn) FilterTag("Mai indossati") { vm.filter.value = f.copy(neverWorn = false) }
                (f.colorHex ?: f.brand)?.let { tag -> FilterTag(tag) { vm.filter.value = f.copy(colorHex = null, brand = null) } }
                Spacer(Modifier.weight(1f))
                Box {
                    TextButton(onClick = { menuOpen = true }) { Text("Ordina e filtra") }
                    androidx.compose.material3.DropdownMenu(menuOpen, { menuOpen = false }) {
                        listOf("recent" to "Più recenti", "worn" to "Più indossati", "price" to "Prezzo più alto", "name" to "Nome A–Z").forEach { (k, l) ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text(if (sort == k) "✓ $l" else l) }, onClick = { sort = k; menuOpen = false })
                        }
                        androidx.compose.material3.HorizontalDivider()
                        androidx.compose.material3.DropdownMenuItem(text = { Text(if (f.onlyFavorites) "✓ Solo preferiti" else "Solo preferiti") },
                            onClick = { vm.filter.value = f.copy(onlyFavorites = !f.onlyFavorites); menuOpen = false })
                        androidx.compose.material3.DropdownMenuItem(text = { Text(if (f.neverWorn) "✓ Mai indossati" else "Mai indossati") },
                            onClick = { vm.filter.value = f.copy(neverWorn = !f.neverWorn); menuOpen = false })
                    }
                }
            }
            if (bulkBusy != null) Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp)); Text(bulkBusy!!)
            }
            if (shown.isEmpty() && bulkBusy == null) {
                EmptyState(
                    if (f.wishlist) "Nessun desiderio" else "Armadio vuoto",
                    "Tocca \"Aggiungi\" e fotografa un capo: l'AI riconosce tipo e colore e compila la scheda, tu confermi. " +
                        "Con \"Importa foto\" ne aggiungi molti in una volta.",
                )
            }
            LazyVerticalGrid(GridCells.Adaptive(104.dp), contentPadding = PaddingValues(20.dp, 6.dp, 20.dp, 100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(shown, key = { it.id }) { it2 ->
                    ItemTile(it2, badge = if (f.wishlist) null else stats.wears(it2).takeIf { n -> n > 0 }?.let { n -> "${n}×" }) { onOpen(it2.id) }
                }
            }
        }
    }
}

@Composable
private fun FilterTag(text: String, onRemove: () -> Unit) {
    Text("$text ✕", Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer)
        .clickable(onClick = onRemove).padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
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

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        AppTopBar(title = { Text(item?.name ?: "") },
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
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
