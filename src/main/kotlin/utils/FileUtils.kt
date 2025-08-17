package utils

import java.io.File

object FileUtils {

    fun validateLatexFile(file: File): List<String> {
        val errors = mutableListOf<String>()

        if (!file.exists()) {
            errors.add("LaTeX file does not exist")
            return errors
        }

        val content = file.readText()

        if (!content.contains("\\documentclass")) {
            errors.add("Missing \\documentclass declaration")
        }

        if (!content.contains("\\begin{document}")) {
            errors.add("Missing \\begin{document}")
        }

        if (!content.contains("\\end{document}")) {
            errors.add("Missing \\end{document}")
        }


        val openBraces = content.count { it == '{' }
        val closeBraces = content.count { it == '}' }
        if (openBraces != closeBraces) {
            errors.add("Unmatched braces: $openBraces open, $closeBraces close")
        }

        return errors
    }
}