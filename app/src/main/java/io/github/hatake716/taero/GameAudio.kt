package io.github.hatake716.taero

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import kotlin.random.Random

class GameAudio(context: Context, private val store: GameStore, private val focusLost: () -> Unit) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val pool = SoundPool.Builder().setMaxStreams(16).setAudioAttributes(attributes).build()
    private val loaded = mutableSetOf<Int>()
    private val sounds = mutableMapOf<String, Int>()
    private val music = MediaPlayer.create(context, R.raw.festival, attributes, manager.generateAudioSessionId())?.apply {
        isLooping = true
        setVolume(.48f, .48f)
    }
    private var focused = false
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
            if (change < 0) { focused = false; pause(); focusLost() }
        }.build()
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        for ((name, resource) in listOf("burst" to R.raw.burst, "restore" to R.raw.restore,
            "slot" to R.raw.slot, "cheat" to R.raw.cheat, "bounce" to R.raw.bounce)) {
            sounds[name] = pool.load(context, resource, 1)
        }
    }
    fun start() {
        if (!store.music && !store.sound) { pause(); return }
        if (!focused) focused = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!focused) return
        pool.autoResume()
        if (store.music) music?.start() else music?.pause()
    }
    fun play(name: String) {
        val id = sounds[name] ?: return
        if (focused && store.sound && id in loaded) {
            val rate = if (name == "burst") Random.nextDouble(.85, 1.8).toFloat() else 1f
            val volume = if (name == "burst") .36f else .65f
            pool.play(id, volume, volume, 1, 0, rate)
        }
    }
    fun pause() { music?.pause(); pool.autoPause() }
    fun release() {
        music?.release(); pool.release(); manager.abandonAudioFocusRequest(request)
    }
}
