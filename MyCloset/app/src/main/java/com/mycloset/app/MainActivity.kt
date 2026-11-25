package com.mycloset.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mycloset.app.ui.AppIcons
import com.mycloset.app.ui.CalendarScreen
import com.mycloset.app.ui.ClosetScreen
import com.mycloset.app.ui.ItemDetailScreen
import com.mycloset.app.ui.ItemEditScreen
import com.mycloset.app.ui.MatchScreen
import com.mycloset.app.ui.MyClosetTheme
import com.mycloset.app.ui.OutfitDetailScreen
import com.mycloset.app.ui.OutfitEditorScreen
import com.mycloset.app.ui.OutfitsScreen
import com.mycloset.app.ui.PurchaseScreen
import com.mycloset.app.ui.SettingsScreen
import com.mycloset.app.ui.StatsScreen
import com.mycloset.app.ui.StylistScreen
import com.mycloset.app.ui.TodayScreen
import com.mycloset.app.ui.TripDetailScreen
import com.mycloset.app.ui.TripsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "Oggi", AppIcons.Today),
    Tab("closet", "Armadio", AppIcons.Hanger),
    Tab("outfits", "Outfit", AppIcons.Shirt),
    Tab("calendar", "Calendario", AppIcons.Calendar),
    Tab("stats", "Statistiche", AppIcons.Chart),
)

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyClosetTheme { App(vm) } }
    }
}

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun App(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val longArg = { name: String -> listOf(navArgument(name) { type = NavType.LongType }) }

    Scaffold(bottomBar = {
        if (route in tabs.map { it.route }) NavigationBar(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            tabs.forEach { t ->
                NavigationBarItem(selected = route == t.route, onClick = { nav.goTab(t.route) },
                    icon = { Icon(t.icon, t.label) }, label = { Text(t.label, maxLines = 1, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
                    colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                        indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary))
            }
        }
    }) { pad ->
        NavHost(nav, startDestination = "today", modifier = androidx.compose.ui.Modifier.padding(pad)) {
            composable("today") {
                TodayScreen(vm,
                    onOpenItem = { nav.navigate("item/$it") },
                    onSettings = { nav.navigate("settings") },
                    onStylist = { nav.navigate("stylist") },
                    onMatch = { nav.navigate("match") },
                    onPurchase = { nav.navigate("purchase") },
                    onTrips = { nav.navigate("trips") },
                    onWishlist = { vm.filter.value = ClosetFilter(wishlist = true); nav.goTab("closet") })
            }
            composable("closet") {
                ClosetScreen(vm, onOpen = { nav.navigate("item/$it") }, onAdd = { wish -> nav.navigate("itemedit/0/$wish") })
            }
            composable("outfits") {
                OutfitsScreen(vm, onOpen = { nav.navigate("outfit/$it") }, onCreate = { nav.navigate("outfitedit/0") })
            }
            composable("calendar") {
                CalendarScreen(vm, onOpenOutfit = { nav.navigate("outfit/$it") }, onOpenItem = { nav.navigate("item/$it") })
            }
            composable("stats") {
                StatsScreen(vm, onOpenItem = { nav.navigate("item/$it") }, onOpenCloset = { nav.goTab("closet") })
            }
            composable("item/{id}", longArg("id")) { e ->
                val id = e.arguments!!.getLong("id")
                ItemDetailScreen(vm, id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("itemedit/$id/false") },
                    onOpenOutfit = { nav.navigate("outfit/$it") })
            }
            composable("itemedit/{id}/{wish}", longArg("id") + navArgument("wish") { type = NavType.BoolType }) { e ->
                ItemEditScreen(vm, e.arguments!!.getLong("id"), e.arguments!!.getBoolean("wish"), onClose = { nav.popBackStack() })
            }
            composable("outfit/{id}", longArg("id")) { e ->
                val id = e.arguments!!.getLong("id")
                OutfitDetailScreen(vm, id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("outfitedit/$id") },
                    onOpenItem = { nav.navigate("item/$it") })
            }
            composable("outfitedit/{id}", longArg("id")) { e ->
                val id = e.arguments!!.getLong("id")
                OutfitEditorScreen(vm, id, onClose = { saved ->
                    nav.popBackStack()
                    if (saved != null && id == 0L) nav.navigate("outfit/$saved")
                })
            }
            composable("trips") { TripsScreen(vm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("trip/$it") }) }
            composable("trip/{id}", longArg("id")) { e -> TripDetailScreen(vm, e.arguments!!.getLong("id"), onBack = { nav.popBackStack() }) }
            composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
            composable("stylist") {
                StylistScreen(vm, onBack = { nav.popBackStack() }, onOpenItem = { nav.navigate("item/$it") }, onSettings = { nav.navigate("settings") })
            }
            composable("match") { MatchScreen(vm, onBack = { nav.popBackStack() }) }
            composable("purchase") { PurchaseScreen(vm, onBack = { nav.popBackStack() }, onOpenItem = { nav.navigate("item/$it") }) }
        }
    }
}
