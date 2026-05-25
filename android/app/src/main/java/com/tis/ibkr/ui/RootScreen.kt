package com.tis.ibkr.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tis.ibkr.ui.nav.Tab
import com.tis.ibkr.ui.screens.FullscreenChartScreen
import com.tis.ibkr.ui.screens.MarketScreen
import com.tis.ibkr.ui.screens.OptionChainScreen
import com.tis.ibkr.ui.screens.OrderFormScreen
import com.tis.ibkr.ui.screens.PositionsScreen
import com.tis.ibkr.ui.screens.SearchScreen
import com.tis.ibkr.ui.screens.SettingsScreen
import com.tis.ibkr.ui.screens.StockDetailScreen
import com.tis.ibkr.ui.screens.WatchlistScreen
import com.tis.ibkr.ui.theme.LbColors

@Composable
fun RootScreen() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in Tab.entries.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = LbColors.Surface,
                    contentColor = LbColors.OnSurface,
                ) {
                    Tab.entries.forEach { tab ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = LbColors.OnSurface,
                                unselectedIconColor = LbColors.OnSurfaceMuted,
                                selectedTextColor = LbColors.OnSurface,
                                unselectedTextColor = LbColors.OnSurfaceMuted,
                                indicatorColor = LbColors.SurfaceElevated,
                            ),
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Tab.Positions.route,
            modifier = Modifier.padding(inner),
        ) {
            composable(Tab.Watchlist.route) {
                WatchlistScreen(
                    onSearch = { nav.navigate("search") },
                    onOpenSymbol = { sym, ex, cur ->
                        nav.navigate("stock/$sym?exchange=$ex&currency=$cur")
                    },
                )
            }
            composable(Tab.Market.route) {
                MarketScreen(onOpenSymbol = { sym, ex, cur ->
                    nav.navigate("stock/$sym?exchange=$ex&currency=$cur")
                })
            }
            composable("search") {
                SearchScreen(
                    onBack = { nav.popBackStack() },
                    onOpenSymbol = { sym, ex, cur ->
                        nav.navigate("stock/$sym?exchange=$ex&currency=$cur")
                    },
                )
            }
            composable(Tab.Positions.route) {
                PositionsScreen(
                    onPositionClick = { p ->
                        nav.navigate(
                            "stock/${p.symbol}?exchange=${p.exchange.ifBlank { "SMART" }}&currency=${p.currency}",
                        )
                    },
                    onOrderClick = { p, side ->
                        val ex = p.exchange.ifBlank { "SMART" }
                        nav.navigate("order/${p.symbol}/$side?exchange=$ex&currency=${p.currency}")
                    },
                )
            }
            composable(Tab.Settings.route) { SettingsScreen() }
            composable(
                route = "stock/{symbol}?exchange={exchange}&currency={currency}",
                arguments = listOf(
                    navArgument("symbol") { type = NavType.StringType },
                    navArgument("exchange") { type = NavType.StringType; defaultValue = "SMART" },
                    navArgument("currency") { type = NavType.StringType; defaultValue = "USD" },
                ),
            ) { entry ->
                val sym = entry.arguments?.getString("symbol") ?: ""
                val ex = entry.arguments?.getString("exchange") ?: "SMART"
                val cur = entry.arguments?.getString("currency") ?: "USD"
                StockDetailScreen(
                    symbol = sym,
                    exchange = ex,
                    currency = cur,
                    onBack = { nav.popBackStack() },
                    onOrder = { side -> nav.navigate("order/$sym/$side?exchange=$ex&currency=$cur") },
                    onOptions = { nav.navigate("options/$sym") },
                    onFullscreen = { nav.navigate("fullscreen/$sym?exchange=$ex&currency=$cur") },
                )
            }
            composable(
                route = "fullscreen/{symbol}?exchange={exchange}&currency={currency}",
                arguments = listOf(
                    navArgument("symbol") { type = NavType.StringType },
                    navArgument("exchange") { type = NavType.StringType; defaultValue = "SMART" },
                    navArgument("currency") { type = NavType.StringType; defaultValue = "USD" },
                ),
            ) { entry ->
                FullscreenChartScreen(
                    symbol = entry.arguments?.getString("symbol") ?: "",
                    exchange = entry.arguments?.getString("exchange") ?: "SMART",
                    currency = entry.arguments?.getString("currency") ?: "USD",
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                route = "options/{symbol}",
                arguments = listOf(navArgument("symbol") { type = NavType.StringType }),
            ) { entry ->
                val sym = entry.arguments?.getString("symbol") ?: ""
                OptionChainScreen(
                    symbol = sym,
                    onBack = { nav.popBackStack() },
                    onSelectContract = { c ->
                        // Default to BUY; user can flip in the form.
                        nav.navigate(
                            "order/$sym/BUY?exchange=SMART&currency=USD" +
                                "&secType=OPT&expiry=${c.expiry}&strike=${c.strike}&right=${c.right}",
                        )
                    },
                )
            }
            composable(
                route = "order/{symbol}/{side}?exchange={exchange}&currency={currency}&secType={secType}&expiry={expiry}&strike={strike}&right={right}",
                arguments = listOf(
                    navArgument("symbol") { type = NavType.StringType },
                    navArgument("side") { type = NavType.StringType },
                    navArgument("exchange") { type = NavType.StringType; defaultValue = "SMART" },
                    navArgument("currency") { type = NavType.StringType; defaultValue = "USD" },
                    navArgument("secType") { type = NavType.StringType; defaultValue = "STK" },
                    navArgument("expiry") { type = NavType.StringType; defaultValue = "" },
                    navArgument("strike") { type = NavType.StringType; defaultValue = "" },
                    navArgument("right") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                OrderFormScreen(
                    symbol = entry.arguments?.getString("symbol") ?: "",
                    side = entry.arguments?.getString("side") ?: "BUY",
                    exchange = entry.arguments?.getString("exchange") ?: "SMART",
                    currency = entry.arguments?.getString("currency") ?: "USD",
                    secType = entry.arguments?.getString("secType") ?: "STK",
                    expiry = entry.arguments?.getString("expiry")?.ifBlank { null },
                    strike = entry.arguments?.getString("strike")?.toDoubleOrNull(),
                    right = entry.arguments?.getString("right")?.ifBlank { null },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
