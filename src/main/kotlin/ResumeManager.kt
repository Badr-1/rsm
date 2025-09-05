import com.github.kinquirer.KInquirer
import com.github.kinquirer.components.promptCheckbox
import com.github.kinquirer.components.promptCheckboxObject
import com.github.kinquirer.components.promptConfirm
import com.github.kinquirer.components.promptList
import com.github.kinquirer.components.promptOrderableListObject
import com.github.kinquirer.core.Choice
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import models.Certification
import models.Certification.Companion.reorganize
import models.Education
import models.Experience
import models.PersonalInfo
import models.Project
import models.ResumeData
import models.SectionType
import models.TechnicalSkills
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.EmptyCommitException
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import utils.Utils.promptSection
import utils.Utils.toCommitMessage

val configFile = File("resume-config.json")
val resumeFile = File("resume.tex")
val ignoreFile = File(".gitignore")
val pdfFile = File("resume.pdf")

object ResumeManager {
    private val json = Json { prettyPrint = true }

    fun initializeResume() {
        val git = Git.init().setDirectory(File(".")).call()

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
                    .map { Choice(it.displayName, it) })


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

                    SectionType.CERTIFICATIONS -> resumeData.certifications =
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

    fun updateAtSection(section: SectionType, target: String) {
        val git = openGitRepository()
        git.checkout().setName(target).call()

        val resumeData = loadConfig()
        when (section) {
            SectionType.PERSONAL_INFO -> {
                resumeData.personalInfo.update()
                saveAndCommit(resumeData, git, "Update Personal Information")
            }

            SectionType.EDUCATION -> updateWhatAtWhere(
                git,
                section,
                resumeData,
                "Select education entry to update:",
                resumeData.education.map { it.toString() })

            SectionType.EXPERIENCE -> updateWhatAtWhere(
                git,
                section,
                resumeData,
                "Select experience entry to update:",
                resumeData.experience.map { it.toString() }
            )

            SectionType.PROJECTS -> updateWhatAtWhere(
                git,
                section,
                resumeData,
                "Select project entry to update:",
                resumeData.projects.map { it.toString() }
            )

            SectionType.TECHNICAL_SKILLS -> updateWhatAtWhere(
                git = git,
                where = section,
                resumeData = resumeData,
                message = "Select technical skill to update:",
                choices = resumeData.technicalSkills.entries.keys.toList(),
            )

            SectionType.CERTIFICATIONS -> updateWhatAtWhere(
                git,
                section,
                resumeData,
                "Select certification entry to update:",
                resumeData.certifications.map { it.toString() }
            )
        }
    }

    fun updateWhatAtWhere(
        git: Git,
        where: SectionType,
        resumeData: ResumeData,
        message: String,
        choices: List<String>
    ) {
        val itemsToUpdate =
            KInquirer.promptCheckbox(message, choices, hint = "pick using spacebar")
        var metaData = ""
        when (where) {
            SectionType.PERSONAL_INFO -> {/*handled earlier*/
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
        saveAndCommit(resumeData, git, metaData)
    }

    fun addToSection(section: SectionType, target: String?) {
        val git = openGitRepository()
        val targetBranch = target ?: getCurrentBranch(git)
        git.checkout().setName(targetBranch).call()

        val resumeData = loadConfig()
        var metaData = ""
        when (section) {
            SectionType.PERSONAL_INFO -> {/*can't add to this section*/
            }

            SectionType.EDUCATION -> {
                val position = KInquirer.promptList(
                    "Select the position to add new education entry to:",
                    resumeData.education.mapIndexed { index, education -> "$index: $education" } + "${resumeData.education.size}: add to the end"
                ).split(":").first().toInt()

                resumeData.education.addAll(position, Education.collect("add new education entry:").apply {
                    metaData += this.toCommitMessage("Added new education\n\n")
                })
            }

            SectionType.EXPERIENCE -> {
                val position = KInquirer.promptList(
                    "Select the position to add new experience entry to:",
                    resumeData.experience.mapIndexed { index, experience -> "$index: $experience" } + "${resumeData.experience.size}: add to the end"
                ).split(":").first().toInt()

                resumeData.experience.addAll(position, Experience.collect("add new experience entry:").apply {
                    metaData += this.toCommitMessage("Added new experiences\n\n")
                })
            }

            SectionType.PROJECTS -> {
                metaData = "\n\n"
                val position = KInquirer.promptList(
                    "Select the position to add new project entry to:",
                    resumeData.projects.mapIndexed { index, project -> "$index: $project" } + "${resumeData.projects.size}: add to the end"
                ).split(":").first().toInt()
                resumeData.projects.addAll(position, Project.collect("add new project entry:").apply {
                    metaData += this.toCommitMessage("Added new projects\n\n")
                })
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
                val position = KInquirer.promptList(
                    "Select the position to add new certification entry to:",
                    resumeData.certifications.mapIndexed { index, certification -> "$index: $certification" } + "${resumeData.certifications.size}: add to the end"
                ).split(":").first().toInt()
                resumeData.certifications.addAll(position, Certification.collect("add new certification entry:").apply {
                    metaData += this.toCommitMessage("Added new certifications\n\n")
                })
                resumeData.certifications.reorganize()
            }
        }

        saveAndCommit(resumeData, git, metaData)
        git.checkout().setName("main").call()
    }

    fun removeFromSection(section: SectionType, target: String?) {
        val git = openGitRepository()
        val targetBranch = target ?: getCurrentBranch(git)

        git.checkout().setName(targetBranch).call()

        val resumeData = loadConfig()

        when (section) {
            SectionType.PERSONAL_INFO -> {/*should not remove from this section*/
            }

            SectionType.EDUCATION -> {
                removeWhatFromWhere(
                    git = git,
                    where = section,
                    resumeData = resumeData,
                    message = "Select education entry to remove:",
                    choices = resumeData.education.map { education -> education.toString() },
                )
            }

            SectionType.EXPERIENCE -> {
                removeWhatFromWhere(
                    git = git,
                    where = section,
                    resumeData = resumeData,
                    message = "Select experience entry to remove:",
                    choices = resumeData.experience.map { experience -> experience.toString() },
                )
            }

            SectionType.PROJECTS -> {
                removeWhatFromWhere(
                    git = git,
                    where = section,
                    resumeData = resumeData,
                    message = "Select project to remove:",
                    choices = resumeData.projects.map { project -> project.toString() },
                )
            }

            SectionType.TECHNICAL_SKILLS -> {
                removeWhatFromWhere(
                    git = git,
                    where = section,
                    resumeData = resumeData,
                    message = "Select technical skill to remove:",
                    choices = resumeData.technicalSkills.entries.keys.toList(),
                )
            }

            SectionType.CERTIFICATIONS -> {
                removeWhatFromWhere(
                    git = git,
                    where = section,
                    resumeData = resumeData,
                    message = "Select certification to remove:",
                    choices = resumeData.certifications.map { certification -> certification.toString() },
                )
            }
        }
        git.checkout().setName("main").call()
    }

    private fun removeWhatFromWhere(
        git: Git,
        where: SectionType,
        resumeData: ResumeData,
        message: String,
        choices: List<String>,
    ) {
        val removedItems =
            KInquirer.promptCheckbox(message, choices, minNumOfSelection = 1, hint = "pick using spacebar")
        var metadata = ""
        when (where) {
            SectionType.PERSONAL_INFO -> {/*handled earlier*/
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
        saveAndCommit(resumeData, git, metadata)
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

    private fun getCurrentBranch(git: Git): String {
        return git.repository.branch
    }

    private fun saveAndCommit(data: ResumeData, git: Git, message: String) {
        saveConfig(data)
        git.add().addFilepattern(".").call()
        try {
            git.commit().setAllowEmpty(false).setMessage(message).call()
        } catch (_: EmptyCommitException) {
            println("❌ No changes to commit.")
            return
        }
        println("✅ $message")
    }

    fun reorderSections(target: String, section: Boolean) {
        val git = openGitRepository()
        git.checkout().setName(target).call()

        val resumeData = loadConfig()
        val orderedSections = KInquirer.promptOrderableListObject(
            message = "choose the order of sections",
            choices = SectionType.entries.filter { !it.isFixed }.map { Choice(it.displayName, it) }.toMutableList(),
            hint = "use arrow keys to move up/down, spacebar to select to reorder"
        )
        if (section) {
            val section = promptSection(
                "What section do you want to reorder?",
                SectionType.entries.filter { !it.isFixed }.map { Choice(it.displayName, it) })

            reorderSection(resumeData, section)
        }
        resumeData.orderedSections = orderedSections.ifEmpty { SectionType.entries.filter { !it.isFixed } }
        saveAndCommit(resumeData, git, "Reordered sections")
        git.checkout().setName("main").call()
    }

    fun reorderSection(resumeData: ResumeData, section: SectionType) {
        when (section) {
            SectionType.PERSONAL_INFO -> {}
            SectionType.EDUCATION -> {
                resumeData.reorderEducation()
            }

            SectionType.EXPERIENCE -> {
                resumeData.reorderExperience()
                resumeData.experience.forEach { experience ->
                    val reorderBulletsConfirm = KInquirer.promptConfirm(
                        "Do you want to reorder bullets for experience: $experience ?",
                        default = false
                    )
                    if (reorderBulletsConfirm) {
                        experience.reorderBullets()
                    }

                }
            }

            SectionType.PROJECTS -> {
                resumeData.reorderProjects()
                resumeData.projects.forEach { project ->
                    val reorderBulletsConfirm = KInquirer.promptConfirm(
                        "Do you want to reorder bullets for project: $project ?",
                        default = false
                    )
                    if (reorderBulletsConfirm) {
                        project.reorderBullets()
                    }

                }
            }

            SectionType.TECHNICAL_SKILLS -> {
                resumeData.reorderTechnicalSkills()
            }

            SectionType.CERTIFICATIONS -> {
                resumeData.reorderCertifications()
            }
        }
    }

}