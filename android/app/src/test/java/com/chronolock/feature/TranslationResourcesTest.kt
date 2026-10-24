package com.chronolock.feature

import com.chronolock.R
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * Every Korean UI literal must map to a resource, and every mapped resource must exist in all five languages with the
 * same placeholders; otherwise the Korean source text or a crash shows up in another language.
 */
class TranslationResourcesTest {
    private val res = File("src/main/res")
    private val languages = mapOf("ko" to "values", "en" to "values-en", "ja" to "values-ja", "zh" to "values-zh-rCN", "es" to "values-es")
    /** Quantities Android selects for each language (CLDR); a missing one falls back to "other" only by luck. */
    private val quantities = mapOf("ko" to setOf("other"), "en" to setOf("one", "other"), "ja" to setOf("other"),
        "zh" to setOf("other"), "es" to setOf("one", "many", "other"))

    private fun names(type: Class<*>) = type.fields.associate { it.getInt(null) to it.name }
    private fun placeholders(text: String) = Regex("%(\\d+)\\$([sd])").findAll(text).map { it.value }.toSortedSet()
    private fun xml(language: String) = res.resolve(languages.getValue(language)).listFiles { file -> file.extension == "xml" }!!
        .joinToString("\n") { it.readText() }
    private fun strings(language: String) = Regex("<string name=\"(\\w+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        .findAll(xml(language)).associate { it.groupValues[1] to it.groupValues[2] }
    private fun plurals(language: String) = Regex("<plurals name=\"(\\w+)\"[^>]*>(.*?)</plurals>", RegexOption.DOT_MATCHES_ALL)
        .findAll(xml(language)).associate { match ->
            match.groupValues[1] to Regex("<item quantity=\"(\\w+)\">(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
                .findAll(match.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
        }

    @Test fun mappedStrings_existInEveryLanguageWithTheSamePlaceholders() {
        val stringNames = names(R.string::class.java)
        val all = languages.keys.associateWith { strings(it) }
        translationKeys.forEach { (source, id) ->
            val name = stringNames.getValue(id)
            val expected = placeholders(source)
            languages.keys.forEach { language ->
                val text = all.getValue(language)[name]
                assertNotNull("$language is missing $name", text)
                assertEquals("$language $name placeholders", expected, placeholders(text!!))
            }
        }
    }

    @Test fun mappedPlurals_haveEveryQuantityInEveryLanguage() {
        val pluralNames = names(R.plurals::class.java)
        val all = languages.keys.associateWith { plurals(it) }
        pluralKeys.forEach { (source, mapping) ->
            val name = pluralNames.getValue(mapping.first)
            val expected = placeholders(source)
            assertTrue("$name: count argument", mapping.second < expected.size)
            languages.keys.forEach { language ->
                val items = all.getValue(language)[name]
                assertNotNull("$language is missing plurals $name", items)
                assertEquals("$language $name quantities", quantities.getValue(language), items!!.keys)
                items.forEach { (quantity, text) -> assertEquals("$language $name $quantity placeholders", expected, placeholders(text)) }
            }
        }
    }

    @Test fun koreanLiteralsInUiCode_areMapped() {
        // Native language names, preview labels and sample data, and the patterns that re-translate domain messages.
        val allowed = setOf("한국어", "오늘 · 휴대폰", "오늘 · 태블릿", "오늘 · 큰 글씨", "앱 제한", "동영상", "소셜")
        val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
        val hangul = Regex("[가-힣]")
        val missing = listOf("feature", "blocking", "data").flatMap { dir ->
            File("src/main/java/com/chronolock/$dir").walk().filter { it.extension == "kt" && it.name != "TranslationKeys.kt" }.flatMap { file ->
                file.readLines().withIndex().flatMap { (index, line) ->
                    if (line.trimStart().startsWith("//") || line.trimStart().startsWith("*") || "Regex(" in line) emptyList()
                    else literal.findAll(line).map { it.groupValues[1].replace("\\$", "$").replace("\\n", "\n") }
                        .filter { hangul.containsMatchIn(it) && it !in translationKeys && it !in pluralKeys && it !in allowed }
                        .map { "${file.name}:${index + 1}: $it" }.toList()
                }
            }
        }
        assertTrue(missing.joinToString("\n"), missing.isEmpty())
    }
}
