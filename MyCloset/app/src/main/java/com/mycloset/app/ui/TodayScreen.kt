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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mycloset.app.AppViewModel
import com.mycloset.app.data.Category
import com.mycloset.app.data.Occasion
import com.mycloset.app.data.Outfit
import com.mycloset.app.data.OutfitItem
import com.mycloset.app.logic.Stylist
import com.mycloset.app.logic.Weather
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    vm: AppViewModel,
    onOpenItem: (Long) -> Unit,
    onSettings: () -> Unit, onStylist: () -> Unit, onMatch: () -> Unit, onPurchase: () -> Unit, onTrips: () -> Unit, onWishlist: () -> Unit,
) {
    val items by vm.items.collectAsState()
    val logs by vm.wearLogs.collectAsState()
    val weather by vm.weather.collectAsState()
    val loading by vm.weatherLoading.collectAsState()
    val today = LocalDate.now()
    var seed by remember { mutableStateOf(1L) }
    var occasion by remember { mutableStateOf<Occasion?>(null) }
    var saved by remember { mutableStateOf(false) }

    val owned = items.filter { it.status == "OWNED" }
    val recent = remember(logs) { logs.filter { it.date >= today.toEpochDay() - 3 && it.date <= today.toEpochDay() }.map { it.itemId }.toSet() }
    val suggestion = remember(items, weather, seed, occasion, recent) {
        Stylist.generate(owned, weather?.feelsLike, weather?.rainy ?: false, occasion, recent, seed)
    }
    val wornToday = logs.filter { it.date == today.toEpochDay() }.mapNotNull { l -> items.firstOrNull { it.id == l.itemId } }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ciao 👋", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() } + " " +
                        today.dayOfMonth + " " + today.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN),
                        style = MaterialTheme.typography.headlineSmall)
                }
                androidx.compose.material3.IconButton(onClick = onSettings) {
                    androidx.compose.material3.Icon(AppIcons.Settings, "Impostazioni")
                }
            }
            Spacer(Modifier.height(14.dp))
            WeatherHero(weather, loading, vm.city.collectAsState().value, onSettings, onRefresh = { vm.refreshWeather() })

            // ---- Outfit del giorno ----
            SectionTitle("Cosa mi metto oggi?")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Qualsiasi", occasion == null) { occasion = null }
                Occasion.entries.forEach { o -> Chip(o.label, occasion == o) { occasion = if (occasion == o) null else o } }
            }
            Spacer(Modifier.height(12.dp))
            if (suggestion == null) {
                EmptyState("Servono più capi",
                    if (owned.isEmpty()) "Aggiungi qualche capo all'armadio e ti propongo degli abbinamenti."
                    else "Per un abbinamento servono almeno un top e un pantalone/gonna (o un abito)${if (occasion != null) " adatti a questa occasione" else ""}.")
            } else {
                SoftCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            suggestion.items.forEach { it2 ->
                                Box(Modifier.width(104.dp).aspectRatio(0.88f).clip(RoundedCornerShape(20.dp)).background(Paper)
                                    .clickable { onOpenItem(it2.id) }) { ItemImage(it2, Modifier.padding(8.dp)) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (suggestion.note.isNotBlank()) Text(suggestion.note + ".", style = MaterialTheme.typography.bodyMedium)
                        Text("Armonia dei colori: ${suggestion.score}/100", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { vm.wear(today, suggestion.items.map { it.id }) }, Modifier.weight(1f).height(48.dp)) { Text("Lo indosso") }
                            OutlinedButton(onClick = { seed++; saved = false }, Modifier.weight(1f).height(48.dp)) { Text("Altro") }
                        }
                        TextButton(onClick = {
                            val placements = suggestion.items.mapIndexed { i, it2 ->
                                val (x, y, s) = defaultSlot(Category.of(it2.category))
                                it2 to OutfitItem(0, it2.id, x, y, s, 0f, i)
                            }
                            vm.saveOutfit(Outfit(name = "Suggerimento ${today.dayOfMonth}/${today.monthValue}", occasion = occasion?.name ?: ""), placements) { saved = true }
                        }, enabled = !saved) { Text(if (saved) "Salvato negli outfit ✓" else "Salva come outfit") }
                    }
                }
            }
            if (wornToday.isNotEmpty()) {
                SectionTitle("Indossato oggi")
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    wornToday.forEach { it2 ->
                        Box(Modifier.width(76.dp).aspectRatio(0.88f).clip(RoundedCornerShape(16.dp)).background(Paper)
                            .clickable { onOpenItem(it2.id) }) { ItemImage(it2, Modifier.padding(6.dp)) }
                    }
                }
            }

            // ---- Stilista e strumenti ----
            SectionTitle("Il tuo stilista")
            Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
                .clickable(onClick = onStylist).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(AppIcons.Sparkle, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("✨ Chiedi allo stilista AI", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text("Conosce il tuo armadio e il meteo: ti propone outfit su misura.", style = MaterialTheme.typography.bodySmall, color = Color(0xE6FFFFFF))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolTile(AppIcons.Shirt, "Questo abbinamento va bene?", Modifier.weight(1f), onMatch)
                    ToolTile(AppIcons.Bag, "Devo comprarlo?", Modifier.weight(1f), onPurchase)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolTile(AppIcons.Suitcase, "Valigia per i viaggi", Modifier.weight(1f), onTrips)
                    ToolTile(AppIcons.Hanger, "Lista desideri", Modifier.weight(1f), onWishlist)
                }
            }
        }
    }
}

@Composable
private fun ToolTile(icon: ImageVector, title: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(112.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
        .clickable(onClick = onClick).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
    }
}

@Composable
private fun WeatherHero(w: Weather?, loading: Boolean, city: String, onSettings: () -> Unit, onRefresh: () -> Unit) {
    val white = Color.White
    Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF7C5CFA), Color(0xFFB86BFF), Color(0xFFFF7FA3)))).padding(20.dp)) {
        Column {
            when {
                city.isBlank() -> {
                    Text("Meteo", style = MaterialTheme.typography.titleMedium, color = white)
                    Text("Imposta la tua città per outfit adatti al tempo.", color = Color(0xE6FFFFFF))
                    TextButton(onClick = onSettings) { Text("Imposta città", color = white) }
                }
                w == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    if (loading) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = white); Spacer(Modifier.width(12.dp)) }
                    Text(if (loading) "Carico il meteo per $city…" else "Meteo non disponibile (serve internet almeno una volta).", color = white)
                    if (!loading) TextButton(onClick = onRefresh) { Text("Riprova", color = white) }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(w.city, style = MaterialTheme.typography.labelLarge, color = Color(0xE6FFFFFF))
                            Text("${w.temp.toInt()}°", style = MaterialTheme.typography.displaySmall, color = white)
                            Text("${Weather.describe(w.code)} · percepita ${w.feelsLike.toInt()}°", style = MaterialTheme.typography.bodyMedium, color = white)
                            w.days.firstOrNull()?.let { Text("Min ${it.min.toInt()}° · Max ${it.max.toInt()}° · Pioggia ${it.rainPct}%", style = MaterialTheme.typography.bodySmall, color = Color(0xE6FFFFFF)) }
                            if (w.offline) Text("Dati salvati (offline)", style = MaterialTheme.typography.labelSmall, color = Color(0xCCFFFFFF))
                        }
                        TextButton(onClick = onRefresh) { Text("Aggiorna", color = white) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        w.days.drop(1).forEach { d ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN), style = MaterialTheme.typography.labelSmall, color = Color(0xE6FFFFFF))
                                Text("${d.max.toInt()}°", style = MaterialTheme.typography.titleSmall, color = white)
                                Text("${d.min.toInt()}°", style = MaterialTheme.typography.labelSmall, color = Color(0xB3FFFFFF))
                            }
                        }
                    }
                }
            }
        }
    }
}
