package com.s2s.sigma.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.s2s.sigma.ui.theme.Cyan
import com.s2s.sigma.ui.theme.DeepNavy
import com.s2s.sigma.ui.theme.Navy
import com.s2s.sigma.ui.theme.SecondaryText

object Routes {
    const val HOME = "home"
    const val SPORTS = "sports"
    const val ANALYSIS = "analysis"
    const val HISTORY = "history"
    const val PROFILE = "profile"
    const val IMPORT = "import-research"
    const val COMPETITIONS = "competitions/{sportId}"
    const val EVENTS = "events/{competitionId}"
    const val EVENT_DETAIL = "event/{eventId}"
    const val QUESTIONS = "questions/{eventId}"
    const val RESEARCH = "research/{eventId}/{questionId}"
    const val RESULT = "result/{eventId}/{questionId}"

    val mandatoryScreens = listOf(
        HOME, SPORTS, COMPETITIONS, EVENTS, EVENT_DETAIL,
        QUESTIONS, RESEARCH, RESULT, HISTORY, PROFILE,
    )

    fun competitions(sportId: String) = "competitions/$sportId"
    fun events(competitionId: String) = "events/$competitionId"
    fun event(eventId: String) = "event/$eventId"
    fun questions(eventId: String) = "questions/$eventId"
    fun research(eventId: String, questionId: String) = "research/$eventId/$questionId"
    fun result(eventId: String, questionId: String) = "result/$eventId/$questionId"
}

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val bottomItems = listOf(
    BottomItem(Routes.HOME, "Inicio", Icons.Default.Home),
    BottomItem(Routes.SPORTS, "Explorar", Icons.Default.Explore),
    BottomItem(Routes.ANALYSIS, "Análisis", Icons.Default.Analytics),
    BottomItem(Routes.HISTORY, "Historial", Icons.Default.History),
    BottomItem(Routes.PROFILE, "Perfil", Icons.Default.Person),
)

@Composable
fun SigmaApp(sigmaViewModel: SigmaViewModel = viewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomItems.any { it.route == currentRoute }

    Scaffold(
        containerColor = DeepNavy,
        bottomBar = {
            if (showBottomBar) {
                Surface(
                    color = Navy,
                    tonalElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                ) {
                    NavigationBar(containerColor = Navy, tonalElevation = 0.dp) {
                        bottomItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Cyan,
                                    selectedTextColor = Cyan,
                                    unselectedIconColor = SecondaryText,
                                    unselectedTextColor = SecondaryText,
                                    indicatorColor = Cyan.copy(alpha = .13f),
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    vm = sigmaViewModel,
                    onExplore = { navController.navigate(Routes.SPORTS) },
                    onEvent = { navController.navigate(Routes.event(it)) },
                    onHistory = { navController.navigate(Routes.HISTORY) },
                    onImport = { navController.navigate(Routes.IMPORT) },
                )
            }
            composable(Routes.SPORTS) {
                SportsScreen(sigmaViewModel) { navController.navigate(Routes.competitions(it)) }
            }
            composable(Routes.COMPETITIONS) { entry ->
                CompetitionsScreen(
                    vm = sigmaViewModel,
                    sportId = entry.arguments?.getString("sportId").orEmpty(),
                    onBack = navController::popBackStack,
                    onCompetition = { navController.navigate(Routes.events(it)) },
                )
            }
            composable(Routes.EVENTS) { entry ->
                EventsScreen(
                    vm = sigmaViewModel,
                    competitionId = entry.arguments?.getString("competitionId").orEmpty(),
                    onBack = navController::popBackStack,
                    onEvent = { navController.navigate(Routes.event(it)) },
                )
            }
            composable(Routes.EVENT_DETAIL) { entry ->
                EventDetailScreen(
                    vm = sigmaViewModel,
                    eventId = entry.arguments?.getString("eventId").orEmpty(),
                    onBack = navController::popBackStack,
                    onQuestions = { navController.navigate(Routes.questions(it)) },
                )
            }
            composable(Routes.QUESTIONS) { entry ->
                QuestionsScreen(
                    vm = sigmaViewModel,
                    eventId = entry.arguments?.getString("eventId").orEmpty(),
                    onBack = navController::popBackStack,
                    onResearch = { eventId, questionId -> navController.navigate(Routes.research(eventId, questionId)) },
                )
            }
            composable(Routes.RESEARCH) { entry ->
                val eventId = entry.arguments?.getString("eventId").orEmpty()
                val questionId = entry.arguments?.getString("questionId").orEmpty()
                ResearchScreen(
                    vm = sigmaViewModel,
                    eventId = eventId,
                    questionId = questionId,
                    onBack = navController::popBackStack,
                    onResult = { navController.navigate(Routes.result(eventId, questionId)) },
                )
            }
            composable(Routes.RESULT) { entry ->
                ResultScreen(
                    vm = sigmaViewModel,
                    eventId = entry.arguments?.getString("eventId").orEmpty(),
                    questionId = entry.arguments?.getString("questionId").orEmpty(),
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.ANALYSIS) {
                AnalysisHubScreen(sigmaViewModel) { eventId, questionId ->
                    navController.navigate(Routes.result(eventId, questionId))
                }
            }
            composable(Routes.HISTORY) {
                HistoryScreen(sigmaViewModel) { eventId, questionId ->
                    navController.navigate(Routes.result(eventId, questionId))
                }
            }
            composable(Routes.PROFILE) { ProfileScreen(sigmaViewModel) }
            composable(Routes.IMPORT) {
                ImportResearchScreen(
                    vm = sigmaViewModel,
                    onBack = navController::popBackStack,
                    onImported = { navController.navigate(Routes.event(it)) },
                )
            }
        }
    }
}
