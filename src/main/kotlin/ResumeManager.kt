import com.github.kinquirer.KInquirer
import com.github.kinquirer.components.promptCheckbox
import com.github.kinquirer.components.promptConfirm
import com.github.kinquirer.components.promptList
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import models.Certification
import models.Education
import models.Experience
import models.PersonalInfo
import models.Project
import models.ResumeData
import models.SectionType
import models.TechnicalSkillType
import models.TechnicalSkills
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.EmptyCommitException
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import utils.Utils.readLineOptional
import utils.Utils.readLineRequired
import utils.Utils.toCommitMessage

val configFile = File("resume-config.json")
val resumeFile = File("resume.tex")
val ignoreFile = File(".gitignore")
val pdfFile = File("resume.pdf")

class ResumeManager {
    private val json = Json { prettyPrint = true }

    fun initializeResume() {
        val git = Git.init().setDirectory(File(".")).call()

        if (!configFile.exists()) {

            ignoreFile.writeText(
                """
            # Ignore generated files
            *.tex
            *.pdf
            """.trimIndent()
            )


            val personalInfo = collectPersonalInfo()
            val education = collectEducation("\n🎓 Education:")
            val experience = collectExperience("\n💼 Experience:")
            val projects = collectProjects("\n🚀 Projects:")
            val skills = collectTechnicalSkills("\n🔧 Technical Skills:")
            val certifications = collectCertifications("\n🏆 Certifications:")

            val resumeData = ResumeData(
                personalInfo = personalInfo,
                education = education,
                experience = experience,
                projects = projects,
                technicalSkills = skills,
                certifications = certifications
            )

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

    fun addToSection(section: SectionType, target: String?) {
        val git = openGitRepository()
        val targetBranch = target ?: getCurrentBranch(git)
        git.checkout().setName(targetBranch).call()

        val resumeData = loadConfig()
        var metaData = ""
        when (section) {
            SectionType.EDUCATION -> {
                val position = KInquirer.promptList(
                    "Select the position to add new education entry to:",
                    resumeData.education.mapIndexed { index, education -> "$index: $education" } + "${resumeData.education.size}: add to the end"
                ).split(":").first().toInt()

                resumeData.education.addAll(position, collectEducation("add new education entry:").apply {
                    metaData += this.toCommitMessage("Added new education\n\n")
                })
            }

            SectionType.EXPERIENCE -> {
                val position = KInquirer.promptList(
                    "Select the position to add new experience entry to:",
                    resumeData.experience.mapIndexed { index, experience -> "$index: $experience" } + "${resumeData.experience.size}: add to the end"
                ).split(":").first().toInt()

                resumeData.experience.addAll(position, collectExperience("add new experience entry:").apply {
                    metaData += this.toCommitMessage("Added new experiences\n\n")
                })
            }

            SectionType.PROJECTS -> {
                metaData = "\n\n"
                val position = KInquirer.promptList(
                    "Select the position to add new project entry to:",
                    resumeData.projects.mapIndexed { index, project -> "$index: $project" } + "${resumeData.projects.size}: add to the end"
                ).split(":").first().toInt()
                resumeData.projects.addAll(position, collectProjects("add new project entry:").apply {
                    metaData += this.toCommitMessage("Added new projects\n\n")
                })
            }

            SectionType.TECHNICAL_SKILLS -> {
                metaData = "Added new technical skills\n\n"
                resumeData.technicalSkills += collectTechnicalSkills("add new technical skills:").apply {
                    metaData += languages.toCommitMessage("Languages") +
                            frameworks.toCommitMessage("Frameworks") +
                            technologies.toCommitMessage("Technologies") +
                            libraries.toCommitMessage("Libraries")
                }
            }

            SectionType.CERTIFICATIONS -> {
                val position = KInquirer.promptList(
                    "Select the position to add new certification entry to:",
                    resumeData.certifications.mapIndexed { index, certification -> "$index: $certification" } + "${resumeData.certifications.size}: add to the end"
                ).split(":").first().toInt()
                resumeData.certifications.addAll(position, collectCertifications("add new certification entry:").apply {
                    metaData += this.toCommitMessage("Added new certifications\n\n")
                })
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
                    choices = TechnicalSkillType.entries.map { skillType -> skillType.name.lowercase() },
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
                removedItems.forEach { item ->
                    when (val skillType = TechnicalSkillType.valueOf(item.uppercase())) {
                        TechnicalSkillType.LANGUAGES -> {
                            metadata += removeTechnicalSkill(
                                where = skillType,
                                resumeData = resumeData,
                                message = "Select language to remove:",
                                choices = resumeData.technicalSkills.languages,
                            )
                        }

                        TechnicalSkillType.FRAMEWORKS -> {
                            metadata += removeTechnicalSkill(
                                where = skillType,
                                resumeData = resumeData,
                                message = "Select framework to remove:",
                                choices = resumeData.technicalSkills.frameworks,
                            )
                        }

                        TechnicalSkillType.TECHNOLOGIES -> {
                            metadata += removeTechnicalSkill(
                                where = skillType,
                                resumeData = resumeData,
                                message = "Select technology to remove:",
                                choices = resumeData.technicalSkills.technologies,
                            )
                        }

                        TechnicalSkillType.LIBRARIES -> {
                            metadata += removeTechnicalSkill(
                                where = skillType,
                                resumeData = resumeData,
                                message = "Select library to remove:",
                                choices = resumeData.technicalSkills.libraries,
                            )
                        }
                    }
                }
            }

            SectionType.CERTIFICATIONS -> {
                resumeData.certifications.removeIf { it.toString() in removedItems }
                metadata += removedItems.toCommitMessage("Removed certifications\n\n")
            }
        }
        saveAndCommit(resumeData, git, metadata)
    }

    private fun removeTechnicalSkill(
        where: TechnicalSkillType,
        resumeData: ResumeData,
        message: String,
        choices: List<String>,
    ): String {
        val removedSkills = KInquirer.promptCheckbox(
            message,
            choices,
            hint = "pick using spacebar"
        )
        var metaData = ""
        when (where) {
            TechnicalSkillType.LANGUAGES -> {
                resumeData.technicalSkills.languages.removeIf { it in removedSkills }
                metaData += removedSkills.toCommitMessage("Removed languages")
            }

            TechnicalSkillType.FRAMEWORKS -> {
                resumeData.technicalSkills.frameworks.removeIf { it in removedSkills }
                metaData += removedSkills.toCommitMessage("Removed frameworks")
            }

            TechnicalSkillType.TECHNOLOGIES -> {
                resumeData.technicalSkills.technologies.removeIf { it in removedSkills }
                metaData += removedSkills.toCommitMessage("Removed technologies")
            }

            TechnicalSkillType.LIBRARIES -> {
                resumeData.technicalSkills.libraries.removeIf { it in removedSkills }
                metaData += removedSkills.toCommitMessage("Removed libraries")
            }
        }
        return metaData
    }

    private fun collectPersonalInfo(): PersonalInfo {
        println("\n👤 Personal Information:")
        val name = readLineRequired("Full Name: ")
        val phone = readLineRequired("Phone Number: ")
        val email = readLineRequired("Email: ")
        val linkedin = readLineOptional("LinkedIn URL: ")
        val github = readLineOptional("GitHub URL: ")

        return PersonalInfo(name, phone, email, linkedin, github)
    }

    private fun collectEducation(prompt: String): MutableList<Education> {
        println(prompt)
        val educationList = mutableListOf<Education>()

        do {
            val institution = readLineRequired("Institution: ")
            val degree = readLineRequired("Degree: ")
            val location = readLineRequired("Location: ")
            val graduationDate = readLineRequired("Graduation Date: ")
            val gpa = readLineOptional("GPA: ")

            educationList.add(
                Education(
                    institution = institution,
                    degree = degree,
                    location = location,
                    graduationDate = graduationDate,
                    gpa = gpa,
                )
            )

            val addMore = KInquirer.promptConfirm("Add another education entry?", default = false)
        } while (addMore)

        return educationList
    }

    private fun collectExperience(prompt: String): MutableList<Experience> {
        println(prompt)
        val experienceList = mutableListOf<Experience>()

        do {
            val company = readLineRequired("Company: ")
            val position = readLineRequired("Position: ")
            val location = readLineRequired("Location: ")
            val date = readLineRequired("Date: ")

            println("Bullet Points (press Enter on empty line to finish):")
            val bullets = mutableListOf<String>()
            do {
                val bullet = readLineOptional("• ")
                if (bullet.isNotBlank()) bullets.add(bullet)
            } while (bullet.isNotBlank())

            experienceList.add(
                Experience(
                    company = company,
                    position = position,
                    location = location,
                    date = date,
                    bullets = bullets
                )
            )

            val addMore = KInquirer.promptConfirm("Add another experience entry?", default = false)
        } while (addMore)

        return experienceList
    }

    private fun collectProjects(prompt: String): MutableList<Project> {
        println(prompt)
        val projectsList = mutableListOf<Project>()

        do {
            val name = readLineRequired("Project Name: ")
            val technologies = readLineRequired("Technologies: ")
            val date = readLineRequired("Date: ")

            println("Project Details (press Enter on empty line to finish):")
            val bullets = mutableListOf<String>()
            do {
                val bullet = readLineOptional("• ")
                if (bullet.isNotBlank()) bullets.add(bullet)
            } while (bullet.isNotBlank())

            projectsList.add(
                Project(
                    name = name,
                    technologies = technologies,
                    date = date,
                    bullets = bullets
                )
            )

            val addMore = KInquirer.promptConfirm("Add another project entry?", default = false)
        } while (addMore)

        return projectsList
    }

    private fun collectTechnicalSkills(prompt: String): TechnicalSkills {
        println(prompt)

        val languages =
            readLineRequired("Languages (comma-separated): ").split(",").map { it.trim() }
                .filter { it.isNotEmpty() }.toMutableList()
        val frameworks =
            readLineRequired("Frameworks (comma-separated): ").split(",").map { it.trim() }
                .filter { it.isNotEmpty() }.toMutableList()
        val developerTools =
            readLineRequired("Technologies (comma-separated): ").split(",").map { it.trim() }
                .filter { it.isNotEmpty() }.toMutableList()
        val libraries =
            readLineRequired("Libraries (comma-separated): ").split(",").map { it.trim() }
                .filter { it.isNotEmpty() }.toMutableList()

        return TechnicalSkills(languages, frameworks, developerTools, libraries)
    }

    private fun collectCertifications(prompt: String): MutableList<Certification> {
        println(prompt)
        val certificationsList = mutableListOf<Certification>()

        do {
            val name = readLineRequired("Certification Name: ")
            val issuingOrganization = readLineRequired("Issuing Organization: ")
            val issueDate = readLineRequired("Issue Date: ")

            certificationsList.add(
                Certification(
                    name = name,
                    issuingOrganization = issuingOrganization,
                    issueDate = issueDate
                )
            )

            val addMore = KInquirer.promptConfirm("Add another certification entry?", default = false)
        } while (addMore)

        return certificationsList
    }

    fun generateLatexFile() {
        if (configFile.exists()) {
            val latex = LaTeXGenerator().generate(loadConfig())
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
                    technicalSkills = data.technicalSkills.copy(
                        languages = data.technicalSkills.languages.filter { it.isNotBlank() }.toMutableList(),
                        frameworks = data.technicalSkills.frameworks.filter { it.isNotBlank() }.toMutableList(),
                        technologies = data.technicalSkills.technologies.filter { it.isNotBlank() }.toMutableList(),
                        libraries = data.technicalSkills.libraries.filter { it.isNotBlank() }.toMutableList()
                    )
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

}