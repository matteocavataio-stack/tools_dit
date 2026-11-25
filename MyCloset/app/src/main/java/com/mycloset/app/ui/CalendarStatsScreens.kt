package com.mycloset.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mycloset.app.AppViewModel
import com.mycloset.app.data.Category
import com.mycloset.app.data.Outfit
import com.mycloset.app.logic.Colors
import com.mycloset.app.logic.Stats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// =====================================================================================
//  CALENDARIO OUTFIT (OOTD)
// =====================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: AppViewModel, onOpenOutfit: (Long) -> Unit, onOpenItem: (Long) -> Unit) {
    val items by vm.items.collectAsState()
    val outfits by vm.outfits.collectAsState()
    val logs by vm.wearLogs.collectAsState()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var pickItems by remember { mutableStateOf(false) }
    var pickOutfit by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val byDay = remember(logs) { logs.groupBy { it.date } }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { AppTopBar(title = { Text("Calendario") }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mese precedente") }
                Text(month.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() } + " ${month.year}",
                    Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mese successivo") }
            }
            Row(Modifier.padding(horizontal = 8.dp)) {
                DayOfWeek.entries.forEach { d ->
                    Text(d.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).take(3), Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val first = month.atDay(1)
            val offset = first.dayOfWeek.value - 1
            val cells = (0 until 42).map { first.plusDays((it - offset).toLong()) }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cells.chunked(7).filter { row -> row.any { YearMonth.from(it) == month } }.forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { d ->
                            val inMonth = YearMonth.from(d) == month
                            val entries = byDay[d.toEpochDay()].orEmpty()
                            val outfit = entries.firstNotNullOfOrNull { e -> outfits.firstOrNull { it.id == e.outfitId } }
                            val firstItem = entries.firstOrNull()?.let { e -> items.firstOrNull { it.id == e.itemId } }
                            Box(
                                Modifier.weight(1f).aspectRatio(0.72f).clip(RoundedCornerShape(14.dp))
                                    .background(if (inMonth) Paper else Color.Transparent)
                                    .then(when {
                                        d == selected -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                                        d == today -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(14.dp))
                                        else -> Modifier
                                    })
                                    .clickable { selected = d; if (!inMonth) month = YearMonth.from(d) }
                            ) {
                                if (inMonth) {
                                    when {
                                        outfit != null -> OutfitImage(outfit, Modifier.fillMaxSize())
                                        firstItem != null -> ItemImage(firstItem, Modifier.fillMaxSize().padding(4.dp))
                                    }
                                    Text("${d.dayOfMonth}", Modifier.align(Alignment.TopStart).padding(4.dp)
                                        .background(Color(0x88FFFFFF), RoundedCornerShape(6.dp)).padding(horizontal = 4.dp),
                                        style = MaterialTheme.typography.labelSmall, color = Color.Black,
                                        fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal)
                                    if (d.isAfter(today) && entries.isNotEmpty())
                                        Text("●", Modifier.align(Alignment.BottomEnd).padding(4.dp), color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            // ---- Giorno selezionato ----
            val entries = byDay[selected.toEpochDay()].orEmpty()
            SectionTitle(selected.dayOfMonth.toString() + " " +
                selected.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN) + if (selected.isAfter(today)) " · pianificato" else "")
            val outfitGroups = entries.filter { it.outfitId != null }.groupBy { it.outfitId!! }
            outfitGroups.forEach { (oid, es) ->
                val o = outfits.firstOrNull { it.id == oid }
                if (o != null) Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onOpenOutfit(oid) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutfitImage(o, Modifier.width(56.dp).aspectRatio(0.75f).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.name.ifBlank { "Outfit" }, fontWeight = FontWeight.SemiBold)
                        Text("${es.size} capi", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { vm.unwearOutfit(selected, oid) }) { Icon(Icons.Filled.Close, "Rimuovi") }
                }
            }
            val single = entries.filter { it.outfitId == null || outfits.none { o -> o.id == it.outfitId } }
            if (single.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                single.forEach { e ->
                    items.firstOrNull { it.id == e.itemId }?.let { it2 ->
                        Box(Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)).background(Paper).clickable { onOpenItem(it2.id) }) {
                            ItemImage(it2, Modifier.fillMaxSize().padding(4.dp))
                            Icon(Icons.Filled.Close, "Rimuovi", Modifier.align(Alignment.TopEnd).size(22.dp)
                                .clickable { vm.unwearItem(selected, it2.id) }.padding(2.dp)
                                .background(Color(0x88FFFFFF), RoundedCornerShape(11.dp)))
                        }
                    }
                }
            }
            if (entries.isEmpty()) Text("Nessun outfit registrato per questo giorno.", Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pickOutfit = true }, Modifier.weight(1f), enabled = outfits.isNotEmpty()) { Text("Aggiungi outfit") }
                OutlinedButton(onClick = { pickItems = true }, Modifier.weight(1f)) { Text("Aggiungi capi") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickItems) ItemPickerDialog(items.filter { it.status == "OWNED" }, "Capi per il ${selected.dayOfMonth}/${selected.monthValue}",
        onDismiss = { pickItems = false }) { picked -> vm.wear(selected, picked.map { it.id }); pickItems = false }
    if (pickOutfit) OutfitPickerDialog(outfits, onDismiss = { pickOutfit = false }) { o ->
        val parts = vm.outfitItems.value.filter { it.outfitId == o.id }.map { it.itemId }
        vm.wear(selected, parts, o.id); pickOutfit = false
    }
}

@Composable
fun OutfitPickerDialog(outfits: List<Outfit>, onDismiss: () -> Unit, onPick: (Outfit) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Chiudi") }
                    Text("Scegli un outfit", style = MaterialTheme.typography.titleLarge)
                }
                LazyVerticalGrid(GridCells.Adaptive(140.dp), contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(outfits, key = { it.id }) { o ->
                        Card(onClick = { onPick(o) }) {
                            OutfitImage(o, Modifier.fillMaxWidth().aspectRatio(0.75f))
                            if (o.name.isNotBlank()) Text(o.name, Modifier.padding(8.dp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================================
//  STATISTICHE
// =====================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: AppViewModel, onOpenItem: (Long) -> Unit, onOpenCloset: () -> Unit) {
    val items by vm.items.collectAsState()
    val logs by vm.wearLogs.collectAsState()
    val currency by vm.currency.collectAsState()
    val s = remember(items, logs) { Stats(items, logs) }
    val f by vm.filter.collectAsState()

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { AppTopBar(title = { Text("Statistiche") }) }) { pad ->
        if (s.owned.isEmpty()) {
            Box(Modifier.padding(pad)) { EmptyState("Ancora nessun dato", "Aggiungi capi con il prezzo e registra cosa indossi: qui vedrai costo per utilizzo, capi mai usati e molto altro.") }
            return@Scaffold
        }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Capi", "${s.owned.size}", Modifier.weight(1f))
                StatBox("Valore", money(s.totalValue, currency), Modifier.weight(1f))
                StatBox("Utilizzi", "${s.totalWears}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Costo medio/uso", if (s.avgCpw > 0) money(s.avgCpw, currency) else "—", Modifier.weight(1f))
                StatBox("Mai indossati", "${s.neverWorn.size}", Modifier.weight(1f))
            }

            SectionTitle("Per categoria")
            val cats = s.byCategory()
            cats.forEach { (c, n) ->
                BarRow(Category.of(c).label, n, cats.first().second, MaterialTheme.colorScheme.primary) {
                    vm.filter.value = f.copy(category = Category.of(c), colorHex = null, brand = null, wishlist = false); onOpenCloset()
                }
            }

            SectionTitle("Per colore")
            val cols = s.byColor().take(8)
            cols.forEach { (name, n) ->
                val hex = s.owned.first { Colors.name(it.colorHex) == name }.colorHex
                BarRow(name, n, cols.first().second, Color(Colors.parse(hex)), dot = hex) {
                    vm.filter.value = f.copy(colorHex = name, category = null, brand = null, wishlist = false); onOpenCloset()
                }
            }

            val brands = s.byBrand().take(6)
            if (brands.isNotEmpty()) {
                SectionTitle("Marche")
                brands.forEach { (b, n) -> BarRow(b, n, brands.first().second, MaterialTheme.colorScheme.tertiary) {
                    vm.filter.value = f.copy(brand = b, category = null, colorHex = null, wishlist = false); onOpenCloset()
                } }
            }

            val spend = s.spendingByMonth()
            if (spend.any { it.second > 0 }) {
                SectionTitle("Spesa negli ultimi 12 mesi")
                val max = spend.maxOf { it.second }
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom) {
                    spend.forEach { (d, v) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Box(Modifier.fillMaxWidth().height((90 * (v / max)).dp.coerceAtLeast(if (v > 0) 3.dp else 0.dp))
                                .clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.secondary))
                            Text(d.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).take(1).uppercase(),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text("Totale: ${money(spend.sumOf { it.second }, currency)}", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge)
            }

            RankSection("I più indossati", s.mostWorn.take(5), s, currency, showCpw = false, onOpenItem)
            RankSection("Miglior costo per utilizzo", s.bestCpw.take(5), s, currency, showCpw = true, onOpenItem)
            RankSection("Costo per utilizzo più alto", s.worstCpw.take(5), s, currency, showCpw = true, onOpenItem)
            if (s.neverWorn.isNotEmpty()) {
                SectionTitle("Mai indossati · ${s.neverWorn.size} capi (${money(s.neverWorn.sumOf { it.price }, currency)})")
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.neverWorn.take(12).forEach { it2 ->
                        Box(Modifier.size(90.dp).clip(RoundedCornerShape(12.dp)).background(Paper).clickable { onOpenItem(it2.id) }) {
                            ItemImage(it2, Modifier.fillMaxSize().padding(4.dp))
                        }
                    }
                }
                TextButton(onClick = { vm.filter.value = ClosetFilterNeverWorn(f); onOpenCloset() }, Modifier.padding(horizontal = 8.dp)) {
                    Text("Vedi tutti nell'armadio")
                }
            }
        }
    }
}

private fun ClosetFilterNeverWorn(f: com.mycloset.app.ClosetFilter) =
    f.copy(neverWorn = true, category = null, colorHex = null, brand = null, wishlist = false)

@Composable
private fun BarRow(label: String, value: Int, max: Int, color: Color, dot: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(120.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) { ColorDot(dot, 14); Spacer(Modifier.width(6.dp)) }
            Text(label, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
        }
        Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(value / max.toFloat()).fillMaxSize().clip(RoundedCornerShape(7.dp)).background(color))
        }
        Text("$value", Modifier.width(36.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RankSection(title: String, list: List<com.mycloset.app.data.Item>, s: Stats, cur: String, showCpw: Boolean, onOpen: (Long) -> Unit) {
    if (list.isEmpty()) return
    SectionTitle(title)
    list.forEachIndexed { i, it2 ->
        Row(Modifier.fillMaxWidth().clickable { onOpen(it2.id) }.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}", Modifier.width(24.dp), fontWeight = FontWeight.Bold)
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Paper)) { ItemImage(it2, Modifier.fillMaxSize().padding(2.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(it2.name, maxLines = 1)
                Text("${s.wears(it2)} utilizzi", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (showCpw) money(s.costPerWear(it2), cur) + "/uso" else "${s.wears(it2)}×", fontWeight = FontWeight.SemiBold)
        }
    }
}
