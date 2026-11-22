package utils

import java.io.File

val configFile = File("resume-config.json")
val resumeFile = File("resume.tex")
val ignoreFile = File(".gitignore")
val pdfFile = File("resume.pdf")

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

    data class LatexError(val message: String, val line: Int?, val source: String?)

    /**
     * Extracts the errors from a pdflatex log. Each error starts with a line
     * beginning with "! " and is usually followed (a few lines later) by
     * "l.<number> ..." giving the line in the .tex file. [texLines] is used to
     * show the full source line, since the log truncates it.
     */
    fun parseLatexLog(logLines: List<String>, texLines: List<String>): List<LatexError> {
        val lineRef = Regex("""^l\.(\d+)""")
        val errors = mutableListOf<LatexError>()
        logLines.forEachIndexed { i, raw ->
            if (!raw.startsWith("! ")) return@forEachIndexed
            val message = raw.removePrefix("! ").trim()
            // Follow-up lines that only restate an earlier error
            if (message.startsWith("==> Fatal error") || message == "Emergency stop.") return@forEachIndexed
            // For missing files TeX reports the line *after* the \usepackage, which is
            // misleading, and the cause isn't in the resume content anyway.
            val lineNumber = if (isMissingFile(message)) {
                null
            } else {
                logLines.drop(i + 1).take(10)
                    .firstNotNullOfOrNull { lineRef.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            }
            val source = lineNumber?.let { texLines.getOrNull(it - 1)?.trim() }
            errors.add(LatexError(message, lineNumber, source))
        }
        return errors.distinct()
    }

    private fun isMissingFile(message: String) = message.contains("File `") && message.contains("' not found")

    /** A short hint for common errors caused by resume content, or null. */
    fun hintFor(error: LatexError): String? = when {
        error.message.startsWith("Missing \$ inserted") ->
            "A special character such as _ or ^ isn't escaped. Write it as \\_ or \\^{} in your resume content."
        error.message.startsWith("Misplaced alignment tab character &") ->
            "An & isn't escaped. Write it as \\& in your resume content."
        error.message.contains("Undefined control sequence") ->
            "A backslash (\\) in your content starts an unknown LaTeX command. Write a literal backslash as \\textbackslash{}."
        isMissingFile(error.message) ->
            "A LaTeX package is missing. Re-run the rsm installer to install the required packages."
        else -> null
    }
}
