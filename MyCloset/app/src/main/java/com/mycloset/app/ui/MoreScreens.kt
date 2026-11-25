package com.mycloset.app.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mycloset.app.AppViewModel
import com.mycloset.app.data.Category
import com.mycloset.app.data.Item
import com.mycloset.app.data.Trip
import com.mycloset.app.logic.Backup
import com.mycloset.app.logic.Colors
import com.mycloset.app.logic.ImageProc
import com.mycloset.app.logic.MatchReport
import com.mycloset.app.logic.Place
import com.mycloset.app.logic.PurchaseReport
import com.mycloset.app.logic.Stylist
import com.mycloset.app.logic.WeatherApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)

// =====================================================================================
//  ABBINAMENTO
// =====================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScreen(vm: AppViewModel, onBack: () -> Unit) {
    val items by vm.items.collectAsState()
    var picked by remember { mutableStateOf<List<Item>>(emptyList()) }
    var showPicker by remember { mutableStateOf(false) }
    val report: MatchReport? = if (picked.size >= 2) Stylist.match(picked) else null

    Scaffold(topBar = {
        TopAppBar(title = { Text("Questo abbinamento va bene?") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } })
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Button(onClick = { showPicker = true }, Modifier.fillMaxWidth()) { Text(if (picked.isEmpty()) "Scegli i capi" else "Cambia capi") }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                picked.forEach { Box(Modifier.width(96.dp).aspectRatio(0.85f).clip(RoundedCornerShape(12.dp)).background(Paper)) { ItemImage(it, Modifier.padding(4.dp)) } }
            }
            if (report != null) {
                Spacer(Modifier.height(16.dp))
                Text("${report.score}/100", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(report.verdict, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                report.tips.forEach { Text("• $it", Modifier.padding(vertical = 2.dp)) }
                Spacer(Modifier.height(12.dp))
                Text("Valutazione basata su armonia dei colori, categorie, stagioni e occasioni.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Text("Scegli almeno due capi dal tuo armadio.", Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showPicker) ItemPickerDialog(items.filter { it.status == "OWNED" }, "Scegli i capi", picked.map { it.id }.toSet(),
        onDismiss = { showPicker = false }) { picked = it; showPicker = false }
}

// =====================================================================================
//  DEVO COMPRARLO?
// =====================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseScreen(vm: AppViewModel, onBack: () -> Unit, onOpenItem: (Long) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by vm.items.collectAsState()
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(Category.TOP) }
    var colorHex by remember { mutableStateOf("#808080") }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val b = ImageProc.load(ctx, uri)
            if (b != null) {
                val cut = ImageProc.removeBackground(b) ?: b
                bmp = cut; colorHex = Colors.dominant(cut)
                ImageProc.suggestCategory(b)?.let { category = it }
            }
            busy = false
        }
    }
    val report: PurchaseReport? = if (bmp != null) Stylist.purchaseCheck(items, category, colorHex) else null
    val aiOn by vm.aiEnabled.collectAsState()
    var aiBusy by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<com.mycloset.app.logic.AiPurchase?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Devo comprarlo?") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } })
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Carica la foto del capo che stai pensando di comprare (anche da uno screenshot di un negozio online).")
            Spacer(Modifier.height(8.dp))
            Button(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth()) {
                Text("Scegli foto")
            }
            if (busy) Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text("Analizzo…")
            }
            bmp?.let { b ->
                Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(16.dp)).background(Paper)) {
                    androidx.compose.foundation.Image(b.asImageBitmap(), null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                }
                Text("Categoria", fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { c -> Chip(c.label, category == c) { category = c } }
                }
                Spacer(Modifier.height(8.dp))
                Text("Colore: ${Colors.name(colorHex)}", fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Colors.palette.forEach { (_, hex) -> Box(Modifier.clickable { colorHex = hex }) {
                        ColorDot(hex, if (hex.equals(colorHex, true)) 36 else 28) } }
                }
            }
            if (bmp != null && aiOn) {
                Spacer(Modifier.height(12.dp))
                Button(enabled = !aiBusy, modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.launch {
                        aiBusy = true; aiError = null
                        try { aiResult = vm.ai.purchase(bmp!!, items, vm::wearsOf) }
                        catch (e: com.mycloset.app.logic.AiException) { aiError = e.message }
                        catch (e: Exception) { aiError = "Qualcosa e' andato storto: riprova." }
                        aiBusy = false
                    }
                }) { Text(if (aiBusy) "L'AI sta valutando…" else "✨ Parere dello stilista AI") }
                aiError?.let { Text(it, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.error) }
            }
            aiResult?.let { r ->
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(when (r.verdict) { "buy" -> "Sì, compralo"; "skip" -> "Meglio evitare"; else -> "Dipende" },
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(r.summary, Modifier.padding(top = 4.dp))
                        r.reasons.forEach { Text("• $it", Modifier.padding(top = 4.dp)) }
                        if (r.similarIds.isNotEmpty()) {
                            Text("Simili che hai già", Modifier.padding(top = 10.dp), fontWeight = FontWeight.SemiBold)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                r.similarIds.mapNotNull { id -> items.firstOrNull { it.id == id } }.forEach { i ->
                                    Box(Modifier.width(76.dp).aspectRatio(0.85f).clip(RoundedCornerShape(10.dp)).background(Paper).clickable { onOpenItem(i.id) }) {
                                        ItemImage(i, Modifier.padding(4.dp)) } }
                            }
                        }
                        if (r.pairsWellIds.isNotEmpty()) {
                            Text("Ci si abbina", Modifier.padding(top = 10.dp), fontWeight = FontWeight.SemiBold)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                r.pairsWellIds.mapNotNull { id -> items.firstOrNull { it.id == id } }.forEach { i ->
                                    Box(Modifier.width(76.dp).aspectRatio(0.85f).clip(RoundedCornerShape(10.dp)).background(Paper).clickable { onOpenItem(i.id) }) {
                                        ItemImage(i, Modifier.padding(4.dp)) } }
                            }
                        }
                        r.outfitIdeas.forEach { AiOutfitCard(vm, it, items, onOpenItem) }
                    }
                }
            }
            if (report != null) {
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(report.verdict, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        report.tips.forEach { Text("• $it", Modifier.padding(top = 4.dp)) }
                    }
                }
                if (report.similar.isNotEmpty()) {
                    SectionTitle("Hai già qualcosa di simile")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        report.similar.forEach { i -> Box(Modifier.width(92.dp).aspectRatio(0.85f).clip(RoundedCornerShape(12.dp)).background(Paper)
                            .clickable { onOpenItem(i.id) }) { ItemImage(i, Modifier.padding(4.dp)) } }
                    }
                }
                if (report.pairsWith.isNotEmpty()) {
                    SectionTitle("Si abbinerebbe con")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        report.pairsWith.forEach { i -> Box(Modifier.width(92.dp).aspectRatio(0.85f).clip(RoundedCornerShape(12.dp)).background(Paper)
                            .clickable { onOpenItem(i.id) }) { ItemImage(i, Modifier.padding(4.dp)) } }
                    }
                }
            }
        }
    }
}

// =====================================================================================
//  VIAGGI / VALIGIA
// =====================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(vm: AppViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val trips by vm.trips.collectAsState()
    val tripItems by vm.tripItems.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Viaggi") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } }) },
        floatingActionButton = { FloatingActionButton(onClick = { showNew = true }) { Icon(Icons.Filled.Add, "Nuovo viaggio") } },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            if (trips.isEmpty()) EmptyState("Nessun viaggio", "Crea un viaggio e prepara la valigia con i capi del tuo armadio.")
            trips.forEach { t ->
                val its = tripItems.filter { it.tripId == t.id }
                Card(onClick = { onOpen(t.id) }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(t.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text((if (t.city.isNotBlank()) t.city + " · " else "") +
                            LocalDate.ofEpochDay(t.startDate).format(shortDate) + " – " + LocalDate.ofEpochDay(t.endDate).format(shortDate),
                            style = MaterialTheme.typography.bodyMedium)
                        Text("${its.count { it.packed }}/${its.size} in valigia", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(88.dp))
        }
    }
    if (showNew) NewTripDialog(onDismiss = { showNew = false }) { vm.saveTrip(it); showNew = false }
}

@Composable
private fun NewTripDialog(onDismiss: () -> Unit, onSave: (Trip) -> Unit) {
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(LocalDate.now().plusDays(7)) }
    var end by remember { mutableStateOf(LocalDate.now().plusDays(10)) }
    var pick by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Nuovo viaggio") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Nome (es. Weekend a Parigi)") }, singleLine = true)
                OutlinedTextField(city, { city = it }, label = { Text("Destinazione") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pick = 1 }) { Text("Dal ${start.format(shortDate)}") }
                    OutlinedButton(onClick = { pick = 2 }) { Text("Al ${end.format(shortDate)}") }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            onSave(Trip(name = name.trim(), city = city.trim(), startDate = start.toEpochDay(), endDate = maxOf(start, end).toEpochDay()))
        }) { Text("Crea") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
    if (pick == 1) DatePickerDlg(start, { pick = 0 }) { start = it; if (end < it) end = it; pick = 0 }
    if (pick == 2) DatePickerDlg(end, { pick = 0 }) { end = it; pick = 0 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(vm: AppViewModel, tripId: Long, onBack: () -> Unit) {
    val trips by vm.trips.collectAsState()
    val tripItems by vm.tripItems.collectAsState()
    val items by vm.items.collectAsState()
    val logs by vm.wearLogs.collectAsState()
    val trip = trips.firstOrNull { it.id == tripId }
    var showPicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val mine = tripItems.filter { it.tripId == tripId }
    val days = trip?.let { (ChronoUnit.DAYS.between(LocalDate.ofEpochDay(it.startDate), LocalDate.ofEpochDay(it.endDate)) + 1).toInt() } ?: 1

    Scaffold(topBar = {
        TopAppBar(title = { Text(trip?.name ?: "") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            actions = { IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, "Elimina viaggio") } })
    }) { pad ->
        if (trip == null) return@Scaffold
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text("$days giorni" + if (trip.city.isNotBlank()) " a ${trip.city}" else "", Modifier.padding(16.dp, 8.dp),
                style = MaterialTheme.typography.titleMedium)
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showPicker = true }, Modifier.weight(1f)) { Text("Aggiungi capi") }
                OutlinedButton(onClick = {
                    // Valigia suggerita in base ai giorni, privilegiando i capi che usi di piu'
                    val wornCount = logs.groupBy { it.itemId }.mapValues { it.value.size }
                    fun best(c: Category, n: Int) = items.filter { it.status == "OWNED" && it.category == c.name }
                        .sortedByDescending { (wornCount[it.id] ?: 0) + if (it.favorite) 2 else 0 }.take(n)
                    val picks = best(Category.TOP, (days * 0.8).toInt().coerceIn(2, 8)) +
                        best(Category.BOTTOM, ((days + 1) / 2).coerceIn(1, 5)) +
                        best(Category.SHOES, if (days > 3) 2 else 1) + best(Category.OUTERWEAR, 1)
                    picks.forEach { vm.addToTrip(tripId, it.id) }
                }, Modifier.weight(1f)) { Text("Suggerisci") }
            }
            if (mine.isEmpty()) EmptyState("Valigia vuota", "Aggiungi i capi da portare, oppure usa \"Suggerisci\".")
            mine.forEach { ti ->
                val it2 = items.firstOrNull { it.id == ti.itemId } ?: return@forEach
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(ti.packed, { vm.setPacked(ti, it) })
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Paper)) { ItemImage(it2, Modifier.fillMaxSize().padding(3.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(it2.name, maxLines = 1)
                        Text(Category.of(it2.category).label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.removeFromTrip(ti) }) { Icon(Icons.Filled.Delete, "Rimuovi") }
                }
            }
        }
    }
    if (showPicker) ItemPickerDialog(items.filter { it.status == "OWNED" }, "Capi in valigia", mine.map { it.itemId }.toSet(),
        onDismiss = { showPicker = false }) { picked -> picked.forEach { vm.addToTrip(tripId, it.id) }; showPicker = false }
    if (showDelete && trip != null) ConfirmDialog("Eliminare il viaggio?", "La lista della valigia andrà persa.",
        onDismiss = { showDelete = false }) { vm.deleteTrip(trip); onBack() }
}

// =====================================================================================
//  IMPOSTAZIONI
// =====================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val city by vm.city.collectAsState()
    val currency by vm.currency.collectAsState()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = "Creo il backup…"
            msg = try { val n = Backup.export(ctx, vm.dao, uri); "Backup salvato ($n foto)." } catch (e: Exception) { "Errore: ${e.message}" }
            busy = null
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) confirmImport = uri }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Impostazioni") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } })
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Città per il meteo", fontWeight = FontWeight.SemiBold)
            Text(if (city.isBlank()) "Non impostata" else city, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, label = { Text("Cerca città") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(enabled = query.length >= 2 && !searching, onClick = {
                    scope.launch {
                        searching = true
                        results = try { withContext(Dispatchers.IO) { WeatherApi.search(query.trim()) } }
                        catch (_: Exception) { msg = "Ricerca non riuscita: controlla la connessione."; emptyList() }
                        if (results.isEmpty() && msg == null) msg = "Nessun risultato."
                        searching = false
                    }
                }) { Text("Cerca") }
            }
            results.forEach { p ->
                TextButton(onClick = { vm.setCity(p.name, p.lat, p.lon); results = emptyList(); query = ""; msg = "Città impostata: ${p.name}" }) {
                    Text("${p.name}, ${p.country}")
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Stilista AI", fontWeight = FontWeight.SemiBold)
            Text("Analisi automatica delle foto, chat con lo stilista e parere sugli acquisti. Le foto e l'elenco dei capi vengono inviati al tuo server, che li passa a Claude; non vengono salvati.",
                style = MaterialTheme.typography.bodySmall)
            val aiOn by vm.aiEnabled.collectAsState()
            val aiUrl by vm.aiUrl.collectAsState()
            var urlText by remember(aiUrl) { mutableStateOf(aiUrl) }
            var aiStatus by remember { mutableStateOf<String?>(null) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Usa l'AI", Modifier.weight(1f))
                androidx.compose.material3.Switch(aiOn, { vm.setAi(it, urlText) })
            }
            OutlinedTextField(urlText, { urlText = it }, label = { Text("Indirizzo del server AI") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.setAi(aiOn, urlText) }) { Text("Salva") }
                Button(onClick = {
                    vm.setAi(aiOn, urlText)
                    scope.launch { aiStatus = "Controllo…"; aiStatus = try { vm.ai.health() } catch (e: com.mycloset.app.logic.AiException) { e.message } }
                }) { Text("Verifica") }
            }
            aiStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            Spacer(Modifier.height(8.dp))
            Text("Valuta", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("€", "$", "£", "CHF ").forEach { c -> Chip(c.trim(), currency == c) { vm.setCurrency(c) } }
            }

            Spacer(Modifier.height(8.dp))
            Text("Backup e ripristino", fontWeight = FontWeight.SemiBold)
            Text("Salva tutto (capi, foto, outfit, calendario, viaggi) in un file .zip: puoi conservarlo dove vuoi e ripristinarlo su un altro telefono. Nessun limite al numero di capi.",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { export.launch("MyCloset-backup-${LocalDate.now()}.zip") }, Modifier.weight(1f)) { Text("Esporta") }
                OutlinedButton(onClick = { import.launch(arrayOf("application/zip", "application/octet-stream")) }, Modifier.weight(1f)) { Text("Ripristina") }
            }
            if (busy != null) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(busy!!)
            }
            msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            Spacer(Modifier.height(16.dp))
            Text("MyCloset 1.1", fontWeight = FontWeight.SemiBold)
            Text("I tuoi dati restano sul telefono. Le foto vengono elaborate sul dispositivo; la rete serve solo per il meteo (Open-Meteo) e per scaricare, al primo uso, il modello che rimuove lo sfondo.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    confirmImport?.let { uri ->
        ConfirmDialog("Ripristinare il backup?", "Tutti i dati attuali verranno sostituiti con quelli del backup.", "Ripristina",
            onDismiss = { confirmImport = null }) {
            scope.launch {
                busy = "Ripristino…"
                msg = try { Backup.import(ctx, vm.dao, uri); "Backup ripristinato." } catch (e: Exception) { "Errore: ${e.message}" }
                busy = null
            }
        }
    }
}
