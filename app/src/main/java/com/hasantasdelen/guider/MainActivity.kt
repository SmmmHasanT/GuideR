package com.hasantasdelen.guider

import android.app.Application
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hasantasdelen.guider.data.GuideType
import com.hasantasdelen.guider.data.Library
import com.hasantasdelen.guider.data.Prefs
import com.hasantasdelen.guider.reader.*
import com.hasantasdelen.guider.ui.*
import com.hasantasdelen.guider.util.Lang
import java.net.URLDecoder
import java.net.URLEncoder

class GuideRApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Library.init(this)
    }
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.isTr = resources.configuration.locales[0].language == "tr"
        enableEdgeToEdge()
        setContent {
            GuideRTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    AppNav()
                }
            }
        }
    }
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
private fun dec(s: String?) = s?.let { URLDecoder.decode(it, "UTF-8") }

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onGame = { nav.navigate("game/$it") },
                onAdd = { nav.navigate("add") },
                onSettings = { nav.navigate("settings") }
            )
        }
        composable("add") {
            AddGameScreen(onBack = { nav.popBackStack() }, onAdded = { id ->
                nav.navigate("game/$id") { popUpTo("home") }
            })
        }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable("game/{id}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            GameDetailScreen(
                gameId = id,
                onBack = { nav.popBackStack() },
                onOpenGuide = { g -> nav.navigate("read/$id/${g.id}") },
                onFind = { nav.navigate("browse/$id") },
                onPixie = { nav.navigate("pixie/$id") },
                onAchievements = { nav.navigate("ra/$id") },
            )
        }
        composable(
            "browse/{id}?url={url}",
            arguments = listOf(navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            BrowserScreen(id, dec(e.arguments?.getString("url")), onBack = { nav.popBackStack() })
        }
        composable("pixie/{id}") { e ->
            PixieScreen(e.arguments?.getString("id"), onBack = { nav.popBackStack() }, onSettings = { nav.navigate("settings") })
        }
        composable("ra/{id}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            AchievementsScreen(id, onBack = { nav.popBackStack() }, onSettings = { nav.navigate("settings") })
        }
        composable("read/{gid}/{id}") { e ->
            val gid = e.arguments?.getString("gid") ?: return@composable
            val id = e.arguments?.getString("id") ?: return@composable
            val guide = remember { Library.guide(gid, id) }
            if (guide == null) {
                LaunchedEffect(Unit) { nav.popBackStack() }
                return@composable
            }
            val back: () -> Unit = { nav.popBackStack() }
            val pixie: (() -> Unit)? = if (Prefs.pixieEnabled) ({ nav.navigate("pixie/$gid") }) else null
            var image by remember { mutableStateOf<String?>(null) }
            when (guide.type) {
                GuideType.TXT -> TxtReaderScreen(guide, back, pixie)
                GuideType.HTML, GuideType.URL -> HtmlReaderScreen(guide, back, pixie) { image = it }
                GuideType.PDF -> PdfReaderScreen(guide, back, pixie)
                GuideType.IMAGE -> ImageReaderScreen(guide, back)
                GuideType.CBZ -> CbzReaderScreen(guide, back)
            }
            image?.let { ImageDialog(it) { image = null } }
        }
    }
}
