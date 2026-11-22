package utils

import com.github.kinquirer.KInquirer
import com.github.kinquirer.components.promptConfirm
import com.github.kinquirer.components.promptInput
import com.github.kinquirer.components.promptList
import com.github.kinquirer.components.promptListObject
import com.github.kinquirer.components.promptOrderableListObject
import com.github.kinquirer.core.Choice
import models.OrderableBullets
import models.SectionType
import java.io.File
import java.util.concurrent.TimeUnit

object Utils {
    // A resume compiles in well under a second; this only guards against hangs.
    const val COMPILE_TIMEOUT_SECONDS = 60L

    fun List<Any>.toCommitMessage(title: String): String {
        return if (this.isEmpty()) {
            ""
        } else {
            title + this.joinToString(prefix = "\n- ", separator = "\n- ", postfix = "\n") { it.toString() }
        }
    }

    fun String.escapeLatexSpecialChars(): String {
        return this.replace("""[&%$#_{}~^\\]""".toRegex()) { matchResult ->
            when (val match = matchResult.value) {
                "&" -> "\\&"
                "%" -> "\\%"
                "$" -> "\\$"
                "#" -> "\\#"
                "_" -> "\\_"
                "{" -> "\\{"
                "}" -> "\\}"
                "~" -> "\\textasciitilde{}"
                "^" -> "\\textasciicircum{}"
                "\\" -> "\\textbackslash{}"
                else -> match
            }
        }
    }

    // Characters that make pdflatex fail when they appear unescaped in text.
    // Ones already escaped (preceded by a backslash, as stored by the prompts
    // above) are left alone, so this is safe to apply to any stored content.
    private val unescapedLatexChars = Regex("""(?<!\\)[&%$#_^]""")

    /** Escapes only the special characters that aren't escaped yet (idempotent). */
    fun String.escapeUnescapedLatex(): String = replace(unescapedLatexChars) { match ->
        if (match.value == "^") "\\textasciicircum{}" else "\\" + match.value
    }

    fun readLineRequired(prompt: String, validation: (String) -> Boolean = { true }): String {
        return KInquirer.promptInput(
            message = prompt,
            hint = "(required)",
            validation = { it.isNotEmpty() && validation(it) }
        )
            .escapeLatexSpecialChars()
    }

    fun readLineOptional(prompt: String, default: String = "", validation: (String) -> Boolean = { true }): String {
        return KInquirer.promptInput(message = prompt, hint = "(optional)", default = default, validation = validation)
            .escapeLatexSpecialChars()
    }

    fun promptTargetBranch(): String {
        return KInquirer.promptList("Select target branch:", GitUtils.listBranches())
    }

    fun promptSection(message: String, orderable: Boolean, predicate: (SectionType) -> Boolean): SectionType {
        return if (orderable) {
            KInquirer.promptListObject(
                message,
                SectionType.entries.filter(predicate).map { Choice(it.displayName, it) }
            )
        } else {
            KInquirer.promptListObject(
                message,
                SectionType.entries.filter(predicate).map { Choice(it.displayName, it) }
            )
        }
    }

    fun promptSectionAndTargetBranch(message: String, predicate: (SectionType) -> Boolean): Pair<SectionType, String> {
        val branch = promptTargetBranch()
        val section = promptSection(message, false, predicate)
        return Pair(section, branch)
    }

    /**
     * Runs pdflatex on resume.tex. Returns the process and its exit code, or a
     * null exit code if it didn't finish within [COMPILE_TIMEOUT_SECONDS] (the
     * process is killed in that case).
     */
    fun compilePdf(): Pair<Process, Int?> {
        val nullDevice = File(
            if (System.getProperty("os.name").lowercase().contains("win")) "NUL" else "/dev/null"
        )
        // pdflatex's default "errorstopmode" stops at the first error and waits for
        // the user to type something. Its output is hidden and nothing ever answers,
        // so rsm would hang forever. nonstopmode + halt-on-error make it exit with an
        // error instead, and reading stdin from the null device means any other
        // prompt (e.g. "Enter file name:" for a missing package) gets EOF right away.
        val process = ProcessBuilder(
            "pdflatex",
            "-interaction=nonstopmode",
            "-halt-on-error",
            "resume.tex"
        )
            .directory(File("."))
            .redirectErrorStream(true)
            .redirectInput(ProcessBuilder.Redirect.from(nullDevice))
            .redirectOutput(ProcessBuilder.Redirect.to(nullDevice))
            .start()

        if (!process.waitFor(COMPILE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
            return Pair(process, null)
        }
        return Pair(process, process.exitValue())
    }

    inline fun <reified T> MutableList<T>.reorder(reorderBullets: Boolean = true) {
        val organized = KInquirer.promptOrderableListObject(
            "Reorder ${T::class.java.simpleName} Entries:",
            this.map { Choice(it.toString(), it) }.toMutableList(),
            hint = "move using arrow keys"
        ) as MutableList<T>
        this.clear()
        this.addAll(organized)

        if (reorderBullets && OrderableBullets::class.java.isAssignableFrom(T::class.java)) {
            forEach {
                val reorderBulletsConfirm = KInquirer.promptConfirm(
                    "Do you want to reorder bullets for $it ?",
                    default = false
                )
                if (reorderBulletsConfirm) {
                    (it as OrderableBullets).reorderBullets()
                }
            }
        }
    }
}
