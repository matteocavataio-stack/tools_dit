package com.mycloset.app.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mycloset.app.AppViewModel
import com.mycloset.app.ChatMsg
import com.mycloset.app.data.Category
import com.mycloset.app.data.Item
import com.mycloset.app.data.Outfit
import com.mycloset.app.data.OutfitItem
import com.mycloset.app.logic.AiOutfit
import java.time.LocalDate

/** Outfit proposto dall'AI: capi, motivazione e azioni (indossa oggi / salva). */
@Composable
fun AiOutfitCard(vm: AppViewModel, outfit: AiOutfit, items: List<Item>, onOpenItem: (Long) -> Unit) {
    val parts = outfit.itemIds.mapNotNull { id -> items.firstOrNull { it.id == id } }
    if (parts.size < 2) return
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.primaryContainer).padding(12.dp)) {
        if (outfit.title.isNotBlank()) Text(outfit.title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            parts.forEach { it2 ->
                Box(Modifier.width(76.dp).aspectRatio(0.85f).clip(RoundedCornerShape(10.dp)).background(Paper)
                    .clickable { onOpenItem(it2.id) }) { ItemImage(it2, Modifier.padding(4.dp)) }
            }
        }
        if (outfit.why.isNotBlank()) Text(outfit.why, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.wear(LocalDate.now(), parts.map { it.id }) }) { Text("Lo indosso") }
            OutlinedButton(enabled = !saved, onClick = {
                val placements = parts.mapIndexed { i, p ->
                    val (x, y, s) = defaultSlot(Category.of(p.category)); p to OutfitItem(0, p.id, x, y, s, 0f, i)
                }
                vm.saveOutfit(Outfit(name = outfit.title), placements) { saved = true }
            }) { Text(if (saved) "Salvato ✓" else "Salva") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StylistScreen(vm: AppViewModel, onBack: () -> Unit, onOpenItem: (Long) -> Unit, onSettings: () -> Unit) {
    val msgs by vm.chat.collectAsState()
    val busy by vm.chatBusy.collectAsState()
    val items by vm.items.collectAsState()
    val aiOn by vm.aiEnabled.collectAsState()
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size, busy) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size) }

    fun send(t: String) { vm.sendChat(t); input = "" }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Stilista AI") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            actions = { if (msgs.isNotEmpty()) TextButton(onClick = { vm.clearChat() }) { Text("Nuova chat") } })
    }, bottomBar = {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Chiedi allo stilista…") },
                maxLines = 3, enabled = aiOn)
            IconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !busy && aiOn) {
                Icon(Icons.AutoMirrored.Filled.Send, "Invia")
            }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!aiOn) Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("L'AI è disattivata.", Modifier.weight(1f)); TextButton(onClick = onSettings) { Text("Impostazioni") }
            }
            LazyColumn(Modifier.weight(1f), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("Ciao! Conosco il tuo armadio (${items.count { it.status == "OWNED" }} capi) e il meteo di oggi. Chiedimi cosa mettere, come abbinare un capo o cosa riscoprire.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (msgs.isEmpty()) Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Cosa mi metto oggi?", "Un outfit elegante per una cena", "Cosa non indosso da tempo e posso rimettere?",
                            "Cosa mi manca nell'armadio?").forEach { q ->
                            OutlinedButton(onClick = { send(q) }, enabled = aiOn && !busy, modifier = Modifier.fillMaxWidth()) { Text(q) }
                        }
                    }
                }
                items(msgs.size) { i ->
                    val m: ChatMsg = msgs[i]
                    val mine = m.role == "user"
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        Column(Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp))
                            .background(when { mine -> MaterialTheme.colorScheme.primary; m.error -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant }).padding(12.dp)) {
                            Text(m.text, color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            if (m.error) TextButton(onClick = onSettings) { Text("Impostazioni AI") }
                            m.outfits.forEach { AiOutfitCard(vm, it, items, onOpenItem) }
                        }
                    }
                }
                if (busy) item { Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Sto pensando…")
                } }
            }
        }
    }
}
