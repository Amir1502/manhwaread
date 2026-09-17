package com.manhwaread.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.manhwaread.feature.settings.SettingsRoute

private val PlaceholderIconSize = 48.dp

// Корень приложения: Scaffold с нижней навигацией по разделам.
@Composable
fun ManhwareadRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    Scaffold(
        bottomBar = {
            ManhwareadBottomBar(
                currentRoute = currentRoute,
                onTabSelected = { destination -> navController.navigateToTab(destination) },
            )
        },
    ) { innerPadding ->
        ManhwareadNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun ManhwareadBottomBar(
    currentRoute: String?,
    onTabSelected: (AppRoute) -> Unit,
) {
    NavigationBar {
        AppRoute.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onTabSelected(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

@Composable
fun ManhwareadNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startRoute.route,
        modifier = modifier,
    ) {
        AppRoute.entries.forEach { destination ->
            composable(route = destination.route) {
                if (destination == AppRoute.SETTINGS) {
                    // ФАЗА 12: экран настроек провайдера перевода — рабочий раздел.
                    SettingsRoute()
                } else {
                    SectionPlaceholderScreen(destination)
                }
            }
        }
    }
}

// Переключение таба по стандартному паттерну Material: singleTop, сохранение
// состояния стека и прокрутки при уходе/возврате.
private fun NavHostController.navigateToTab(destination: AppRoute) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

// Экран раздела до ФАЗЫ 14: иконка и название (рабочий контент, не заглушка).
@Composable
private fun SectionPlaceholderScreen(destination: AppRoute) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            modifier = Modifier.size(PlaceholderIconSize),
        )
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
