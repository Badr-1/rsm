import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import models.Education
import models.Experience
import models.PersonalInfo
import models.Project
import models.ResumeData
import models.TechnicalSkills
import java.io.File
import java.net.URL
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import utils.FileUtils

class ResumeManager {
    private val configFile = File(".resume-config.json")
    private val resumeFile = File("resume.tex")
    private val jakeTemplateUrl = "https://raw.githubusercontent.com/jakegut/resume/master/resume.tex"

    fun initializeResume() {
        println("🚀 Initializing resume repository...")

        // Initialize git repository
        val git = Git.init().setDirectory(File(".")).call()

        // Download Jake's template
        downloadTemplate()

        var resumeData = loadConfig()

        if (!configFile.exists()) {
            // Interactive setup
            val personalInfo = collectPersonalInfo()
            val education = collectEducation()
            val experience = collectExperience()
            val projects = collectProjects()
            val skills = collectTechnicalSkills()

            resumeData = ResumeData(
                personalInfo = personalInfo,
                education = education,
                experience = experience,
                projects = projects,
                technicalSkills = skills
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
                displayEducationList(resumeData.education)
                val index = readLine("Enter the number of education entry to remove: ")?.toIntOrNull()
                if (index != null && index > 0 && index <= resumeData.education.size) {
                    val updated = resumeData.education.toMutableList()
                    updated.removeAt(index - 1)
                    saveAndCommit(resumeData.copy(education = updated), git, "Remove education from $targetBranch")
                }
            }

            "experience" -> {
                displayExperienceList(resumeData.experience)
                val index = readLine("Enter the number of experience entry to remove: ")?.toIntOrNull()
                if (index != null && index > 0 && index <= resumeData.experience.size) {
                    val updated = resumeData.experience.toMutableList()
                    updated.removeAt(index - 1)
                    saveAndCommit(resumeData.copy(experience = updated), git, "Remove experience from $targetBranch")
                }
            }

            "projects" -> {
                displayProjectsList(resumeData.projects)
                val index = readLine("Enter the number of project to remove: ")?.toIntOrNull()
                if (index != null && index > 0 && index <= resumeData.projects.size) {
                    val updated = resumeData.projects.toMutableList()
                    updated.removeAt(index - 1)
                    saveAndCommit(resumeData.copy(projects = updated), git, "Remove project from $targetBranch")
                }
            }

            else -> println("❌ Unknown section: $section")
        }
    }

    private fun downloadTemplate() {
        val templateContent = URL(jakeTemplateUrl).readText()
        resumeFile.writeText(templateContent)
        if (FileUtils.downloadFile(jakeTemplateUrl, resumeFile))
            println("📥 Downloaded Jake's resume template")
        else
            println("❌ Failed to download Jake's resume template")
    }

    private fun collectPersonalInfo(): PersonalInfo {
        println("\n👤 Personal Information:")
        val name = readLine("Full Name: ") ?: ""
        val phone = readLine("Phone Number: ") ?: ""
        val email = readLine("Email: ") ?: ""
        val linkedin = readLine("LinkedIn URL: ") ?: ""
        val github = readLine("GitHub URL: ") ?: ""

        return PersonalInfo(name, phone, email, linkedin, github)
    }

    private fun collectEducation(): List<Education> {
        println("\n🎓 Education:")
        val educationList = mutableListOf<Education>()

        do {
            val institution = readLine("Institution: ") ?: ""
            val degree = readLine("Degree: ") ?: ""
            val location = readLine("Location: ") ?: ""
            val graduationDate = readLine("Graduation Date: ") ?: ""
            val gpa = readLine("GPA (optional): ") ?: ""

            println("Relevant Courses (press Enter on empty line to finish):")
            val courses = mutableListOf<String>()
            do {
                val course = readLine("Course: ")
                if (!course.isNullOrBlank()) courses.add(course)
            } while (!course.isNullOrBlank())

            educationList.add(
                Education(
                    id = generateId(),
                    institution = institution,
                    degree = degree,
                    location = location,
                    graduationDate = graduationDate,
                    gpa = gpa,
                    relevantCourses = courses
                )
            )

            val addMore = readLine("Add another education entry? (y/n): ")
        } while (addMore?.lowercase() == "y")

        return educationList
    }

    private fun collectExperience(): List<Experience> {
        println("\n💼 Experience:")
        val experienceList = mutableListOf<Experience>()

        do {
            val company = readLine("Company: ") ?: ""
            val position = readLine("Position: ") ?: ""
            val location = readLine("Location: ") ?: ""
            val startDate = readLine("Start Date: ") ?: ""
            val endDate = readLine("End Date (or 'Present'): ") ?: ""

            println("Bullet Points (press Enter on empty line to finish):")
            val bullets = mutableListOf<String>()
            do {
                val bullet = readLine("• ")
                if (!bullet.isNullOrBlank()) bullets.add(bullet)
            } while (!bullet.isNullOrBlank())

            experienceList.add(
                Experience(
                    id = generateId(),
                    company = company,
                    position = position,
                    location = location,
                    startDate = startDate,
                    endDate = endDate,
                    bullets = bullets
                )
            )

            val addMore = readLine("Add another experience entry? (y/n): ")
        } while (addMore?.lowercase() == "y")

        return experienceList
    }

    private fun collectProjects(): List<Project> {
        println("\n🚀 Projects:")
        val projectsList = mutableListOf<Project>()

        do {
            val name = readLine("Project Name: ") ?: ""
            val technologies = readLine("Technologies: ") ?: ""
            val startDate = readLine("Start Date: ") ?: ""
            val endDate = readLine("End Date: ") ?: ""

            println("Project Details (press Enter on empty line to finish):")
            val bullets = mutableListOf<String>()
            do {
                val bullet = readLine("• ")
                if (!bullet.isNullOrBlank()) bullets.add(bullet)
            } while (!bullet.isNullOrBlank())

            projectsList.add(
                Project(
                    id = generateId(),
                    name = name,
                    technologies = technologies,
                    startDate = startDate,
                    endDate = endDate,
                    bullets = bullets
                )
            )

            val addMore = readLine("Add another project? (y/n): ")
        } while (addMore?.lowercase() == "y")

        return projectsList
    }

    private fun collectTechnicalSkills(): TechnicalSkills {
        println("\n🔧 Technical Skills:")

        val languages = readLine("Languages (comma-separated): ")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val frameworks = readLine("Frameworks (comma-separated): ")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val developerTools =
            readLine("Developer Tools (comma-separated): ")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val libraries = readLine("Libraries (comma-separated): ")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        return TechnicalSkills(languages, frameworks, developerTools, libraries)
    }

    private fun generateLatexFile(data: ResumeData) {
        val latex = LaTeXGenerator().generate(data)
        resumeFile.writeText(latex)
        println("📄 Generated resume.tex")
    }

    private fun saveConfig(data: ResumeData) {
        val json = Json.encodeToString(data)
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
                        developerTools = data.technicalSkills.developerTools.filter { it.isNotBlank() },
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

    private fun generateId(): String {
        return System.currentTimeMillis().toString()
    }

    private fun readLine(prompt: String): String? {
        print(prompt)
        return readlnOrNull()
    }

    // Helper methods for displaying lists and other operations...
    private fun displayEducationList(education: List<Education>) {
        println("\n🎓 Education:")
        education.forEachIndexed { index, edu ->
            println("${index + 1}. ${edu.degree} at ${edu.institution}")
        }
    }

    private fun displayExperienceList(experience: List<Experience>) {
        println("\n💼 Experience:")
        experience.forEachIndexed { index, exp ->
            println("${index + 1}. ${exp.position} at ${exp.company}")
        }
    }

    private fun displayProjectsList(projects: List<Project>) {
        println("\n🚀 Projects:")
        projects.forEachIndexed { index, project ->
            println("${index + 1}. ${project.name}")
        }
    }

    private fun displayTechnicalSkills(skills: TechnicalSkills) {
        println("\n🔧 Technical Skills:")
        println("Languages: ${skills.languages.joinToString(", ")}")
        println("Frameworks: ${skills.frameworks.joinToString(", ")}")
        println("Developer Tools: ${skills.developerTools.joinToString(", ")}")
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
            developerTools = (current.developerTools + newSkills.developerTools).distinct(),
            libraries = (current.libraries + newSkills.libraries).distinct()
        )
    }
}