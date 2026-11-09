package ai.riskmandate.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the colour rules in docs/design/android-theme.md ("Which colour for what"), for variant A where red
 * means only "blocked" and "error":
 *
 * 1. `Pt.colors.red` (and `Color.Red`) is used only in Theme.kt (the palette and its status tones) and in
 *    ui/StatTile.kt (`Tone.Bad`). The brand colour is `Pt.colors.accent` (or `colorScheme.primary`); errors use
 *    `colorScheme.error`; outcomes use `RiskMandateStatus.palette`.
 * 2. No screen writes a colour literal (`Color(0xFF...)`): tokens live in Theme.kt.
 *
 * If this fails, change the colour to the right token; do not widen the allow-list without design.
 */
class ColourRulesTest {
    private val sources: List<File> = File("src/main/java").walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()

    private fun offenders(pattern: Regex, allowed: Set<String>) =
        sources.filter { it.name !in allowed && pattern.containsMatchIn(it.readText()) }.map { it.path }

    @Test fun sourcesWereFound() = assertTrue("no sources found", sources.size > 20)

    @Test fun redIsOnlyForBlockedAndError() {
        val red = Regex("""\.red\b|\bColor\.Red\b""")
        assertEquals(emptyList<String>(), offenders(red, allowed = setOf("Theme.kt", "StatTile.kt")))
    }

    @Test fun noColourLiteralsOutsideTheTheme() {
        val literal = Regex("""\bColor\(\s*0x[0-9A-Fa-f]+""")
        assertEquals(emptyList<String>(), offenders(literal, allowed = setOf("Theme.kt")))
    }
}
