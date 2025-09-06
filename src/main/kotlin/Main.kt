import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

object Rsm : CliktCommand(name = "rsm") {
    override fun run() = Unit
}

class InitCommand : CliktCommand(name = "init", help = "Initialize a new resume repository") {
    override fun run() {
        ResumeManager.initializeResume()
        Rsm.echoFormattedHelp()
    }
}

class ReorderCommand : CliktCommand(name = "reorder", help = "Reorder items in a section") {
    override fun run() {
        ResumeManager.reorderSections()
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
        ResumeManager.addToSection()
    }
}

class RemoveCommand : CliktCommand(name = "remove", help = "Remove content from resume") {

    override fun run() {
        ResumeManager.removeFromSection()
    }
}

class UpdateCommand : CliktCommand(name = "update", help = "Update resume content") {

    override fun run() {
        ResumeManager.updateAtSection()
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
        if (generate)
            ResumeManager.generateLatexFile()
        ResumeManager.compileLateXtoPdf(clean, open)
    }


}


fun main(args: Array<String>) = Rsm
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