package io.github.hatake716.taero

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    lateinit var gameView: GameView
        private set
    private lateinit var store: GameStore
    private lateinit var audio: GameAudio
    private val gameFont by lazy { resources.getFont(R.font.dot_gothic) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        store = GameStore(this)
        audio = GameAudio(this, store) { if (::gameView.isInitialized) gameView.pauseGame() }
        gameView = GameView(this, store, audio, ::showRankings, ::showGuide, ::showSettings)
        val root = android.widget.FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(8, 12, 28))
            addView(gameView)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (gameView.screen) {
                    GameView.Screen.PLAYING, GameView.Screen.COUNTDOWN -> gameView.pauseGame()
                    GameView.Screen.PAUSED, GameView.Screen.RESULT -> gameView.goHome()
                    GameView.Screen.TITLE -> finish()
                }
            }
        })
    }

    private fun showGuide() {
        AlertDialog.Builder(this).setTitle(R.string.guide_title)
            .setMessage(R.string.guide_body)
            .setPositiveButton(R.string.got_it, null).create().showWithGameFont()
    }

    private fun AlertDialog.showWithGameFont() {
        show()
        val root = window?.decorView ?: return
        fun apply(view: View) {
            if (view is TextView && view.typeface != gameFont) view.typeface = gameFont
            if (view is ViewGroup) for (i in 0 until view.childCount) apply(view.getChildAt(i))
        }
        // Platform dialog titles can override the theme font. Also style list
        // rows when Android creates/recycles them after layout or scrolling.
        root.viewTreeObserver.addOnGlobalLayoutListener { apply(root) }
        apply(root)
    }

    private fun showSettings() {
        val labels = arrayOf(R.string.setting_music, R.string.setting_sound, R.string.setting_vibration, R.string.setting_reduced).map { getString(it) }.toTypedArray()
        val checked = booleanArrayOf(store.music, store.sound, store.vibration, store.reduced)
        val dialog = AlertDialog.Builder(this).setTitle(R.string.settings)
            .setMultiChoiceItems(labels, checked) { _, which, value ->
                when (which) {
                    0 -> store.music = value
                    1 -> store.sound = value
                    2 -> store.vibration = value
                    3 -> store.reduced = value
                }
                audio.start()
                gameView.invalidate()
            }.setNeutralButton(R.string.privacy_title) { _, _ ->
                AlertDialog.Builder(this).setTitle(R.string.privacy_title)
                    .setMessage(R.string.privacy_body).setPositiveButton(R.string.close, null).create().showWithGameFont()
            }.setPositiveButton(R.string.close, null).create()
        dialog.setOnDismissListener { if (gameView.screen == GameView.Screen.PAUSED) audio.pause() }
        dialog.showWithGameFont()
    }

    private fun showRankings() {
        val entries = store.rankings()
        val density = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20*density).toInt(), (12*density).toInt(), (20*density).toInt(), (16*density).toInt())
        }
        val date = SimpleDateFormat(getString(R.string.ranking_date_pattern), resources.configuration.locales[0])
        if (entries.isEmpty()) layout.addView(TextView(this).apply {
            text = getString(R.string.rankings_empty); textSize = 18f
        })
        entries.forEachIndexed { index, e ->
            layout.addView(TextView(this).apply {
                text = getString(R.string.ranking_entry, (index+1).toString().padStart(2, '0'),
                    ScoreFormat.score(e.ticks), ScoreFormat.seconds(e.ticks), e.restores, date.format(Date(e.dateMillis)))
                textSize = 16f
                setTextColor(if (index == 0) 0xffdfff70.toInt() else Color.WHITE)
                setPadding(0, (12*density).toInt(), 0, (16*density).toInt())
                contentDescription = getString(R.string.ranking_description, index+1, ScoreFormat.score(e.ticks), ScoreFormat.seconds(e.ticks))
            })
        }
        AlertDialog.Builder(this).setTitle(getString(R.string.rankings_title, entries.size))
            .setView(ScrollView(this).apply { addView(layout) })
            .setPositiveButton(R.string.close, null).create().showWithGameFont()
    }

    override fun onResume() { super.onResume(); if (::gameView.isInitialized) gameView.foreground() }
    override fun onPause() { if (::gameView.isInitialized) gameView.background(); super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::gameView.isInitialized) gameView.pauseGame()
    }
    override fun onDestroy() { if (::audio.isInitialized) audio.release(); super.onDestroy() }
}
