package com.manhwaread.app.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.manhwaread.feature.browse.BrowseRoute
import com.manhwaread.feature.details.DetailsRoute
import com.manhwaread.feature.downloads.DownloadsRoute
import com.manhwaread.feature.history.HistoryRoute
import com.manhwaread.feature.library.LibraryRoute
import com.manhwaread.feature.onboarding.OnboardingRoute
import com.manhwaread.feature.reader.ReaderScreen
import com.manhwaread.feature.settings.SettingsRoute

// Маршруты карточки тайтла: по id в БД (библиотека/история) и по координатам
// источника (каталог). mangaUrl передаётся query-параметром после Uri.encode —
// слеши и спецсимволы пути не ломают сопоставление маршрута, а NavController
// возвращает значение уже декодированным.
private const val DETAILS_BY_ID_ROUTE = "details/id/{mangaId}"
private const val DETAILS_BY_SOURCE_ROUTE = "details/source/{sourceId}?mangaUrl={mangaUrl}"
private const val ARG_MANGA_ID = "mangaId"
private const val ARG_SOURCE_ID = "sourceId"
private const val ARG_MANGA_URL = "mangaUrl"

// Маршрут офлайн-читалки (ФАЗА 15): каталог главы resolves по chapterId.
private const val READER_ROUTE = "reader/{mangaId}/{chapterId}"
private const val ARG_CHAPTER_ID = "chapterId"

// Префикс маршрута читалки: на нём нижняя навигация скрыта (полный экран).
private const val READER_ROUTE_PREFIX = "reader"

// Корень приложения: гейт онбординга + Scaffold с нижней навигацией.
@Composable
fun ManhwareadRoot(viewModel: RootViewModel = hiltViewModel()) {
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
    when (onboardingCompleted) {
        // Флаг ещё читается из DataStore: не показываем ни онбординг, ни разделы.
        null -> Box(modifier = Modifier.fillMaxSize())
        false -> OnboardingRoute()
        true -> MainScaffold()
    }
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Читалка — полноэкранная: на её маршруте нижняя навигация не показывается,
    // innerPadding снизу становится нулевым и контент занимает весь экран.
    val isReaderRoute = currentRoute?.startsWith(READER_ROUTE_PREFIX) == true
    Scaffold(
        bottomBar = {
            if (!isReaderRoute) {
                ManhwareadBottomBar(
                    currentRoute = currentRoute,
                    onTabSelected = { destination -> navController.navigateToTab(destination) },
                )
            }
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
        composable(route = AppRoute.LIBRARY.route) {
            LibraryRoute(onOpenManga = { mangaId -> navController.navigateToDetailsById(mangaId) })
        }
        composable(route = AppRoute.BROWSE.route) {
            BrowseRoute(
                onOpenManga = { sourceId, mangaUrl ->
                    navController.navigateToDetailsBySource(sourceId, mangaUrl)
                },
            )
        }
        composable(route = AppRoute.HISTORY.route) {
            HistoryRoute(onOpenManga = { mangaId -> navController.navigateToDetailsById(mangaId) })
        }
        composable(route = AppRoute.DOWNLOADS.route) {
            DownloadsRoute()
        }
        composable(route = AppRoute.SETTINGS.route) {
            SettingsRoute()
        }
        composable(
            route = DETAILS_BY_ID_ROUTE,
            arguments = listOf(navArgument(ARG_MANGA_ID) { type = NavType.LongType }),
        ) { entry ->
            val mangaId = entry.arguments?.getLong(ARG_MANGA_ID) ?: 0L
            DetailsRoute(
                mangaId = mangaId,
                sourceId = null,
                mangaUrl = null,
                onBack = { navController.popBackStack() },
                onOpenReader = { readerMangaId, chapterId ->
                    navController.navigateToReader(readerMangaId, chapterId)
                },
            )
        }
        composable(
            route = DETAILS_BY_SOURCE_ROUTE,
            arguments = listOf(
                navArgument(ARG_SOURCE_ID) { type = NavType.LongType },
                navArgument(ARG_MANGA_URL) { type = NavType.StringType },
            ),
        ) { entry ->
            val sourceId = entry.arguments?.getLong(ARG_SOURCE_ID) ?: 0L
            val mangaUrl = entry.arguments?.getString(ARG_MANGA_URL).orEmpty()
            DetailsRoute(
                mangaId = null,
                sourceId = sourceId,
                mangaUrl = mangaUrl,
                onBack = { navController.popBackStack() },
                onOpenReader = { readerMangaId, chapterId ->
                    navController.navigateToReader(readerMangaId, chapterId)
                },
            )
        }
        composable(
            route = READER_ROUTE,
            arguments = listOf(
                navArgument(ARG_MANGA_ID) { type = NavType.LongType },
                navArgument(ARG_CHAPTER_ID) { type = NavType.LongType },
            ),
        ) { entry ->
            val mangaId = entry.arguments?.getLong(ARG_MANGA_ID) ?: 0L
            val chapterId = entry.arguments?.getLong(ARG_CHAPTER_ID) ?: 0L
            ReaderNavScreen(
                mangaId = mangaId,
                chapterId = chapterId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

// Обёртка читалки: офлайн-каталог главы и стартовая страница — из
// ReaderNavViewModel (история чтения), прогресс пишется обратно в историю.
@Composable
private fun ReaderNavScreen(
    mangaId: Long,
    chapterId: Long,
    onBack: () -> Unit,
    viewModel: ReaderNavViewModel = hiltViewModel(),
) {
    LaunchedEffect(mangaId, chapterId) {
        viewModel.open(mangaId, chapterId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReaderScreen(
        chapterDir = state.chapterDir.takeIf { state.isOpen },
        onBack = onBack,
        initialPageIndex = state.initialPageIndex,
        onProgress = { pageIndex -> viewModel.onPageChanged(pageIndex) },
    )
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

// Карточка из библиотеки/истории: координаты источника возьмутся из строки БД.
private fun NavHostController.navigateToDetailsById(mangaId: Long) {
    navigate("details/id/$mangaId")
}

// Карточка из каталога: строки в БД ещё нет, передаём sourceId + url.
private fun NavHostController.navigateToDetailsBySource(sourceId: Long, mangaUrl: String) {
    navigate("details/source/$sourceId?mangaUrl=${Uri.encode(mangaUrl)}")
}

// Офлайн-читалка скачанной главы (ФАЗА 15).
private fun NavHostController.navigateToReader(mangaId: Long, chapterId: Long) {
    navigate("reader/$mangaId/$chapterId")
}
