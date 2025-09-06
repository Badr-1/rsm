import com.github.ajalt.clikt.completion.CompletionCommand
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

object Rsm : CliktCommand(name = "rsm") {
    override fun run() = Unit
}

object InitCommand : CliktCommand(name = "init", help = "Initialize a new resume repository") {
    override fun run() {
        ResumeManager.initializeResume()
        Rsm.echoFormattedHelp()
    }
}

object ReorderCommand : CliktCommand(name = "reorder", help = "Reorder items in a section") {
    override fun run() {
        ResumeManager.reorderSections()
    }
}

object GenerateCommand : CliktCommand(name = "generate", help = "Generate LaTeX resume from configuration") {
    override fun run() {
        ResumeManager.generateLatexFile()
    }
}

object RoleCommand : CliktCommand(name = "role", help = "Create a new role-specific branch") {
    private val roleName by argument(help = "Name of the role/position")

    override fun run() {
        ResumeManager.createRoleBranch(roleName)
    }
}

object AddCommand : CliktCommand(name = "add", help = "Add content to resume") {
    override fun run() {
        ResumeManager.addToSection()
    }
}

object RemoveCommand : CliktCommand(name = "remove", help = "Remove content from resume") {

    override fun run() {
        ResumeManager.removeFromSection()
    }
}

object UpdateCommand : CliktCommand(name = "update", help = "Update resume content") {

    override fun run() {
        ResumeManager.updateAtSection()
    }
}

object CompileCommand : CliktCommand(name = "compile", help = "Compile LaTeX resume to PDF") {
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
        ResumeManager.compileLateXtoPdf(clean, open)
    }
}

fun main(args: Array<String>) = Rsm
    .subcommands(
        InitCommand,
        AddCommand,
        RemoveCommand,
        UpdateCommand,
        ReorderCommand,
        RoleCommand,
        GenerateCommand,
        CompileCommand,
        CompletionCommand()
    )
    .main(args)
