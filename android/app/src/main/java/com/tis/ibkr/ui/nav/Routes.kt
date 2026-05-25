package com.tis.ibkr.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Watchlist("watchlist", "自选", Icons.Outlined.StarBorder),
    Market("market", "市场", Icons.AutoMirrored.Outlined.ShowChart),
    Positions("positions", "持仓", Icons.Outlined.PieChart),
    Settings("settings", "我的", Icons.Outlined.Settings);
}
