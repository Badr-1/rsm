import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.kinquirer.KInquirer
import com.github.kinquirer.components.promptList
import models.SectionType
import utils.FileUtils
import utils.GitUtils
import java.io.File

class ResumeCLI : CliktCommand(name = "resume") {
    override fun run() = Unit
}

class InitCommand : CliktCommand(name = "init", help = "Initialize a new resume repository") {
    override fun run() {
        val resumeManager = ResumeManager()
        resumeManager.initializeResume()
    }
}

class GenerateCommand : CliktCommand(name = "generate", help = "Generate LaTeX resume from configuration") {
    override fun run() {
        val resumeManager = ResumeManager()
        resumeManager.generateLatexFile()
    }
}

class CreateRoleCommand : CliktCommand(name = "create", help = "Create a new role-specific branch") {
    private val roleName by argument(help = "Name of the role/position")

    override fun run() {
        val resumeManager = ResumeManager()
        resumeManager.createRoleBranch(roleName)
    }
}

class AddCommand : CliktCommand(name = "add", help = "Add content to resume") {
    override fun run() {
        val sections = SectionType.entries.map { it.name.replace("_"," ") }
        val targets = GitUtils.listBranches()

        val section = SectionType.valueOf(
            KInquirer.promptList(
                "What section do you want to add content to?",
                sections.map { it.lowercase().replaceFirstChar { c -> c.uppercase() } }).replace(" ","_").uppercase()
        )
        val target = KInquirer.promptList("Select the target branch for this section:", targets)

        val resumeManager = ResumeManager()
        resumeManager.addToSection(section, target)
    }
}

class RemoveCommand : CliktCommand(name = "remove", help = "Remove content from resume") {

    override fun run() {
        val sections = SectionType.entries.map { it.name.replace("_"," ") }
        val targets = GitUtils.listBranches()

        val section = SectionType.valueOf(
            KInquirer.promptList(
                "What section do you want to remove content from?",
                sections.map { it.lowercase().replaceFirstChar { c -> c.uppercase() } }).replace(" ","_").uppercase()
        )
        val target = KInquirer.promptList("Select the target branch for this section:", targets)

        val resumeManager = ResumeManager()
        resumeManager.removeFromSection(section, target)
    }
}

class CompileCommand : CliktCommand(name = "compile", help = "Compile LaTeX resume to PDF") {
    private val generate by option("--generate", "-g", help = "Generate LaTeX file before compiling").flag(default = true)
    private val clean by option("--clean", "-c", help = "Clean auxiliary files after compilation").flag()
    private val open by option("--open", "-o", help = "Open PDF after compilation").flag()

    override fun run() {
        if (generate) {
            val resumeManager = ResumeManager()
            resumeManager.generateLatexFile()
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
                val os = System.getProperty("os.name").lowercase()
                when {
                    os.contains("mac") -> Runtime.getRuntime().exec("open resume.pdf")
                    os.contains("win") -> Runtime.getRuntime().exec("start resume.pdf")
                    else -> Runtime.getRuntime().exec("xdg-open resume.pdf")
                }
                println("📖 Opened resume.pdf")
            } catch (e: Exception) {
                println("⚠️  Could not open PDF automatically: ${e.message}")
            }
        }
    }
}


fun main(args: Array<String>) = ResumeCLI()
    .subcommands(
        InitCommand(),
        GenerateCommand(),
        CreateRoleCommand(),
        AddCommand(),
        RemoveCommand(),
        CompileCommand(),
    )
    .main(args)