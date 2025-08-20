package models

import com.github.kinquirer.KInquirer
import com.github.kinquirer.components.promptCheckbox
import com.github.kinquirer.components.promptConfirm
import kotlinx.serialization.Serializable
import utils.Utils.readLineOptional
import utils.Utils.readLineRequired
import utils.Utils.toCommitMessage
import java.util.Collections.emptyList

enum class SectionType {
    EDUCATION,
    EXPERIENCE,
    PROJECTS,
    TECHNICAL_SKILLS,
    CERTIFICATIONS
}

enum class TechnicalSkillType {
    LANGUAGES,
    FRAMEWORKS,
    TECHNOLOGIES,
    LIBRARIES
}

@Serializable
data class PersonalInfo(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val linkedin: String = "",
    val github: String = ""
)

@Serializable
data class Education(
    var institution: String = "",
    var degree: String = "",
    var location: String = "",
    var graduationDate: String = "",
    var gpa: String = "",
) {
    companion object {
        fun collect(prompt: String): MutableList<Education> {
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
    }

    fun update(): String {
        var metaData = ""
        val newInstitution = readLineOptional("Institution ($institution): ")
        val newDegree = readLineOptional("Degree ($degree): ")
        val newLocation = readLineOptional("Location ($location): ")
        val newGraduationDate = readLineOptional("Graduation Date ($graduationDate): ")
        val newGpa = readLineOptional("GPA ($gpa): ")

        if (newInstitution.isNotBlank()) {
            metaData += "Institution updated from '$institution' to '$newInstitution'.\n"
            institution = newInstitution
        }
        if (newDegree.isNotBlank()) {
            metaData += "Degree updated from '$degree' to '$newDegree'.\n"
            degree = newDegree
        }
        if (newLocation.isNotBlank()) {
            metaData += "Location updated from '$location' to '$newLocation'.\n"
            location = newLocation
        }
        if (newGraduationDate.isNotBlank()) {
            metaData += "Graduation Date updated from '$graduationDate' to '$newGraduationDate'.\n"
            graduationDate = newGraduationDate
        }
        if (newGpa.isNotBlank()) {
            metaData += "GPA updated from '$gpa' to '$newGpa'.\n"
            gpa = newGpa
        }
        return metaData
    }

    override fun toString(): String {
        return "$degree at $institution"
    }
}

@Serializable
data class Experience(
    var company: String = "",
    var position: String = "",
    var location: String = "",
    var date: String = "",
    var bullets: List<String> = emptyList()
) {
    companion object {
        fun collect(prompt: String): MutableList<Experience> {
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
    }

    fun update(): String {
        var metaData = ""
        val newCompany = readLineOptional("Company ($company): ")
        val newPosition = readLineOptional("Position ($position): ")
        val newLocation = readLineOptional("Location ($location): ")
        val newDate = readLineOptional("Date ($date): ")
        val newBullets = mutableListOf<String>()

        newBullets += KInquirer.promptCheckbox(
            "Old Bullet Points (choose what to keep)",
            choices = bullets,
            hint = "pick using spacebar"
        )
        println("Add More (press Enter on empty line to finish):")
        do {
            val bullet = readLineOptional("• ")
            if (bullet.isNotBlank()) newBullets.add(bullet)
        } while (bullet.isNotBlank())

        if (newCompany.isNotEmpty()) {
            metaData += "Company updated from '$company' to '$newCompany'.\n"
            company = newCompany
        }
        if (newPosition.isNotEmpty()) {
            metaData += "Position updated from '$position' to '$newPosition'.\n"
            position = newPosition
        }
        if (newLocation.isNotEmpty()) {
            metaData += "Location updated from '$location' to '$newLocation'.\n"
            location = newLocation
        }
        if (newDate.isNotEmpty()) {
            metaData += "Date updated from '$date' to '$newDate'.\n"
            date = newDate
        }
        bullets = newBullets
        metaData += newBullets.joinToString(prefix = "Bullet Points:\n", separator = "\n") { it }

        return metaData
    }

    override fun toString(): String {
        return "$position at $company (${date})"
    }
}

@Serializable
data class Project(
    var name: String = "",
    var technologies: String = "",
    var date: String = "",
    var bullets: List<String> = emptyList()
) {
    companion object {
        fun collect(prompt: String): MutableList<Project> {
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
    }

    fun update(): String {
        var metaData = ""
        val newName: String = readLineOptional("Project Name ($name): ")
        val newTechnologies: String = readLineOptional("Technologies ($technologies): ")
        val newDate: String = readLineOptional("Date ($date):")

        val newBullets = mutableListOf<String>()
        newBullets += KInquirer.promptCheckbox(
            "Old Project Details (choose what to keep)",
            choices = bullets,
            hint = "pick using spacebar"
        )
        println("Add More (press Enter on empty line to finish):")
        do {
            val bullet = readLineOptional("• ")
            if (bullet.isNotBlank()) newBullets.add(bullet)
        } while (bullet.isNotBlank())

        if (newName.isNotEmpty()) {
            metaData += "Name updated from '$name' to '$newName'.\n"
            name = newName
        }
        if (newTechnologies.isNotEmpty()) {
            metaData += "Technologies updated from '$technologies' to '$newTechnologies'.\n"
            technologies = newTechnologies
        }
        if (newDate.isNotEmpty()) {
            metaData += "Date updated from '$date' to '$newDate'.\n"
            date = newDate
        }
        bullets = newBullets
        metaData += newBullets.joinToString(prefix = "Project Details:\n", separator = "\n") { it }

        return metaData
    }

    override fun toString(): String {
        return "$name (${date})"
    }
}

@Serializable
data class TechnicalSkills(
    var languages: MutableList<String> = emptyList(),
    var frameworks: MutableList<String> = emptyList(),
    var technologies: MutableList<String> = emptyList(),
    var libraries: MutableList<String> = emptyList()
) {
    companion object {
        fun collect(prompt: String): TechnicalSkills {
            println(prompt)

            val languages =
                readLineOptional("Languages (comma-separated): ").split(",").map { it.trim() }
                    .filter { it.isNotEmpty() }.toMutableList()
            val frameworks =
                readLineOptional("Frameworks (comma-separated): ").split(",").map { it.trim() }
                    .filter { it.isNotEmpty() }.toMutableList()
            val technologies =
                readLineOptional("Technologies (comma-separated): ").split(",").map { it.trim() }
                    .filter { it.isNotEmpty() }.toMutableList()
            val libraries =
                readLineOptional("Libraries (comma-separated): ").split(",").map { it.trim() }
                    .filter { it.isNotEmpty() }.toMutableList()

            return TechnicalSkills(languages, frameworks, technologies, libraries)
        }
    }

    fun remove(items: List<TechnicalSkillType>): String {
        var metaData = ""
        items.forEach { item ->
            when (item) {
                TechnicalSkillType.LANGUAGES -> {
                    val removedLanguages = KInquirer.promptCheckbox(
                        message = "Select language to remove:",
                        choices = languages,
                        hint = "pick using spacebar"
                    )
                    languages.removeAll(removedLanguages)
                    metaData += removedLanguages.toCommitMessage("Removed languages")
                }

                TechnicalSkillType.FRAMEWORKS -> {
                    val removedFrameworks = KInquirer.promptCheckbox(
                        message = "Select framework to remove:",
                        choices = frameworks,
                        hint = "pick using spacebar"
                    )
                    frameworks.removeAll(removedFrameworks)
                    metaData += removedFrameworks.toCommitMessage("Removed frameworks")
                }

                TechnicalSkillType.TECHNOLOGIES -> {
                    val removedTechnologies = KInquirer.promptCheckbox(
                        message = "Select technology to remove:",
                        choices = technologies,
                        hint = "pick using spacebar"
                    )
                    technologies.removeAll(removedTechnologies)
                    metaData += removedTechnologies.toCommitMessage("Removed technologies")
                }

                TechnicalSkillType.LIBRARIES -> {
                    val removedLibraries = KInquirer.promptCheckbox(
                        message = "Select library to remove:",
                        choices = libraries,
                        hint = "pick using spacebar"
                    )
                    libraries.removeAll(removedLibraries)
                    metaData += removedLibraries.toCommitMessage("Removed libraries")
                }
            }
        }
        return metaData
    }

    fun update(items: List<TechnicalSkillType>): String {
        var metadata = ""

        items.forEach { item ->
            when (item) {
                TechnicalSkillType.LANGUAGES -> {
                    KInquirer.promptCheckbox(
                        "Current Languages (choose what to keep)",
                        choices = languages,
                        hint = "pick using spacebar"
                    ).let { selectedLanguages ->
                        if (selectedLanguages.isNotEmpty()) {
                            languages.clear()
                            languages.addAll(selectedLanguages)
                        }
                    }
                    languages =
                        readLineOptional("New Languages (comma-separated): ").split(",").map { it.trim() }
                            .filter { it.isNotEmpty() }.toMutableList()
                    metadata += languages.toCommitMessage("Updated Languages")
                }

                TechnicalSkillType.FRAMEWORKS -> {
                    KInquirer.promptCheckbox(
                        "Current Frameworks (choose what to keep)",
                        choices = frameworks,
                        hint = "pick using spacebar"
                    )

                        .let { selectedFrameworks ->
                            if (selectedFrameworks.isNotEmpty()) {
                                frameworks.clear()
                                frameworks.addAll(selectedFrameworks)
                            }
                        }

                    frameworks =
                        readLineOptional("New Frameworks (comma-separated): ").split(",").map { it.trim() }
                            .filter { it.isNotEmpty() }.toMutableList()
                    metadata += frameworks.toCommitMessage("Update Frameworks")
                }

                TechnicalSkillType.TECHNOLOGIES -> {
                    KInquirer.promptCheckbox(
                        "Current Technologies (choose what to keep)",
                        choices = technologies,
                        hint = "pick using spacebar"
                    )

                        .let { selectedFrameworks ->
                            if (selectedFrameworks.isNotEmpty()) {
                                technologies.clear()
                                technologies.addAll(selectedFrameworks)
                            }
                        }

                    technologies =
                        readLineOptional("New Technologies (comma-separated): ").split(",").map { it.trim() }
                            .filter { it.isNotEmpty() }.toMutableList()

                    metadata += technologies.toCommitMessage("Updated Technologies")
                }

                TechnicalSkillType.LIBRARIES -> {
                    KInquirer.promptCheckbox(
                        "Current Libraries (choose what to keep)",
                        choices = libraries,
                        hint = "pick using spacebar"
                    )

                        .let { selectedLibraries ->
                            if (selectedLibraries.isNotEmpty()) {
                                libraries.clear()
                                libraries.addAll(selectedLibraries)
                            }
                        }

                    libraries =
                        readLineOptional("New Libraries (comma-separated): ").split(",").map { it.trim() }
                            .filter { it.isNotEmpty() }.toMutableList()
                    metadata += libraries.toCommitMessage("Updated Libraries")
                }
            }
        }

        return metadata
    }

    operator fun plus(other: TechnicalSkills): TechnicalSkills {
        return TechnicalSkills(
            languages = (languages + other.languages).distinct().toMutableList(),
            frameworks = (frameworks + other.frameworks).distinct().toMutableList(),
            technologies = (technologies + other.technologies).distinct().toMutableList(),
            libraries = (libraries + other.libraries).distinct().toMutableList()
        )
    }
}

@Serializable
data class Certification(
    var name: String = "",
    var issuingOrganization: String = "",
    var issueDate: String = "",
) {
    companion object {
        fun collect(prompt: String): MutableList<Certification> {
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
    }

    fun update(): String {
        var metaData = ""
        val newName = readLineOptional("Certification Name ($name): ")
        val newIssuingOrganization = readLineOptional("Issuing Organization ($issuingOrganization): ")
        val newIssueDate = readLineOptional("Issue Date ($issueDate): ")
        if (newName.isNotEmpty()) {
            metaData += "Certification Name update from '$name' to '$newName'.\n"
            name = newName
        }
        if (newIssuingOrganization.isNotEmpty()) {
            metaData += "Certification Name update from '$issuingOrganization' to '$newIssuingOrganization'.\n"
            issuingOrganization = newIssuingOrganization
        }
        if (newIssueDate.isNotEmpty()) {
            metaData += "Certification Name update from '$issueDate' to '$newIssueDate'.\n"
            issueDate = newIssueDate
        }

        return metaData
    }

    override fun toString(): String {
        return "$name by $issuingOrganization ($issueDate)"
    }
}


@Serializable
data class ResumeData(
    var personalInfo: PersonalInfo = PersonalInfo(),
    var education: MutableList<Education> = emptyList(),
    var experience: MutableList<Experience> = emptyList(),
    var projects: MutableList<Project> = emptyList(),
    var technicalSkills: TechnicalSkills = TechnicalSkills(),
    var certifications: MutableList<Certification> = emptyList()
)
