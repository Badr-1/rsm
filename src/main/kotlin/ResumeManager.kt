import com.github.kinquirer.KInquirer
import com.github.kinquirer.components.promptCheckbox
import com.github.kinquirer.components.promptCheckboxObject
import com.github.kinquirer.components.promptConfirm
import com.github.kinquirer.components.promptOrderableListObject
import com.github.kinquirer.core.Choice
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import models.Certification
import models.Certification.Companion.reorder
import models.Certification.Companion.reorganize
import models.Education
import models.Experience
import models.PersonalInfo
import models.Project
import models.ResumeData
import models.SectionType
import models.TechnicalSkills
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.EmptyCommitException
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import utils.FileUtils
import utils.Utils.compilePdf
import utils.Utils.promptSection
import utils.Utils.promptSectionAndTargetBranch
import utils.Utils.promptTargetBranch
import utils.Utils.reorder
import utils.Utils.toCommitMessage
import utils.configFile
import utils.ignoreFile
import utils.pdfFile
import utils.resumeFile
import java.awt.Desktop
import java.io.File

object ResumeManager {
    private val json = Json { prettyPrint = true }

    fun initializeResume() {
        val git = Git.init().setDirectory(File(".")).setInitialBranch("main").call()

        if (!configFile.exists()) {
            ignoreFile.writeText(
                """
            # Ignore generated files
            *
            !.gitignore
            !${configFile.name}
                """.trimIndent()
            )

            val resumeData = ResumeData()
            resumeData.personalInfo = PersonalInfo.collect()

            val sectionsToFill = KInquirer.promptCheckboxObject(
                message = "choose sections you want to fill",
                choices = SectionType.entries.filter { !it.isFixed }
                    .map { Choice(it.displayName, it) }
            )

            sectionsToFill.forEach { sectionToFill ->
                when (sectionToFill) {
                    SectionType.PERSONAL_INFO -> {}
                    SectionType.EDUCATION -> resumeData.education = Education.collect("\n🎓 Education:")
                    SectionType.EXPERIENCE -> resumeData.experience = Experience.collect("\n💼 Experience:")
                    SectionType.PROJECTS -> resumeData.projects = Project.collect("\n🚀 Projects:")
                    SectionType.TECHNICAL_SKILLS -> {
                        val isCategorized =
                            KInquirer.promptConfirm("Do you want to categorize your technical skills?", default = true)
                        resumeData.technicalSkills =
                            TechnicalSkills.collect("\n🔧 Technical Skills:", isCategorized)
                    }

                    SectionType.CERTIFICATIONS ->
                        resumeData.certifications =
                            Certification.collect("\n🏆 Certifications:")
                }
            }

            val orderedSections = KInquirer.promptOrderableListObject(
                message = "choose the order of sections",
                choices = SectionType.entries.filter { !it.isFixed }.map { Choice(it.displayName, it) }.toMutableList(),
                hint = "use arrow keys to move up/down, spacebar to select to reorder"
            )
            resumeData.orderedSections = orderedSections.ifEmpty { SectionType.entries.filter { !it.isFixed } }

            saveConfig(resumeData)

            git.add().addFilepattern(".").call()
            git.commit().setMessage("Initial resume setup").call()

            println("✅ Resume initialized successfully!")
        } else {
            println("📄 Resume configuration already exists.")
        }
    }

    fun createRoleBranch(roleName: String) {
        try {
            val git = openGitRepository()

            git.checkout().setName("main").call()
            git.checkout().setCreateBranch(true).setName(roleName).call()

            println("✅ Created branch '$roleName' for role-specific customization")
            git.checkout().setName("main").call()
        } catch (e: Exception) {
            println("❌ Error creating branch: ${e.message}")
        }
    }

    fun addToSection() {
        val (section, target) = promptSectionAndTargetBranch(
            "What section do you want to add content to?"
        ) { !it.isFixed }

        val git = openGitRepository()
        git.checkout().setName(target).call()

        val resumeData = loadConfig()
        var metaData = ""
        when (section) {
            SectionType.PERSONAL_INFO -> { /*can't add to this section*/
            }

            SectionType.EDUCATION -> {
                resumeData.education.addAll(
                    0,
                    Education.collect("add new education entry:").apply {
                        metaData += this.toCommitMessage("Added new education\n\n")
                    }
                )
                println("New Entries are Added at the top of the Section")
                if (resumeData.education.size > 1 && KInquirer.promptConfirm("Do You want to change order?", false)) {
                    resumeData.education.reorder(false)
                }
            }

            SectionType.EXPERIENCE -> {
                resumeData.experience.addAll(
                    0,
                    Experience.collect("add new experience entry:").apply {
                        metaData += this.toCommitMessage("Added new experiences\n\n")
                    }
                )
                println("New Entries are Added at the top of the Section")
                if (resumeData.experience.size > 1 && KInquirer.promptConfirm("Do You want to change order?", false)) {
                    resumeData.experience.reorder(false)
                }
            }

            SectionType.PROJECTS -> {
                metaData = "\n\n"
                resumeData.projects.addAll(
                    0,
                    Project.collect("add new project entry:").apply {
                        metaData += this.toCommitMessage("Added new projects\n\n")
                    }
                )
                println("New Entries are Added at the top of the Section")
                if (resumeData.projects.size > 1 && KInquirer.promptConfirm("Do You want to change order?", false)) {
                    resumeData.projects.reorder(false)
                }
            }

            SectionType.TECHNICAL_SKILLS -> {
                metaData = "Added new technical skills\n\n"
                resumeData.technicalSkills += TechnicalSkills.collect(
                    "add new technical skills:",
                    !resumeData.technicalSkills.isFlattened()
                ).apply {
                    entries.forEach { (category, skills) ->
                        metaData += skills.toCommitMessage(category)
                    }
                }
            }

            SectionType.CERTIFICATIONS -> {
                resumeData.certifications.addAll(
                    0,
                    Certification.collect("add new certification entry:").apply {
                        metaData += this.toCommitMessage("Added new certifications\n\n")
                    }
                )
                println("New Entries are Added at the top of the Section")
                if (resumeData.certifications.size > 1 && KInquirer.promptConfirm(
                        "Do You want to change order?",
                        false
                    )
                ) {
                    resumeData.certifications.reorder(false)
                }
            }
        }
        saveConfig(resumeData)
        commit(metaData)
        git.checkout().setName("main").call()
    }

    fun updateAtSection() {
        val (section, target) = promptSectionAndTargetBranch("What section do you want to update?") { true }

        val git = openGitRepository()
        git.checkout().setName(target).call()

        val resumeData = loadConfig()
        when (section) {
            SectionType.PERSONAL_INFO -> {
                resumeData.personalInfo.update()
                saveConfig(resumeData)
                commit("Update Personal Information")
            }

            SectionType.EDUCATION -> updateWhatAtWhere(
                section,
                resumeData,
                "Select education entry to update:",
                resumeData.education.map { it.toString() }
            )

            SectionType.EXPERIENCE -> updateWhatAtWhere(
                section,
                resumeData,
                "Select experience entry to update:",
                resumeData.experience.map { it.toString() }
            )

            SectionType.PROJECTS -> updateWhatAtWhere(
                section,
                resumeData,
                "Select project entry to update:",
                resumeData.projects.map { it.toString() }
            )

            SectionType.TECHNICAL_SKILLS -> updateWhatAtWhere(
                where = section,
                resumeData = resumeData,
                message = "Select technical skill to update:",
                choices = resumeData.technicalSkills.entries.keys.toList()
            )

            SectionType.CERTIFICATIONS -> updateWhatAtWhere(
                section,
                resumeData,
                "Select certification entry to update:",
                resumeData.certifications.map { it.toString() }
            )
        }
    }

    fun updateWhatAtWhere(
        where: SectionType,
        resumeData: ResumeData,
        message: String,
        choices: List<String>
    ) {
        val itemsToUpdate =
            KInquirer.promptCheckbox(message, choices, hint = "pick using spacebar")
        var metaData = ""
        when (where) {
            SectionType.PERSONAL_INFO -> { /*handled earlier*/
            }

            SectionType.EDUCATION -> {
                metaData += "Updated education\n\n"
                resumeData.education.filter { it.toString() in itemsToUpdate }.forEach { metaData += it.update() }
            }

            SectionType.EXPERIENCE -> {
                metaData += "Updated experience\n\n"
                resumeData.experience.filter { it.toString() in itemsToUpdate }.forEach { metaData += it.update() }
            }

            SectionType.PROJECTS -> {
                metaData += "Updated projects\n\n"
                resumeData.projects.filter { it.toString() in itemsToUpdate }.forEach { metaData += it.update() }
            }

            SectionType.TECHNICAL_SKILLS -> {
                metaData += "Updated technical skills\n\n"
                metaData += resumeData.technicalSkills.update(itemsToUpdate)
            }

            SectionType.CERTIFICATIONS -> {
                metaData += "Updated certifications\n\n"
                resumeData.certifications.filter { it.toString() in itemsToUpdate }.forEach { metaData += it.update() }
                resumeData.certifications.reorganize()
            }
        }
        saveConfig(resumeData)
        commit(metaData)
    }

    fun removeFromSection() {
        val (section, target) = promptSectionAndTargetBranch(
            "What section do you want to remove content from?"
        ) { !it.isFixed }
        val git = openGitRepository()

        git.checkout().setName(target).call()

        val resumeData = loadConfig()

        when (section) {
            SectionType.PERSONAL_INFO -> { /*should not remove from this section*/
            }

            SectionType.EDUCATION -> {
                removeWhatFromWhere(
                    where = section,
                    resumeData = resumeData,
                    message = "Select education entry to remove:",
                    choices = resumeData.education.map { education -> education.toString() }
                )
            }

            SectionType.EXPERIENCE -> {
                removeWhatFromWhere(
                    where = section,
                    resumeData = resumeData,
                    message = "Select experience entry to remove:",
                    choices = resumeData.experience.map { experience -> experience.toString() }
                )
            }

            SectionType.PROJECTS -> {
                removeWhatFromWhere(
                    where = section,
                    resumeData = resumeData,
                    message = "Select project to remove:",
                    choices = resumeData.projects.map { project -> project.toString() }
                )
            }

            SectionType.TECHNICAL_SKILLS -> {
                removeWhatFromWhere(
                    where = section,
                    resumeData = resumeData,
                    message = "Select technical skill to remove:",
                    choices = resumeData.technicalSkills.entries.keys.toList()
                )
            }

            SectionType.CERTIFICATIONS -> {
                removeWhatFromWhere(
                    where = section,
                    resumeData = resumeData,
                    message = "Select certification to remove:",
                    choices = resumeData.certifications.map { certification -> certification.toString() }
                )
            }
        }
        git.checkout().setName("main").call()
    }

    private fun removeWhatFromWhere(
        where: SectionType,
        resumeData: ResumeData,
        message: String,
        choices: List<String>
    ) {
        val removedItems =
            KInquirer.promptCheckbox(message, choices, minNumOfSelection = 1, hint = "pick using spacebar")
        var metadata = ""
        when (where) {
            SectionType.PERSONAL_INFO -> { /*handled earlier*/
            }

            SectionType.EDUCATION -> {
                resumeData.education.removeIf { it.toString() in removedItems }
                metadata += removedItems.toCommitMessage("Removed education\n\n")
            }

            SectionType.EXPERIENCE -> {
                resumeData.experience.removeIf { it.toString() in removedItems }
                metadata += removedItems.toCommitMessage("Removed experience\n\n")
            }

            SectionType.PROJECTS -> {
                resumeData.projects.removeIf { it.toString() in removedItems }
                metadata += removedItems.toCommitMessage("Removed projects\n\n")
            }

            SectionType.TECHNICAL_SKILLS -> {
                metadata += "Removed technical skills\n\n"
                metadata += resumeData.technicalSkills.remove(removedItems)
            }

            SectionType.CERTIFICATIONS -> {
                resumeData.certifications.removeIf { it.toString() in removedItems }
                metadata += removedItems.toCommitMessage("Removed certifications\n\n")
            }
        }
        saveConfig(resumeData)
        commit(metadata)
    }

    fun reorderSections() {
        val target = promptTargetBranch()

        val git = openGitRepository()
        git.checkout().setName(target).call()

        val resumeData = loadConfig()
        val orderedSections = KInquirer.promptOrderableListObject(
            message = "choose the order of sections",
            choices = resumeData.orderedSections.map { Choice(it.displayName, it) }.toMutableList(),
            hint = "use arrow keys to move up/down, spacebar to select to reorder"
        )
        resumeData.orderedSections = orderedSections.ifEmpty { SectionType.entries.filter { !it.isFixed } }

        val confirmSectionReorder = KInquirer.promptConfirm(
            "Do you want to reorder any section?"
        )
        if (confirmSectionReorder) {
            val section = promptSection(
                "What section do you want to reorder?",
                false
            ) { !it.isFixed }

            reorderSection(resumeData, section)
        }
        saveConfig(resumeData)
        commit("Reordered sections")
        git.checkout().setName("main").call()
    }

    fun reorderSection(resumeData: ResumeData, section: SectionType) {
        when (section) {
            SectionType.PERSONAL_INFO -> {}
            SectionType.EDUCATION -> {
                resumeData.education.reorder()
            }

            SectionType.EXPERIENCE -> {
                resumeData.experience.reorder()
            }

            SectionType.PROJECTS -> {
                resumeData.projects.reorder()
            }

            SectionType.TECHNICAL_SKILLS -> {
                resumeData.technicalSkills.reorder()
            }

            SectionType.CERTIFICATIONS -> {
                resumeData.certifications.reorder()
            }
        }
    }

    fun generateLatexFile() {
        if (configFile.exists()) {
            val latex = LaTeXGenerator.generate(loadConfig())
            resumeFile.writeText(latex)
            println("📄 Generated resume.tex")
        } else {
            println("❌ No configuration found. Please run 'resume init' first.")
        }
    }

    private fun saveConfig(data: ResumeData) {
        val json = json.encodeToString(data)
        configFile.writeText(json)
    }

    private fun loadConfig(): ResumeData {
        return if (configFile.exists()) {
            Json.decodeFromString<ResumeData>(configFile.readText()).let { data ->
                data.copy(
                    education = data.education.filter { it.toString().isNotBlank() }.toMutableList(),
                    experience = data.experience.filter { it.toString().isNotBlank() }.toMutableList(),
                    projects = data.projects.filter { it.toString().isNotBlank() }.toMutableList(),
                    technicalSkills = data.technicalSkills
                )
            }
        } else {
            ResumeData()
        }
    }

    private fun openGitRepository(): Git {
        val repo =
            FileRepositoryBuilder()
                .setGitDir(File(".git"))
                .setWorkTree(File(System.getProperty("user.dir")))
                .setInitialBranch("main")
                .build()
        return Git(repo)
    }

    private fun commit(message: String) {
        val git = openGitRepository()
        git.add().addFilepattern(".").call()
        try {
            git.commit().setAllowEmpty(false).setMessage(message).call()
        } catch (_: EmptyCommitException) {
            println("❌ No changes to commit.")
            return
        }
        println("✅ $message")
    }

    fun compileLateXtoPdf(clean: Boolean, open: Boolean) {
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
            val (process, exitCode) = compilePdf()

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
