import com.github.kinquirer.KInquirer
import com.github.kinquirer.components.promptCheckbox
import com.github.kinquirer.components.promptConfirm
import com.github.kinquirer.components.promptInput
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
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

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
        var metaData: String
        when (section) {
            SectionType.EDUCATION -> {
                metaData = "Added new education\n\n"
                val position = KInquirer.promptList(
                    "Select the position to add new education entry to:",
                    resumeData.education.mapIndexed { index, education -> "$index: ${education.degree} at ${education.institution}" } + "${resumeData.education.size}: add to the end"
                ).split(":").first().toInt()

                resumeData.education.addAll(position, collectEducation("add new education entry:").apply {
                    metaData += this.joinToString(
                        prefix = "\n- ",
                        separator = "\n- "
                    ) { "${it.degree} at ${it.institution}" }
                })
            }

            SectionType.EXPERIENCE -> {
                metaData = "Added new experiences\n\n"
                val position = KInquirer.promptList(
                    "Select the position to add new experience entry to:",
                    resumeData.experience.mapIndexed { index, experience -> "$index: ${experience.position} at ${experience.company}" } + "${resumeData.experience.size}: add to the end"
                ).split(":").first().toInt()
                resumeData.experience.addAll(position, collectExperience("add new experience entry:").apply {
                    metaData += this.joinToString(
                        prefix = "\n- ",
                        separator = "\n- "
                    ) { "${it.position} at ${it.company}" }
                })
            }

            SectionType.PROJECTS -> {
                metaData = "Added new projects\n\n"
                val position = KInquirer.promptList(
                    "Select the position to add new project entry to:",
                    resumeData.projects.mapIndexed { index, project -> "$index: ${project.name} (${project.date})" } + "${resumeData.projects.size}: add to the end"
                ).split(":").first().toInt()
                resumeData.projects.addAll(position, collectProjects("add new project entry:").apply {
                    metaData += this.joinToString(
                        prefix = "\n- ",
                        separator = "\n- "
                    ) { "${it.name} (${it.date})" }
                })
            }

            SectionType.TECHNICAL_SKILLS -> {
                metaData = "Added new technical skills\n\n"
                resumeData.technicalSkills += collectTechnicalSkills("add new technical skills:").apply {
                    if (this.languages.isNotEmpty()) {
                        metaData += "Languages: ${this.languages.joinToString(prefix = "\n- ", separator = "\n- ")}\n"
                    }
                    if (this.frameworks.isNotEmpty()) {
                        metaData += "Frameworks: ${this.frameworks.joinToString(prefix = "\n- ", separator = "\n- ")}\n"
                    }
                    if (this.technologies.isNotEmpty()) {
                        metaData += "Technologies: ${
                            this.technologies.joinToString(
                                prefix = "\n- ",
                                separator = "\n- "
                            )
                        }\n"
                    }
                    if (this.libraries.isNotEmpty()) {
                        metaData += "Libraries: ${this.libraries.joinToString(prefix = "\n- ", separator = "\n- ")}"
                    }
                }
            }

            SectionType.CERTIFICATIONS -> {
                metaData = "Added new certifications\n\n"
                val position = KInquirer.promptList(
                    "Select the position to add new certification entry to:",
                    resumeData.certifications.mapIndexed { index, cert -> "$index: ${cert.name} by ${cert.issuingOrganization}" } + "${resumeData.certifications.size}: add to the end"
                ).split(":").first().toInt()
                resumeData.certifications.addAll(position, collectCertifications("add new certification entry:").apply {
                    metaData += this.joinToString(
                        prefix = "\n- ",
                        separator = "\n- "
                    ) { "${it.name} by ${it.issuingOrganization}" }
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
                    choices = resumeData.education.map { edu -> "${edu.degree} at ${edu.institution}" },
                )
            }

            SectionType.EXPERIENCE -> {
                removeWhatFromWhere(
                    git = git,
                    where = section,
                    resumeData = resumeData,
                    message = "Select experience entry to remove:",
                    choices = resumeData.experience.map { exp -> "${exp.position} at ${exp.company}" },
                )
            }

            SectionType.PROJECTS -> {
                removeWhatFromWhere(
                    git = git,
                    where = section,
                    resumeData = resumeData,
                    message = "Select project to remove:",
                    choices = resumeData.projects.map { proj -> "${proj.name} (${proj.date})" },
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
                    choices = resumeData.certifications.map { cert -> "${cert.name} by ${cert.issuingOrganization}" },
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
        val items = KInquirer.promptCheckbox(message, choices, minNumOfSelection = 1, hint = "pick using spacebar")
        var metadata = ""
        when (where) {
            SectionType.EDUCATION -> {
                items.forEach { item -> resumeData.education.apply { removeIf { edu -> "${edu.degree} at ${edu.institution}" == item } } }
                metadata += "Removed education ${items.joinToString(prefix = "\n\n- ", separator = "\n- ")}"
                saveAndCommit(resumeData, git, metadata)
            }

            SectionType.EXPERIENCE -> {
                items.forEach { item -> resumeData.experience.apply { removeIf { exp -> "${exp.position} at ${exp.company}" == item } } }
                metadata += "Removed experience ${items.joinToString(prefix = "\n\n- ", separator = "\n- ")}"
                saveAndCommit(resumeData, git, metadata)
            }

            SectionType.PROJECTS -> {
                items.forEach { item -> resumeData.projects.apply { removeIf { proj -> "${proj.name} (${proj.date})" == item } } }
                metadata += "Removed projects ${items.joinToString(prefix = "\n\n- ", separator = "\n- ")}"
                saveAndCommit(resumeData, git, metadata)
            }

            SectionType.TECHNICAL_SKILLS -> {
                items.forEach { item ->
                    when (val skillType = TechnicalSkillType.valueOf(item.uppercase())) {
                        TechnicalSkillType.LANGUAGES -> {
                            removeTechnicalSkill(
                                git = git,
                                where = skillType,
                                resumeData = resumeData,
                                message = "Select language to remove:",
                                choices = resumeData.technicalSkills.languages,
                            )
                        }

                        TechnicalSkillType.FRAMEWORKS -> {
                            removeTechnicalSkill(
                                git = git,
                                where = skillType,
                                resumeData = resumeData,
                                message = "Select framework to remove:",
                                choices = resumeData.technicalSkills.frameworks,
                            )
                        }

                        TechnicalSkillType.TECHNOLOGIES -> {
                            removeTechnicalSkill(
                                git = git,
                                where = skillType,
                                resumeData = resumeData,
                                message = "Select technology to remove:",
                                choices = resumeData.technicalSkills.technologies,
                            )
                        }

                        TechnicalSkillType.LIBRARIES -> {
                            removeTechnicalSkill(
                                git = git,
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
                items.forEach { item -> resumeData.certifications.apply { removeIf { cert -> "${cert.name} by ${cert.issuingOrganization}" == item } } }
                metadata += "Removed certifications ${items.joinToString(prefix = "\n\n- ", separator = "\n- ")}"
                saveAndCommit(resumeData, git, metadata)
            }
        }


    }

    private fun removeTechnicalSkill(
        git: Git,
        where: TechnicalSkillType,
        resumeData: ResumeData,
        message: String,
        choices: List<String>,
    ) {
        val items = KInquirer.promptCheckbox(
            message,
            choices,
            minNumOfSelection = 1,
            hint = "pick using spacebar"
        )
        var metaData = ""
        when (where) {
            TechnicalSkillType.LANGUAGES -> {
                items.forEach { item -> resumeData.technicalSkills.languages.remove(item) }
                metaData += "Removed languages ${items.joinToString(prefix = "\n\n- ", separator = "\n- ")}"
            }

            TechnicalSkillType.FRAMEWORKS -> {
                items.forEach { item -> resumeData.technicalSkills.frameworks.remove(item) }
                metaData += "Removed frameworks ${items.joinToString(prefix = "\n\n- ", separator = "\n- ")}"
            }

            TechnicalSkillType.TECHNOLOGIES -> {
                items.forEach { item -> resumeData.technicalSkills.technologies.remove(item) }
                metaData += "Removed technologies ${items.joinToString(prefix = "\n\n- ", separator = "\n- ")}"
            }

            TechnicalSkillType.LIBRARIES -> {
                items.forEach { item -> resumeData.technicalSkills.libraries.remove(item) }
                metaData += "Removed libraries ${items.joinToString(prefix = "\n\n- ", separator = "\n- ")}"
            }
        }
        saveAndCommit(resumeData, git, metaData)
    }

    private fun collectPersonalInfo(): PersonalInfo {
        println("\n👤 Personal Information:")
        val name = readLine("Full Name: ").escapeLatexSpecialChars()
        val phone = readLine("Phone Number: ").escapeLatexSpecialChars()
        val email = readLine("Email: ").escapeLatexSpecialChars()
        val linkedin = readLine("LinkedIn URL: ").escapeLatexSpecialChars()
        val github = readLine("GitHub URL: ").escapeLatexSpecialChars()

        return PersonalInfo(name, phone, email, linkedin, github)
    }

    private fun collectEducation(prompt: String): MutableList<Education> {
        println(prompt)
        val educationList = mutableListOf<Education>()

        do {
            val institution = readLine("Institution: ").escapeLatexSpecialChars()
            val degree = readLine("Degree: ").escapeLatexSpecialChars()
            val location = readLine("Location: ").escapeLatexSpecialChars()
            val graduationDate = readLine("Graduation Date: ").escapeLatexSpecialChars()
            val gpa = readLine("GPA (optional): ").escapeLatexSpecialChars()

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
            val company = readLine("Company: ").escapeLatexSpecialChars()
            val position = readLine("Position: ").escapeLatexSpecialChars()
            val location = readLine("Location: ").escapeLatexSpecialChars()
            val date = readLine("Date: ").escapeLatexSpecialChars()

            println("Bullet Points (press Enter on empty line to finish):")
            val bullets = mutableListOf<String>()
            do {
                val bullet = readLine("• ").escapeLatexSpecialChars()
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
            val name = readLine("Project Name: ").escapeLatexSpecialChars()
            val technologies = readLine("Technologies: ").escapeLatexSpecialChars()
            val date = readLine("Date: ").escapeLatexSpecialChars()

            println("Project Details (press Enter on empty line to finish):")
            val bullets = mutableListOf<String>()
            do {
                val bullet = readLine("• ").escapeLatexSpecialChars()
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
            readLine("Languages (comma-separated): ").split(",").map { it.trim().escapeLatexSpecialChars() }
                .filter { it.isNotEmpty() }.toMutableList()
        val frameworks =
            readLine("Frameworks (comma-separated): ").split(",").map { it.trim().escapeLatexSpecialChars() }
                .filter { it.isNotEmpty() }.toMutableList()
        val developerTools =
            readLine("Technologies (comma-separated): ").split(",").map { it.trim().escapeLatexSpecialChars() }
                .filter { it.isNotEmpty() }.toMutableList()
        val libraries =
            readLine("Libraries (comma-separated): ").split(",").map { it.trim().escapeLatexSpecialChars() }
                .filter { it.isNotEmpty() }.toMutableList()

        return TechnicalSkills(languages, frameworks, developerTools, libraries)
    }

    private fun collectCertifications(prompt: String): MutableList<Certification> {
        println(prompt)
        val certificationsList = mutableListOf<Certification>()

        do {
            val name = readLine("Certification Name: ").escapeLatexSpecialChars()
            val issuingOrganization = readLine("Issuing Organization: ").escapeLatexSpecialChars()
            val issueDate = readLine("Issue Date: ").escapeLatexSpecialChars()

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

    private fun readLine(prompt: String, default: String = ""): String {
        return KInquirer.promptInput(prompt, default)
    }

    private fun saveAndCommit(data: ResumeData, git: Git, message: String) {
        saveConfig(data)
        git.add().addFilepattern(".").call()
        git.commit().setMessage(message).call()
        println("✅ $message")
    }

    private fun String.escapeLatexSpecialChars(): String {
        return this.replace("""[&%$#_{}~^\\]""".toRegex()) { matchResult ->
            when (val match = matchResult.value) {
                "&" -> "\\&"
                "%" -> "\\%"
                "$" -> "\\$"
                "#" -> "\\#"
                "_" -> "\\_"
                "{" -> "\\{"
                "}" -> "\\}"
                "~" -> "\\textasciitilde{}"
                "^" -> "\\textasciicircum{}"
                "\\" -> "\\textbackslash{}"
                else -> match
            }
        }
    }
}