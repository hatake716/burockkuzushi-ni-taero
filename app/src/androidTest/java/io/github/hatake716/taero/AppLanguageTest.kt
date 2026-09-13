package io.github.hatake716.taero

import android.os.LocaleList
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLanguageTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private fun localized(tags: String) = AppLanguage.localizedContext(base, LocaleList.forLanguageTags(tags))

    @Test fun japaneseFirstUsesJapaneseRegardlessOfCountryOrFallback() {
        for(tags in listOf("ja-JP", "ja-US,en-US", "ja,fr-FR")) {
            val c=localized(tags)
            assertEquals("ja",c.resources.configuration.locales[0].language)
            assertEquals("ブロック崩しに耐えろ！",c.getString(R.string.app_name))
            assertEquals("ズルはダメ！",c.getString(R.string.cheat_title))
            assertEquals("貫通",c.getString(SlotEffect.ADD_FIVE_PIERCE.text.secondary!!))
        }
    }

    @Test fun allOtherPrimaryLanguagesUseEnglishIncludingJapaneseSecondary() {
        val english=localized("en-US")
        val strings=R.string::class.java.fields.filter { it.type==Int::class.javaPrimitiveType }
        for(tags in listOf("en-GB", "fr-FR,ja-JP", "ko-KR,ja-JP", "zh-CN", "ar-EG,ja-JP", "")) {
            val c=localized(tags)
            assertEquals("en",c.resources.configuration.locales[0].language)
            assertEquals(View.LAYOUT_DIRECTION_LTR,c.resources.configuration.layoutDirection)
            assertEquals("Survive Breakout!",c.getString(R.string.app_name))
            assertEquals("NO CHEATING!",c.getString(R.string.cheat_title))
            for(field in strings) {
                assertEquals("$tags: ${field.name}",english.getString(field.getInt(null)),c.getString(field.getInt(null)))
            }
            for(effect in SlotEffect.entries) {
                val text=effect.text
                val copy=c.getString(text.label)+c.getString(text.symbol)+text.secondary?.let(c::getString).orEmpty()
                assertFalse(copy.any { it in '\u3040'..'\u30ff' || it in '\u4e00'..'\u9fff' })
            }
        }
    }

    @Test fun changingLanguageKeepsSavedRunRankingAndSettings() {
        base.getSharedPreferences("taero.v1",0).edit().clear().commit()
        val japanese=GameStore(localized("ja-JP"))
        val entry=ScoreEntry("language-test",123456,1234,7,8)
        japanese.record(entry); japanese.music=false
        val e=GameEngine().apply {
            blocks[9]=false
            applyEffect(SlotEffect.ADD_FIVE_PIERCE); applyEffect(SlotEffect.TRIPLE_SPLIT)
        }
        japanese.saveRun("saved-in-japanese",e)
        for(tags in listOf("en-US", "fr-FR,ja-JP", "ja-JP")) {
            val store=GameStore(localized(tags))
            assertEquals(listOf(entry),store.rankings()); assertFalse(store.music)
            val saved=store.loadRun()!!
            assertEquals("saved-in-japanese",saved.first)
            assertEquals(SlotEffect.TRIPLE_SPLIT,saved.second.lastEffect)
            assertEquals(6,saved.second.balls.size); assertEquals(3.0,saved.second.speedMultiplier,0.0)
            assertTrue(saved.second.piercing); assertTrue(saved.second.splitting)
            assertFalse(saved.second.blocks[9])
        }
    }
}
