package io.github.hatake716.taero

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
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
import java.util.Locale

class MainActivity : ComponentActivity() {
    lateinit var gameView: GameView
        private set
    private lateinit var store: GameStore
    private lateinit var audio: GameAudio

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
        AlertDialog.Builder(this).setTitle("神さまの遊び方")
            .setMessage("あなたはブロックを再生する神。CPUが全40個を壊しきるまで、ひたすら耐えよう！\n\n" +
                "① 点線の空きブロックを１本の指でタップ。指を離すと１つ再生します。なぞり・長押しでの連続再生はできません。\n\n" +
                "② 同時に２本以上の指で触ると「ズルはダメ！」。３秒間は再生できず、玉は動き続けます。\n\n" +
                "③ 右下の１列スロットは７秒ごとに確定。CPUにランダムな効果が追加されます。\n\n" +
                "＋３玉／速度×２／速度×３／５秒貫通／５秒分裂／玉数を１に／速度を初期値に\n\n" +
                "速度は掛け算で重複。貫通・分裂の再当選は残り時間に５秒加算。分裂は衝突した１玉が２玉になります。\n\n" +
                "④ 最後の１個を破壊されると終了。スコア＝生存時間（秒）²。端末内に上位100件を保存します。\n\n" +
                "CPUの棒は高速で左右に動きます。捕れない玉は落下し、全て落ちたら１玉を再発射します。\n\n" +
                "一時停止・バックグラウンド中は時間も効果も停止。戻ったら３秒の合図で再開します。")
            .setPositiveButton("わかった！", null).show()
    }

    private fun showSettings() {
        val labels = arrayOf("BGM：電子音 × ピアノ × ドラム", "効果音：ネオン花火", "振動フィードバック", "光と粒子の演出を控えめに")
        val checked = booleanArrayOf(store.music, store.sound, store.vibration, store.reduced)
        val dialog = AlertDialog.Builder(this).setTitle("音と演出")
            .setMultiChoiceItems(labels, checked) { _, which, value ->
                when (which) {
                    0 -> store.music = value
                    1 -> store.sound = value
                    2 -> store.vibration = value
                    3 -> store.reduced = value
                }
                audio.start()
                gameView.invalidate()
            }.setPositiveButton("閉じる", null).create()
        dialog.setOnDismissListener { if (gameView.screen == GameView.Screen.PAUSED) audio.pause() }
        dialog.show()
    }

    private fun showRankings() {
        val entries = store.rankings()
        val density = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20*density).toInt(), (12*density).toInt(), (20*density).toInt(), (16*density).toInt())
        }
        val date = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.JAPAN)
        if (entries.isEmpty()) layout.addView(TextView(this).apply {
            text = "記録はまだありません。\n最初の耐久記録を刻もう！"; textSize = 18f
        })
        entries.forEachIndexed { index, e ->
            layout.addView(TextView(this).apply {
                text = "${(index+1).toString().padStart(2, '0')}    ${ScoreFormat.score(e.ticks)} pt\n" +
                    "${ScoreFormat.seconds(e.ticks)} 秒  ·  再生 ${e.restores} 回\n${date.format(Date(e.dateMillis))}"
                textSize = 16f
                setTextColor(if (index == 0) 0xffdfff70.toInt() else Color.WHITE)
                setPadding(0, (12*density).toInt(), 0, (16*density).toInt())
                contentDescription = "${index+1}位、${ScoreFormat.score(e.ticks)}ポイント、${ScoreFormat.seconds(e.ticks)}秒"
            })
        }
        AlertDialog.Builder(this).setTitle("耐久ランキング  ${entries.size} / 100")
            .setView(ScrollView(this).apply { addView(layout) })
            .setPositiveButton("閉じる", null).show()
    }

    override fun onResume() { super.onResume(); if (::gameView.isInitialized) gameView.foreground() }
    override fun onPause() { if (::gameView.isInitialized) gameView.background(); super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::gameView.isInitialized) gameView.pauseGame()
    }
    override fun onDestroy() { if (::audio.isInitialized) audio.release(); super.onDestroy() }
}
