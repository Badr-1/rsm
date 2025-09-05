import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.kinquirer.core.Choice
import models.SectionType
import utils.FileUtils
import utils.Utils.promptSection
import utils.Utils.promptTargetBranch
import java.awt.Desktop
import java.io.File
import kotlin.collections.filter

val rsm = ResumeCLI()

class ResumeCLI : CliktCommand(name = "rsm") {
    override fun run() = Unit
}

class InitCommand : CliktCommand(name = "init", help = "Initialize a new resume repository") {
    override fun run() {
        ResumeManager.initializeResume()
        rsm.echoFormattedHelp()
    }
}

class ReorderCommand : CliktCommand(name = "reorder", help = "Reorder items in a section") {
    private val section by option(
        "--section",
        "-s",
        help = "choose section to reorder"
    ).flag(default = false)

    override fun run() {
        val target = promptTargetBranch("Select the target branch to reorder this in:")
        ResumeManager.reorderSections(target, section)
    }
}

class GenerateCommand : CliktCommand(name = "generate", help = "Generate LaTeX resume from configuration") {
    override fun run() {
        ResumeManager.generateLatexFile()
    }
}

class RoleCommand : CliktCommand(name = "role", help = "Create a new role-specific branch") {
    private val roleName by argument(help = "Name of the role/position")

    override fun run() {
        ResumeManager.createRoleBranch(roleName)
    }
}

class AddCommand : CliktCommand(name = "add", help = "Add content to resume") {
    override fun run() {
        val section = promptSection(
            "What section do you want to add content to?",
            SectionType.entries.filter { !it.isFixed }.map { Choice(it.displayName, it) })
        val target = promptTargetBranch("Select the target branch to add this to:")
        ResumeManager.addToSection(section, target)
    }
}

class RemoveCommand : CliktCommand(name = "remove", help = "Remove content from resume") {

    override fun run() {
        val section = promptSection(
            "What section do you want to remove content from?",
            SectionType.entries.filter { !it.isFixed }.map { Choice(it.displayName, it) })
        val target = promptTargetBranch("Select the target branch to remove this from:")
        ResumeManager.removeFromSection(section, target)
    }
}

class UpdateCommand : CliktCommand(name = "update", help = "Update resume content") {

    override fun run() {
        val section = promptSection(
            "What section do you want to update?",
            SectionType.entries.map { Choice(it.displayName, it) })

        val target = promptTargetBranch("Select the target branch to update this at:")
        ResumeManager.updateAtSection(section, target)
    }
}



class CompileCommand : CliktCommand(name = "compile", help = "Compile LaTeX resume to PDF") {
    private val generate by option(
        "--generate",
        "-g",
        help = "Generate LaTeX file before compiling"
    ).flag(default = true)
    private val clean by option("--clean", "-c", help = "Clean auxiliary files after compilation").flag()
    private val open by option("--open", "-o", help = "Open PDF after compilation").flag()

    override fun run() {
        if (generate) {
            ResumeManager.generateLatexFile()
        }

        if (!resumeFile.exists()) {
            println("❌ resume.tex not found. Run 'resume init' first.")
            return
        }


        val errors = FileUtils.validateLatexFile(resumeFile)
        if (errors.isNotEmpty()) {
            println("❌ LaTeX validation errors:")
            errors.forEach { println("  • $it") }
            return
        }

        println("🔨 Compiling LaTeX to PDF...")

        try {
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

            if (exitCode == 0) {
                println("✅ Successfully compiled to resume.pdf")

                if (clean) {
                    cleanAuxiliaryFiles()
                }

                if (open) {
                    openPdf()
                }
            } else {
                println("❌ Compilation failed with exit code: $exitCode")
                val output = process.inputStream.bufferedReader().readText()
                if (output.isNotEmpty()) {
                    println("Error output:")
                    println(output)
                }
            }
        } catch (e: Exception) {
            println("❌ Failed to compile: ${e.message}")
            println("💡 Make sure pdflatex is installed and in your PATH")
        }
    }

    private fun cleanAuxiliaryFiles() {
        val auxiliaryExtensions = listOf("aux", "log", "out", "fls", "fdb_latexmk", "synctex.gz")
        auxiliaryExtensions.forEach { ext ->
            val file = File("resume.$ext")
            if (file.exists()) {
                file.delete()
                println("🧹 Cleaned resume.$ext")
            }
        }
    }

    private fun openPdf() {
        if (pdfFile.exists()) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(pdfFile)
                }
                println("📖 Opened resume.pdf")
            } catch (e: Exception) {
                println("⚠️  Could not open PDF automatically: ${e.message}")
            }
        }
    }
}


fun main(args: Array<String>) = rsm
    .subcommands(
        InitCommand(),
        GenerateCommand(),
        RoleCommand(),
        AddCommand(),
        RemoveCommand(),
        UpdateCommand(),
        ReorderCommand(),
        CompileCommand(),
    )
    .main(args)