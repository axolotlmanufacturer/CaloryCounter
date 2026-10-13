package de.privat.kalorienzaehler

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * All user-facing text must come from `strings.xml` (PROMPT.md §2.3). Android lint's
 * `HardcodedText` only covers XML layouts, so this test scans the Compose sources for
 * string literals passed to text-bearing parameters.
 */
class NoHardcodedUiStringsTest {

    // Gradle runs unit tests with the module directory as working directory.
    private val sourceRoots = listOf(File("src/main/java"), File("src/main/kotlin")).filter { it.isDirectory }

    // Text("…"), contentDescription = "…", label = "…", etc. with a literal containing a letter.
    // `\s*` also spans line breaks, so calls formatted over several lines are caught too.
    private val hardcoded = Regex(
        """(\bText\(\s*|\b(text|title|label|message|placeholder|contentDescription|supportingText|actionLabel)\s*=\s*)"[^"]*\p{L}""",
    )

    @Test
    fun composeSourcesUseStringResources() {
        assertTrue("no source root found under ${File(".").absolutePath}", sourceRoots.isNotEmpty())
        val offenders = sourceRoots.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    val source = file.readText()
                    hardcoded.findAll(source).map { match ->
                        val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                        "${file.relativeTo(root)}:$line: ${match.value.trim()}"
                    }
                }
                .toList()
        }
        assertTrue("Hardcoded UI strings found (move them to strings.xml):\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun detectorFindsTypicalViolations() {
        listOf(
            """Text("Heute")""",
            """Text(text = "Gewicht")""",
            "Text(\n    \"Heute\",\n    style = style,\n)",
            """Icon(painter, contentDescription = "Zurück")""",
            """TopAppBar(title = "Profil")""",
        ).forEach { assertTrue("not detected: $it", hardcoded.containsMatchIn(it)) }
        listOf(
            """Text(stringResource(R.string.nav_today))""",
            """Icon(painter, contentDescription = null)""",
            """Modifier.testTag("diary-list")""",
        ).forEach { assertTrue("false positive: $it", !hardcoded.containsMatchIn(it)) }
    }
}
