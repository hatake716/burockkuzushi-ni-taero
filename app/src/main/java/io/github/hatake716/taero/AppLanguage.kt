package io.github.hatake716.taero

import android.content.Context
import android.app.LocaleManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object AppLanguage {
    private fun preferredLocales(base: Context): LocaleList {
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = base.getSystemService(LocaleManager::class.java)
            val override = manager.applicationLocales
            return if (override.isEmpty) manager.systemLocales else override
        }
        return Resources.getSystem().configuration.locales
    }

    /** Follow the first preferred language, including when Japanese is only a fallback. */
    fun localizedContext(base: Context, first: LocaleList = preferredLocales(base)): Context {
        // Resource matching can promote a secondary supported language; use the original preference list.
        val locale = if (!first.isEmpty && first[0].language == "ja") Locale.JAPANESE else Locale.ENGLISH
        val config = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(locale))
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }
}

data class SlotText(val label: Int, val symbol: Int, val secondary: Int? = null)

/** Keep translations out of the persisted effect identity and physics engine. */
val SlotEffect.text: SlotText get() = when (this) {
    SlotEffect.ADD_BALLS -> SlotText(R.string.slot_add_balls, R.string.symbol_add_three)
    SlotEffect.SPEED_DOUBLE -> SlotText(R.string.slot_double, R.string.symbol_double)
    SlotEffect.SPEED_TRIPLE -> SlotText(R.string.slot_triple, R.string.symbol_triple)
    SlotEffect.PIERCE -> SlotText(R.string.slot_pierce, R.string.symbol_pierce)
    SlotEffect.SPLIT -> SlotText(R.string.slot_split, R.string.symbol_split)
    SlotEffect.RESET_BALLS -> SlotText(R.string.slot_reset_balls, R.string.symbol_one_ball)
    SlotEffect.RESET_SPEED -> SlotText(R.string.slot_reset_speed, R.string.symbol_reset_speed)
    SlotEffect.ADD_FIVE_PIERCE -> SlotText(R.string.slot_five_pierce, R.string.symbol_add_five, R.string.symbol_pierce)
    SlotEffect.TRIPLE_SPLIT -> SlotText(R.string.slot_triple_split, R.string.symbol_triple, R.string.symbol_split)
}
