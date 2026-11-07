package com.krinry.scancraft.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.krinry.scancraft.ui.camera.CameraScreen
import com.krinry.scancraft.ui.crop.CropScreen
import com.krinry.scancraft.ui.detail.DocumentDetailScreen
import com.krinry.scancraft.ui.filter.FilterScreen
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.krinry.scancraft.ui.home.HomeScreen
import com.krinry.scancraft.ui.navigation.Screen
import com.krinry.scancraft.ui.theme.ScanCraftTheme
import com.krinry.scancraft.ui.theme.ThemeManager
import com.krinry.scancraft.ui.viewer.PageViewerScreen
import com.krinry.scancraft.ui.pdf.EditPdfScreen
import com.krinry.scancraft.ui.profile.ProfileScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemeManager.init(this)

        setContent {
            val context = LocalContext.current
            val themeMode by ThemeManager.themeMode.collectAsState()
            val isDark = when (themeMode) {
                com.krinry.scancraft.ui.theme.AppThemeMode.LIGHT -> false
                com.krinry.scancraft.ui.theme.AppThemeMode.DARK -> true
                com.krinry.scancraft.ui.theme.AppThemeMode.SYSTEM -> ThemeManager.isDark(context)
            }

            ScanCraftTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    val externalUri = remember {
                        val data = intent?.data
                        val mime = intent?.type ?: (data?.let { context.contentResolver.getType(it) } ?: "")
                        if (data != null && (mime == "application/pdf" || data.toString().endsWith(".pdf", ignoreCase = true))) {
                            data
                        } else null
                    }

                    LaunchedEffect(externalUri) {
                        if (externalUri != null) {
                            val app = context.applicationContext as com.krinry.scancraft.ScanCraftApp
                            val repo = com.krinry.scancraft.data.repository.ScannerRepository(
                                context,
                                app.database.documentDao(),
                                app.database.pageDao(),
                                app.database.folderDao()
                            )
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val newDocId = repo.importPdfDocument(externalUri)
                                if (newDocId > 0) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        navController.navigate(Screen.EditPdf.createRoute(newDocId, 0))
                                    }
                                }
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                onNavigateToCamera = { docId, mode ->
                                    navController.navigate(Screen.Camera.createRoute(docId, mode))
                                },
                                onNavigateToDetail = { docId ->
                                    navController.navigate(Screen.DocumentDetail.createRoute(docId))
                                },
                                onNavigateToProfile = {
                                    navController.navigate(Screen.Profile.route)
                                },
                                onNavigateToEditPdf = { docId, pageIndex ->
                                    navController.navigate(Screen.EditPdf.createRoute(docId, pageIndex))
                                }
                            )
                        }

                        composable(
                            route = Screen.Camera.route,
                            arguments = listOf(
                                navArgument("docId") { type = NavType.LongType; defaultValue = -1L },
                                navArgument("mode") { type = NavType.StringType; defaultValue = "Document" }
                            )
                        ) { backStackEntry ->
                            val docId = backStackEntry.arguments?.getLong("docId") ?: -1L
                            val mode = backStackEntry.arguments?.getString("mode") ?: "Document"
                            CameraScreen(
                                docId = docId,
                                initialMode = mode,
                                onPhotoCaptured = { imagePath, targetDocId ->
                                    val encodedPath = Uri.encode(imagePath)
                                    navController.navigate(Screen.Crop.createRoute(encodedPath, targetDocId))
                                },
                                onClose = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Screen.Crop.route,
                            arguments = listOf(
                                navArgument("imagePath") { type = NavType.StringType },
                                navArgument("docId") { type = NavType.LongType; defaultValue = -1L }
                            )
                        ) { backStackEntry ->
                            val imagePath = Uri.decode(backStackEntry.arguments?.getString("imagePath").orEmpty())
                            val docId = backStackEntry.arguments?.getLong("docId") ?: -1L
                            CropScreen(
                                imagePath = imagePath,
                                docId = docId,
                                onNavigateToFilter = { path, dId, border ->
                                    val encodedPath = Uri.encode(path)
                                    val encodedBorder = Uri.encode(border)
                                    navController.navigate(Screen.Filter.createRoute(encodedPath, dId, encodedBorder))
                                },
                                onClose = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Screen.Filter.route,
                            arguments = listOf(
                                navArgument("imagePath") { type = NavType.StringType },
                                navArgument("docId") { type = NavType.LongType; defaultValue = -1L },
                                navArgument("border") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val imagePath = Uri.decode(backStackEntry.arguments?.getString("imagePath").orEmpty())
                            val docId = backStackEntry.arguments?.getLong("docId") ?: -1L
                            val border = Uri.decode(backStackEntry.arguments?.getString("border").orEmpty())
                            FilterScreen(
                                imagePath = imagePath,
                                docId = docId,
                                borderStr = border,
                                onSaved = { savedDocId ->
                                    navController.navigate(Screen.DocumentDetail.createRoute(savedDocId)) {
                                        popUpTo(Screen.Home.route)
                                    }
                                },
                                onClose = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Screen.DocumentDetail.route,
                            arguments = listOf(navArgument("docId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val docId = backStackEntry.arguments?.getLong("docId") ?: -1L
                            DocumentDetailScreen(
                                docId = docId,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToCamera = { dId ->
                                    navController.navigate(Screen.Camera.createRoute(dId, "Document"))
                                },
                                onNavigateToViewer = { pageId ->
                                    navController.navigate(Screen.PageViewer.createRoute(pageId))
                                },
                                onNavigateToEditPdf = { dId, pageIdx ->
                                    navController.navigate(Screen.EditPdf.createRoute(dId, pageIdx))
                                }
                            )
                        }

                        composable(
                            route = Screen.PageViewer.route,
                            arguments = listOf(navArgument("pageId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val pageId = backStackEntry.arguments?.getLong("pageId") ?: -1L
                            PageViewerScreen(
                                pageId = pageId,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Screen.EditPdf.route,
                            arguments = listOf(
                                navArgument("docId") { type = NavType.LongType },
                                navArgument("initialPageIndex") { type = NavType.IntType; defaultValue = 0 }
                            )
                        ) { backStackEntry ->
                            val docId = backStackEntry.arguments?.getLong("docId") ?: -1L
                            val initialPageIndex = backStackEntry.arguments?.getInt("initialPageIndex") ?: 0
                            EditPdfScreen(
                                docId = docId,
                                initialPageIndex = initialPageIndex,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Profile.route) {
                            ProfileScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
