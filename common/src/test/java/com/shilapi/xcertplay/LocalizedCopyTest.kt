package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-level checks for the Chinese copy.
 *
 * Android lint already breaks the build when `values-zh` misses a key, but it cannot tell a real
 * translation from English text that was copied into the Chinese file. Local unit tests cannot load
 * resources either (stub `R`), so the guarantee is asserted here, against the resource files.
 */
class LocalizedCopyTest {
    /** Entries that are meant to read the same in both languages. */
    private val intentionallyIdentical = setOf(
        "app_name",
        "about_title",
        "notification_title",
        "content_desc_carplay",
        "language_english",
        "language_chinese",
        "choice_music_buffer_500",
        "report_saved_path",
    )

    @Test
    fun `chinese resources cover exactly the english keys`() {
        assertEquals(english.keys, chinese.keys)
    }

    @Test
    fun `no english entry was left untranslated`() {
        val untranslated = english.keys
            .filterNot { it in intentionallyIdentical }
            .filter { english[it] == chinese[it] }
            .sorted()
        assertTrue("still identical to English: $untranslated", untranslated.isEmpty())
    }

    @Test
    fun `every chinese entry has copy`() {
        val blank = chinese.filterValues { it.isBlank() }.keys.sorted()
        assertTrue("blank Chinese entries: $blank", blank.isEmpty())
    }

    private val english by lazy { read("values") }
    private val chinese by lazy { read("values-zh") }

    private fun read(folder: String): Map<String, String> {
        val file = listOf(
            File("src/main/res/$folder/strings.xml"),
            File("common/src/main/res/$folder/strings.xml"),
        ).firstOrNull { it.isFile } ?: error("strings.xml not found for $folder from ${File("").absolutePath}")
        return Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2].trim() }
    }
}
