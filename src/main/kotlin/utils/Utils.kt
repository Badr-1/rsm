package utils

import com.github.kinquirer.KInquirer
import com.github.kinquirer.components.promptInput

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

    fun readLineRequired(prompt: String): String {
        return KInquirer.promptInput(message = prompt, hint = "(required)", validation = { it.isNotEmpty() })
            .escapeLatexSpecialChars()
    }

    fun readLineOptional(prompt: String): String {
        return KInquirer.promptInput(message = prompt, hint = "(optional)").escapeLatexSpecialChars()
    }

}