package utils

import com.github.kinquirer.KInquirer
import com.github.kinquirer.components.promptInput
import com.github.kinquirer.components.promptList
import com.github.kinquirer.components.promptListObject
import com.github.kinquirer.core.Choice
import models.SectionType

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
        return KInquirer.promptInput(message = prompt, hint = "(required)", validation = { it.isNotEmpty() })
            .escapeLatexSpecialChars()
    }

    fun readLineOptional(prompt: String, default: String = "", validation: (String) -> Boolean = { true }): String {
        return KInquirer.promptInput(message = prompt, hint = "(optional)", default = default, validation = validation)
            .escapeLatexSpecialChars()
    }

    fun promptSection(message: String, choices: List<Choice<SectionType>>): SectionType {
        val selected = KInquirer.promptListObject(message, choices)
        return selected
    }

    fun promptTargetBranch(message: String): String {
        return KInquirer.promptList(message, GitUtils.listBranches())
    }

}