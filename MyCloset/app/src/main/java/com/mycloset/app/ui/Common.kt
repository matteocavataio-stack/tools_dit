package com.mycloset.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mycloset.app.data.Category
import com.mycloset.app.data.Item
import com.mycloset.app.data.Outfit
import com.mycloset.app.logic.Colors
import com.mycloset.app.logic.ImageProc
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

val Paper = Color(0xFFF7F4EF) // sfondo "foto" dei capi (uguale alla tela degli outfit)

@Composable
fun ItemImage(item: Item, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Fit) {
    val ctx = LocalContext.current
    AsyncImage(
        model = ImageProc.itemFile(ctx, item.imagePath),
        contentDescription = item.name,
        modifier = modifier, contentScale = scale,
    )
}

@Composable
fun OutfitImage(outfit: Outfit, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    AsyncImage(
        model = ImageProc.outfitFile(ctx, outfit.imagePath),
        contentDescription = outfit.name,
        modifier = modifier, contentScale = scale,
    )
}

@Composable
fun ColorDot(hex: String, size: Int = 18, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size.dp).clip(CircleShape).background(Color(Colors.parse(hex)))
            .border(1.dp, Color(0x33000000), CircleShape)
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
fun ItemTile(item: Item, selected: Boolean = false, badge: String? = null, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.85f)) {
            ItemImage(item, Modifier.fillMaxSize().padding(8.dp))
            if (item.favorite) Icon(Icons.Filled.Favorite, null, tint = Color(0xFFE11D48),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp))
            if (selected) Icon(Icons.Filled.Check, null, tint = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape).padding(3.dp))
            if (badge != null) Text(badge, color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

/** Selettore di capi a schermo intero (singolo o multiplo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemPickerDialog(
    items: List<Item>,
    title: String = "Scegli i capi",
    initial: Set<Long> = emptySet(),
    multi: Boolean = true,
    onDismiss: () -> Unit,
    onDone: (List<Item>) -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    var cat by remember { mutableStateOf<Category?>(null) }
    var query by remember { mutableStateOf("") }
    val shown = items.filter {
        (cat == null || it.category == cat!!.name) &&
            (query.isBlank() || it.name.contains(query, true) || it.brand.contains(query, true))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Chiudi") }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Button(onClick = { onDone(items.filter { it.id in selected }) }, enabled = selected.isNotEmpty()) {
                        Text(if (multi) "Fatto (${selected.size})" else "Scegli")
                    }
                }
                OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Cerca") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                ChipRow {
                    Chip("Tutti", cat == null) { cat = null }
                    Category.entries.forEach { c -> Chip(c.label, cat == c) { cat = if (cat == c) null else c } }
                }
                Spacer(Modifier.height(8.dp))
                if (shown.isEmpty()) EmptyState("Nessun capo", "Aggiungi dei capi al tuo armadio per poterli scegliere.")
                LazyVerticalGrid(GridCells.Adaptive(104.dp), contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.id }) { it2 ->
                        ItemTile(it2, selected = it2.id in selected) {
                            selected = if (!multi) setOf(it2.id)
                            else if (it2.id in selected) selected - it2.id else selected + it2.id
                        }
                    }
                }
            }
        }
    }
}

/** Selettore di data (Material3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDlg(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    ) { DatePicker(state) }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Elimina", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

fun money(v: Double, cur: String): String =
    if (v == v.toLong().toDouble()) "$cur${v.toLong()}" else "$cur" + String.format(java.util.Locale.ITALY, "%.2f", v)
