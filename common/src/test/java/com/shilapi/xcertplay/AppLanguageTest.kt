package com.shilapi.xcertplay

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The language contract: an untouched install follows the head unit locale, while an explicit choice
 * is forced on top of it.
 *
 * Local unit tests run against stub resource ids, so the assertions stay on the configuration — which
 * resources a locale then resolves is checked by [LocalizedCopyTest] and by lint's MissingTranslation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], manifest = Config.NONE)
class AppLanguageTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `missing and unknown choices leave the locale to the head unit`() {
        assertNull(AppLanguage.resolve(null))
        assertNull(AppLanguage.resolve(""))
        assertNull(AppLanguage.resolve("fr"))
        assertEquals(Locale.ENGLISH, AppLanguage.resolve(AppLanguage.ENGLISH))
        assertEquals(Locale.SIMPLIFIED_CHINESE, AppLanguage.resolve(AppLanguage.CHINESE))
    }

    @Test
    fun `an untouched english head unit is left alone`() {
        clearStoredLanguage()
        assertSame(context, AppLanguage.wrap(context))
        assertEquals("en", AppLanguage.effective(context, null))
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    fun `an untouched chinese head unit is left alone too`() {
        clearStoredLanguage()
        assertSame(context, AppLanguage.wrap(context))
        assertEquals("zh", AppLanguage.effective(context, null))
    }

    @Test
    @Config(qualifiers = "zh-rTW")
    fun `a traditional chinese head unit counts as chinese`() {
        clearStoredLanguage()
        assertEquals("zh", AppLanguage.effective(context, null))
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    fun `choosing english overrides a chinese head unit`() {
        DiPlayPreferences.saveLanguage(context, AppLanguage.ENGLISH)
        val wrapped = AppLanguage.wrap(context)
        assertNotSame(context, wrapped)
        assertEquals("en", wrapped.resources.configuration.locales[0].language)
    }

    @Test
    fun `choosing chinese overrides an english head unit`() {
        DiPlayPreferences.saveLanguage(context, AppLanguage.CHINESE)
        val wrapped = AppLanguage.wrap(context)
        assertNotSame(context, wrapped)
        assertEquals("zh", wrapped.resources.configuration.locales[0].language)
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    fun `a stored choice survives the head unit changing language`() {
        DiPlayPreferences.saveLanguage(context, AppLanguage.CHINESE)
        assertEquals(AppLanguage.CHINESE, AppLanguage.effective(context, AppLanguage.CHINESE))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.effective(context, AppLanguage.ENGLISH))
    }

    private fun clearStoredLanguage() {
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().remove("language").commit()
    }
}
