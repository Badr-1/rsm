package utils

import java.io.File
import java.net.URL

object FileUtils {
    fun downloadFile(url: String, destination: File): Boolean {
        return try {
            val content = URL(url).readText()
            destination.writeText(content)
            true
        } catch (e: Exception) {
            println("❌ Failed to download file: ${e.message}")
            false
        }
    }

    fun validateLatexFile(file: File): List<String> {
        val errors = mutableListOf<String>()

        if (!file.exists()) {
            errors.add("LaTeX file does not exist")
            return errors
        }

        val content = file.readText()

        // Basic LaTeX validation
        if (!content.contains("\\documentclass")) {
            errors.add("Missing \\documentclass declaration")
        }

        if (!content.contains("\\begin{document}")) {
            errors.add("Missing \\begin{document}")
        }

        if (!content.contains("\\end{document}")) {
            errors.add("Missing \\end{document}")
        }

        // Check for unmatched braces
        val openBraces = content.count { it == '{' }
        val closeBraces = content.count { it == '}' }
        if (openBraces != closeBraces) {
            errors.add("Unmatched braces: $openBraces open, $closeBraces close")
        }

        return errors
    }
}