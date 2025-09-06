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

object Utils {
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

    fun readLineRequired(prompt: String, validation: (String) -> Boolean = { true }): String {
        return KInquirer.promptInput(
            message = prompt,
            hint = "(required)",
            validation = { it.isNotEmpty() && validation(it) })
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
        return if (orderable)
            KInquirer.promptListObject(
                message,
                SectionType.entries.filter(predicate).map { Choice(it.displayName, it) })
        else
            KInquirer.promptListObject(
                message,
                SectionType.entries.filter(predicate).map { Choice(it.displayName, it) }
            )
    }

    fun promptSectionAndTargetBranch(message: String, predicate: (SectionType) -> Boolean): Pair<SectionType, String> {
        val branch = promptTargetBranch()
        val section = promptSection(message, false, predicate)
        return Pair(section, branch)
    }


    fun compilePdf(): Pair<Process, Int> {
        val process = ProcessBuilder("pdflatex", "resume.tex")
            .directory(File("."))
            .redirectErrorStream(true)
            .redirectOutput(
                ProcessBuilder.Redirect.to(
                    File(
                        if (System.getProperty("os.name").lowercase().contains("win")) "NUL" else "/dev/null"
                    )
                )
            )
            .start()

        val exitCode = process.waitFor()
        return Pair(process, exitCode)
    }

    inline fun <reified T> MutableList<T>.reorder(message: String) {
        val organized = KInquirer.promptOrderableListObject(
            message,
            this.map { Choice(it.toString(), it) }.toMutableList(),
            hint = "move using arrow keys"
        ) as MutableList<T>
        this.clear()
        this.addAll(organized)

        if (OrderableBullets::class.java.isAssignableFrom(T::class.java))
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