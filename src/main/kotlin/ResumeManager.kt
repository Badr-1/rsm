import com.github.kinquirer.KInquirer
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
import models.TechnicalSkills
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

class ResumeManager {
    private val configFile = File(".resume-config.json")
    private val resumeFile = File("resume.tex")
    private val json = Json { prettyPrint = true }

    fun initializeResume() {
        println("🚀 Initializing resume repository...")

        // Initialize git repository
        val git = Git.init().setDirectory(File(".")).call()

        var resumeData = loadConfig()

        if (!configFile.exists()) {
            // Interactive setup
            val personalInfo = collectPersonalInfo()
            val education = collectEducation()
            val experience = collectExperience()
            val projects = collectProjects()
            val skills = collectTechnicalSkills()
            val certifications = collectCertifications()

            resumeData = ResumeData(
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
            println("📝 Edit resume.tex or use 'resume add/remove' commands to modify")
            println("🔄 Use 'resume create <role-name>' to create role-specific versions")
        }


        generateLatexFile(resumeData)


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

    fun addToSection(section: String, target: String?) {
        val git = openGitRepository()
        val targetBranch = target ?: getCurrentBranch(git)

        // Switch to target branch
        git.checkout().setName(targetBranch).call()

        val resumeData = loadConfig()
        val updatedData = when (section.lowercase()) {
            "education" -> resumeData.copy(education = resumeData.education + collectNewEducation())
            "experience" -> resumeData.copy(experience = resumeData.experience + collectNewExperience())
            "projects" -> resumeData.copy(projects = resumeData.projects + collectNewProject())
            "skills" -> resumeData.copy(technicalSkills = updateTechnicalSkills(resumeData.technicalSkills))
            "certifications" -> resumeData.copy(certifications = resumeData.certifications + collectCertifications())
            else -> {
                println("❌ Unknown section: $section")
                return
            }
        }

        saveConfig(updatedData)
        generateLatexFile(updatedData)

        // Commit changes
        git.add().addFilepattern(".").call()
        git.commit().setMessage("Add $section to $targetBranch").call()

        println("✅ Added $section to $targetBranch branch")
    }

    fun removeFromSection(section: String, target: String?) {
        val git = openGitRepository()
        val targetBranch = target ?: getCurrentBranch(git)

        git.checkout().setName(targetBranch).call()

        val resumeData = loadConfig()

        when (section.lowercase()) {
            "education" -> {
                removeWhatFromWhere(
                    git = git,
                    where = section.lowercase(),
                    resumeData = resumeData,
                    message = "Select education entry to remove:",
                    choices = resumeData.education.mapIndexed { index, edu -> "$index: ${edu.degree} at ${edu.institution}" },
                    commitMessage = "Remove education from $targetBranch"
                )
            }

            "experience" -> {
                removeWhatFromWhere(
                    git = git,
                    where = section.lowercase(),
                    resumeData = resumeData,
                    message = "Select experience entry to remove:",
                    choices = resumeData.experience.mapIndexed { index, exp -> "$index: ${exp.position} at ${exp.company}" },
                    commitMessage = "Remove experience from $targetBranch"
                )
            }

            "projects" -> {
                removeWhatFromWhere(
                    git = git,
                    where = section.lowercase(),
                    resumeData = resumeData,
                    message = "Select project to remove: (${resumeData.projects.indices})",
                    choices = resumeData.projects.mapIndexed { index, proj -> "$index: ${proj.name} (${proj.startDate} - ${proj.endDate})" },
                    commitMessage = "Remove project from $targetBranch"
                )
            }

            "certifications" -> {
                removeWhatFromWhere(
                    git = git,
                    where = section.lowercase(),
                    resumeData = resumeData,
                    message = "Select certification to remove:",
                    choices = resumeData.certifications.mapIndexed { index, cert -> "$index: ${cert.name} by ${cert.issuingOrganization}" },
                    commitMessage = "Remove certification from $targetBranch"
                )
            }

            else -> println("❌ Unknown section: $section")
        }
    }

    private fun removeWhatFromWhere(
        git: Git,
        where: String,
        resumeData: ResumeData,
        message: String,
        choices: List<String>,
        commitMessage: String
    ) {
        val index = KInquirer.promptList(message, choices).split(":").first().toInt()
        when(where)
        {
            "education" -> {
                val updated = resumeData.education.toMutableList().apply { removeAt(index) }
                saveAndCommit(resumeData.copy(education = updated), git, commitMessage)
            }
            "experience" -> {
                val updated = resumeData.experience.toMutableList().apply { removeAt(index) }
                saveAndCommit(resumeData.copy(experience = updated), git, commitMessage)
            }
            "projects" -> {
                val updated = resumeData.projects.toMutableList().apply { removeAt(index) }
                saveAndCommit(resumeData.copy(projects = updated), git, commitMessage)
            }
            "certifications" -> {
                val updated = resumeData.certifications.toMutableList().apply { removeAt(index) }
                saveAndCommit(resumeData.copy(certifications = updated), git, commitMessage)
            }
        }


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

    private fun collectEducation(): List<Education> {
        println("\n🎓 Education:")
        val educationList = mutableListOf<Education>()

        do {
            val institution = readLine("Institution: ").escapeLatexSpecialChars()
            val degree = readLine("Degree: ").escapeLatexSpecialChars()
            val location = readLine("Location: ").escapeLatexSpecialChars()
            val graduationDate = readLine("Graduation Date: ").escapeLatexSpecialChars()
            val gpa = readLine("GPA (optional): ").escapeLatexSpecialChars()

            println("Relevant Courses (press Enter on empty line to finish):")
            val courses = mutableListOf<String>()
            do {
                val course = readLine("Course: ").escapeLatexSpecialChars()
                if (course.isNotBlank()) courses.add(course)
            } while (course.isNotBlank())

            educationList.add(
                Education(
                    institution = institution,
                    degree = degree,
                    location = location,
                    graduationDate = graduationDate,
                    gpa = gpa,
                    relevantCourses = courses
                )
            )

            val addMore = KInquirer.promptConfirm("Add another education entry?", default = false)
        } while (addMore)

        return educationList
    }

    private fun collectExperience(): List<Experience> {
        println("\n💼 Experience:")
        val experienceList = mutableListOf<Experience>()

        do {
            val company = readLine("Company: ").escapeLatexSpecialChars()
            val position = readLine("Position: ").escapeLatexSpecialChars()
            val location = readLine("Location: ").escapeLatexSpecialChars()
            val startDate = readLine("Start Date: ").escapeLatexSpecialChars()
            val endDate = readLine("End Date (or 'Present'): ").escapeLatexSpecialChars()

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
                    startDate = startDate,
                    endDate = endDate,
                    bullets = bullets
                )
            )

            val addMore = KInquirer.promptConfirm("Add another experience entry?", default = false)
        } while (addMore)

        return experienceList
    }

    private fun collectProjects(): List<Project> {
        println("\n🚀 Projects:")
        val projectsList = mutableListOf<Project>()

        do {
            val name = readLine("Project Name: ").escapeLatexSpecialChars()
            val technologies = readLine("Technologies: ").escapeLatexSpecialChars()
            val startDate = readLine("Start Date: ").escapeLatexSpecialChars()
            val endDate = readLine("End Date: ").escapeLatexSpecialChars()

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
                    startDate = startDate,
                    endDate = endDate,
                    bullets = bullets
                )
            )

            val addMore = KInquirer.promptConfirm("Add another project entry?", default = false)
        } while (addMore)

        return projectsList
    }

    private fun collectTechnicalSkills(): TechnicalSkills {
        println("\n🔧 Technical Skills:")

        val languages =
            readLine("Languages (comma-separated): ").split(",").map { it.trim().escapeLatexSpecialChars() }
                .filter { it.isNotEmpty() }
        val frameworks =
            readLine("Frameworks (comma-separated): ").split(",").map { it.trim().escapeLatexSpecialChars() }
                .filter { it.isNotEmpty() }
        val developerTools =
            readLine("Technologies (comma-separated): ").split(",").map { it.trim().escapeLatexSpecialChars() }
                .filter { it.isNotEmpty() }
        val libraries =
            readLine("Libraries (comma-separated): ").split(",").map { it.trim().escapeLatexSpecialChars() }
                .filter { it.isNotEmpty() }

        return TechnicalSkills(languages, frameworks, developerTools, libraries)
    }

    private fun collectCertifications(): List<Certification> {
        println("\n🏆 Certifications:")
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

    private fun generateLatexFile(data: ResumeData) {
        val latex = LaTeXGenerator().generate(data)
        resumeFile.writeText(latex)
        println("📄 Generated resume.tex")
    }


    private fun saveConfig(data: ResumeData) {
        val json = json.encodeToString(data)
        configFile.writeText(json)
    }

    private fun loadConfig(): ResumeData {
        return if (configFile.exists()) {
            Json.decodeFromString<ResumeData>(configFile.readText()).let { data ->
                data.copy(
                    education = data.education.filter { it.toString().isNotBlank() },
                    experience = data.experience.filter { it.toString().isNotBlank() },
                    projects = data.projects.filter { it.toString().isNotBlank() },
                    technicalSkills = data.technicalSkills.copy(
                        languages = data.technicalSkills.languages.filter { it.isNotBlank() },
                        frameworks = data.technicalSkills.frameworks.filter { it.isNotBlank() },
                        technologies = data.technicalSkills.technologies.filter { it.isNotBlank() },
                        libraries = data.technicalSkills.libraries.filter { it.isNotBlank() }
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
        print(prompt)
        return readlnOrNull() ?: default
    }

    private fun displayTechnicalSkills(skills: TechnicalSkills) {
        println("\n🔧 Technical Skills:")
        println("Languages: ${skills.languages.joinToString(", ")}")
        println("Frameworks: ${skills.frameworks.joinToString(", ")}")
        println("Technologies: ${skills.technologies.joinToString(", ")}")
        println("Libraries: ${skills.libraries.joinToString(", ")}")
    }

    private fun saveAndCommit(data: ResumeData, git: Git, message: String) {
        saveConfig(data)
        generateLatexFile(data)
        git.add().addFilepattern(".").call()
        git.commit().setMessage(message).call()
        println("✅ $message")
    }

    // Additional helper methods for collecting new entries...
    private fun collectNewEducation(): Education {
        println("Adding new education entry:")
        return collectEducation().first()
    }

    private fun collectNewExperience(): Experience {
        println("Adding new experience entry:")
        return collectExperience().first()
    }

    private fun collectNewProject(): Project {
        println("Adding new project:")
        return collectProjects().first()
    }

    private fun updateTechnicalSkills(current: TechnicalSkills): TechnicalSkills {
        displayTechnicalSkills(current)

        val newSkills = collectTechnicalSkills()

        return TechnicalSkills(
            languages = (current.languages + newSkills.languages).distinct(),
            frameworks = (current.frameworks + newSkills.frameworks).distinct(),
            technologies = (current.technologies + newSkills.technologies).distinct(),
            libraries = (current.libraries + newSkills.libraries).distinct()
        )
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