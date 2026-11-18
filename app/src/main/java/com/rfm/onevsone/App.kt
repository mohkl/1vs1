package com.rfm.onevsone

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rfm.onevsone.data.Db
import com.rfm.onevsone.data.Repository
import com.rfm.onevsone.data.SettingsStore
import com.rfm.onevsone.domain.GameConfig
import com.rfm.onevsone.media.SoundFx
import com.rfm.onevsone.net.RoomSession
import com.rfm.onevsone.ui.AppRoot

class AppContainer(ctx: Context) {
    val settings = SettingsStore(ctx)
    val repo = Repository(Db(ctx))
    val sound = SoundFx(ctx, settings)
    var pendingGame: GameConfig? = null
    var session: RoomSession? = null
}

class App : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { AppRoot() }
    }
}
