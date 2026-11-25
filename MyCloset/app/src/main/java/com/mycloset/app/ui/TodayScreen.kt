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
    val currency by vm.currency.collectAsState()
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

    Scaffold(topBar = {
        TopAppBar(title = { Text("MyCloset") }, actions = { TextButton(onClick = onSettings) { Text("Impostazioni") } })
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() } + " " +
                today.dayOfMonth + " " + today.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN),
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            // ---- Meteo ----
            WeatherCard(weather, loading, vm.city.collectAsState().value, onSettings, onRefresh = { vm.refreshWeather() })

            // ---- Outfit del giorno ----
            SectionTitle("Cosa mi metto oggi?")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Qualsiasi", occasion == null) { occasion = null }
                Occasion.entries.forEach { o -> Chip(o.label, occasion == o) { occasion = if (occasion == o) null else o } }
            }
            Spacer(Modifier.height(8.dp))
            if (suggestion == null) {
                EmptyState("Servono più capi",
                    if (owned.isEmpty()) "Aggiungi qualche capo all'armadio e ti propongo degli abbinamenti."
                    else "Per un abbinamento servono almeno un top e un pantalone/gonna (o un abito)${if (occasion != null) " adatti a questa occasione" else ""}.")
            } else {
                Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            suggestion.items.forEach { it2 ->
                                Box(Modifier.width(92.dp).aspectRatio(0.85f).clip(RoundedCornerShape(12.dp)).background(Paper)
                                    .clickable { onOpenItem(it2.id) }) { ItemImage(it2, Modifier.padding(4.dp)) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (suggestion.note.isNotBlank()) Text(suggestion.note + ".", style = MaterialTheme.typography.bodyMedium)
                        Text("Armonia colori: ${suggestion.score}/100", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.wear(today, suggestion.items.map { it.id }) }, Modifier.weight(1f)) { Text("Lo indosso") }
                            OutlinedButton(onClick = { seed++; saved = false }, Modifier.weight(1f)) { Text("Altro") }
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
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    wornToday.forEach { it2 ->
                        Box(Modifier.width(72.dp).aspectRatio(0.85f).clip(RoundedCornerShape(10.dp)).background(Paper)
                            .clickable { onOpenItem(it2.id) }) { ItemImage(it2, Modifier.padding(4.dp)) }
                    }
                }
            }

            // ---- Strumenti dello stilista ----
            SectionTitle("Il tuo stilista")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolCard("✨ Chiedi allo stilista AI", "Chatta con lo stilista: conosce il tuo armadio e il meteo e ti propone outfit su misura.", onStylist)
                ToolCard("Questo abbinamento va bene?", "Scegli 2 o più capi e ti dico se i colori e lo stile funzionano.", onMatch)
                ToolCard("Devo comprarlo?", "Fotografa un capo che vuoi comprare: confronto con ciò che hai già e vedo cosa ci abbineresti.", onPurchase)
                ToolCard("Valigia per i viaggi", "Prepara la lista dei capi da portare e spunta quelli già in valigia.", onTrips)
                ToolCard("Lista desideri", "I capi che vorresti comprare, in un posto solo.", onWishlist)
            }
        }
    }
}

@Composable
private fun ToolCard(title: String, body: String, onClick: () -> Unit) {
    Card(onClick = onClick, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeatherCard(w: Weather?, loading: Boolean, city: String, onSettings: () -> Unit, onRefresh: () -> Unit) {
    Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            when {
                city.isBlank() -> {
                    Text("Meteo", fontWeight = FontWeight.SemiBold)
                    Text("Imposta la tua città per avere outfit adatti al tempo.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onSettings) { Text("Imposta città") }
                }
                w == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    if (loading) { CircularProgressIndicator(Modifier.width(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)) }
                    Text(if (loading) "Carico il meteo per $city…" else "Meteo non disponibile (serve internet almeno una volta).")
                    if (!loading) TextButton(onClick = onRefresh) { Text("Riprova") }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(w.city, style = MaterialTheme.typography.labelLarge)
                            Text("${w.temp.toInt()}°", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                            Text("${Weather.describe(w.code)} · percepita ${w.feelsLike.toInt()}°" +
                                (w.days.firstOrNull()?.let { " · min ${it.min.toInt()}° max ${it.max.toInt()}° · pioggia ${it.rainPct}%" } ?: ""),
                                style = MaterialTheme.typography.bodySmall)
                            if (w.offline) Text("Dati salvati (offline)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        TextButton(onClick = onRefresh) { Text("Aggiorna") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        w.days.drop(1).forEach { d ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN), style = MaterialTheme.typography.labelSmall)
                                Text("${d.max.toInt()}°", fontWeight = FontWeight.SemiBold)
                                Text("${d.min.toInt()}°", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
