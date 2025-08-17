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

            // Interactive setup
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
            // Save configuration
            saveConfig(resumeData)

            // Initial commit
            git.add().addFilepattern(".").call()
            git.commit().setMessage("Initial resume setup").call()

            println("✅ Resume initialized successfully!")
            showHints()

        } else {
            println("📄 Resume configuration already exists.")
            showHints()
        }
    }

    private fun showHints() {
        println("💡 Use 'resume generate' to create the LaTeX file")
        println("💡 Use 'resume create <role>' to create a new role-specific branch")
        println("💡 Use 'resume add/remove' to modify sections in your resume")
        println("💡 Use 'resume compile' to compile the LaTeX file into PDF")
    }

    fun createRoleBranch(roleName: String) {
        try {
            val git = openGitRepository()

            // Create new branch from main
            git.checkout().setName("main").call()
            git.checkout().setCreateBranch(true).setName(roleName).call()

            println("✅ Created branch '$roleName' for role-specific customization")
            println("💡 Use 'resume add/remove --target $roleName' to modify this version")

        } catch (e: Exception) {
            println("❌ Error creating branch: ${e.message}")
        }
    }

    fun addToSection(section: SectionType, target: String?) {
        val git = openGitRepository()
        val targetBranch = target ?: getCurrentBranch(git)

        // Switch to target branch
        git.checkout().setName(targetBranch).call()

        val resumeData = loadConfig()
        when (section) {
            SectionType.EDUCATION -> resumeData.education.addAll(collectEducation("add new education entry:"))
            SectionType.EXPERIENCE -> resumeData.experience.addAll(collectExperience("add new experience entry:"))
            SectionType.PROJECTS -> resumeData.projects.addAll(collectProjects("add new project entry:"))
            SectionType.TECHNICAL_SKILLS -> resumeData.technicalSkills += collectTechnicalSkills("add new technical skills:")
            SectionType.CERTIFICATIONS -> resumeData.certifications.addAll(collectCertifications("add new certification entry:"))
        }

        saveConfig(resumeData)

        // Commit changes
        git.add().addFilepattern(".").call()
        git.commit().setMessage("Add $section to $targetBranch").call()

        println("✅ Added $section to $targetBranch branch")
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
                    choices = resumeData.education.mapIndexed { index, edu -> "$index: ${edu.degree} at ${edu.institution}" },
                    commitMessage = "Remove education from $targetBranch"
                )
            }

            SectionType.EXPERIENCE -> {
                removeWhatFromWhere(
                    git = git,
                    where = section,
                    resumeData = resumeData,
                    message = "Select experience entry to remove:",
                    choices = resumeData.experience.mapIndexed { index, exp -> "$index: ${exp.position} at ${exp.company}" },
                    commitMessage = "Remove experience from $targetBranch"
                )
            }

            SectionType.PROJECTS -> {
                removeWhatFromWhere(
                    git = git,
                    where = section,
                    resumeData = resumeData,
                    message = "Select project to remove: (${resumeData.projects.indices})",
                    choices = resumeData.projects.mapIndexed { index, proj -> "$index: ${proj.name} (${proj.date})" },
                    commitMessage = "Remove project from $targetBranch"
                )
            }

            SectionType.TECHNICAL_SKILLS -> {
                removeWhatFromWhere(
                    git = git,
                    where = section,
                    resumeData = resumeData,
                    message = "Select technical skill to remove:",
                    choices = TechnicalSkillType.entries.mapIndexed { index, skillType -> "$index: ${skillType.name.lowercase()}" },
                    commitMessage = "Remove technical skills from $targetBranch"
                )
            }

            SectionType.CERTIFICATIONS -> {
                removeWhatFromWhere(
                    git = git,
                    where = section,
                    resumeData = resumeData,
                    message = "Select certification to remove:",
                    choices = resumeData.certifications.mapIndexed { index, cert -> "$index: ${cert.name} by ${cert.issuingOrganization}" },
                    commitMessage = "Remove certification from $targetBranch"
                )
            }
        }
    }

    private fun removeWhatFromWhere(
        git: Git,
        where: SectionType,
        resumeData: ResumeData,
        message: String,
        choices: List<String>,
        commitMessage: String
    ) {
        val index = KInquirer.promptList(message, choices).split(":").first().toInt()
        when (where) {
            SectionType.EDUCATION -> {
                resumeData.education.apply { removeAt(index) }
                saveAndCommit(resumeData, git, commitMessage)
            }

            SectionType.EXPERIENCE -> {
                resumeData.experience.apply { removeAt(index) }
                saveAndCommit(resumeData, git, commitMessage)
            }

            SectionType.PROJECTS -> {
                resumeData.projects.apply { removeAt(index) }
                saveAndCommit(resumeData, git, commitMessage)
            }

            SectionType.TECHNICAL_SKILLS -> {
                when (val skillType = TechnicalSkillType.entries[index]) {
                    TechnicalSkillType.LANGUAGES -> {
                        removeTechnicalSkill(
                            git = git,
                            where = skillType,
                            resumeData = resumeData,
                            message = "Select language to remove:",
                            choices = resumeData.technicalSkills.languages,
                            commitMessage = commitMessage
                        )
                    }

                    TechnicalSkillType.FRAMEWORKS -> {
                        removeTechnicalSkill(
                            git = git,
                            where = skillType,
                            resumeData = resumeData,
                            message = "Select framework to remove:",
                            choices = resumeData.technicalSkills.frameworks,
                            commitMessage = commitMessage
                        )
                    }

                    TechnicalSkillType.TECHNOLOGIES -> {
                        removeTechnicalSkill(
                            git = git,
                            where = skillType,
                            resumeData = resumeData,
                            message = "Select technology to remove:",
                            choices = resumeData.technicalSkills.technologies,
                            commitMessage = commitMessage
                        )
                    }

                    TechnicalSkillType.LIBRARIES -> {
                        removeTechnicalSkill(
                            git = git,
                            where = skillType,
                            resumeData = resumeData,
                            message = "Select library to remove:",
                            choices = resumeData.technicalSkills.libraries,
                            commitMessage = commitMessage
                        )
                    }
                }
            }

            SectionType.CERTIFICATIONS -> {
                resumeData.certifications.apply { removeAt(index) }
                saveAndCommit(resumeData, git, commitMessage)
            }
        }


    }

    private fun removeTechnicalSkill(
        git: Git,
        where: TechnicalSkillType,
        resumeData: ResumeData,
        message: String,
        choices: List<String>,
        commitMessage: String
    ) {
        val items = KInquirer.promptCheckbox(
            message,
            choices,
            minNumOfSelection = 1,
            hint = "pick using spacebar"
        )
        when (where) {
            TechnicalSkillType.LANGUAGES -> {
                items.forEach { item -> resumeData.technicalSkills.languages.remove(item) }
            }

            TechnicalSkillType.FRAMEWORKS -> {
                items.forEach { item -> resumeData.technicalSkills.frameworks.remove(item) }
            }

            TechnicalSkillType.TECHNOLOGIES -> {
                items.forEach { item -> resumeData.technicalSkills.technologies.remove(item) }
            }

            TechnicalSkillType.LIBRARIES -> {
                items.forEach { item -> resumeData.technicalSkills.libraries.remove(item) }
            }
        }
        saveAndCommit(resumeData, git, commitMessage)
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