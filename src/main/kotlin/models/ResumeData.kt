package models

import com.github.kinquirer.KInquirer
import com.github.kinquirer.components.promptCheckbox
import com.github.kinquirer.components.promptConfirm
import com.github.kinquirer.components.promptOrderableListObject
import com.github.kinquirer.core.Choice
import kotlinx.serialization.Serializable
import utils.Utils.readLineOptional
import utils.Utils.readLineRequired
import utils.Utils.toCommitMessage
import java.util.Collections.emptyList

enum class SectionType(val displayName: String, val isFixed: Boolean = false) {
    PERSONAL_INFO("Personal Info", true),
    EDUCATION("Education"),
    EXPERIENCE("Experience"),
    PROJECTS("Projects"),
    TECHNICAL_SKILLS("Technical Skills"),
    CERTIFICATIONS("Certifications"),
}


@Serializable
data class PersonalInfo(
    var name: String = "",
    var phone: String = "",
    var email: String = "",
    var linkedin: String = "",
    var github: String = ""
) {
    companion object {
        fun collect(): PersonalInfo {
            println("\n👤 Personal Information:")
            val name = readLineRequired("Full Name: ")
            val phone = readLineRequired("Phone Number: ")
            val email = readLineRequired("Email: ")
            val linkedin = readLineOptional("LinkedIn URL: ")
            val github = readLineOptional("GitHub URL: ")

            return PersonalInfo(name, phone, email, linkedin, github)
        }
    }

    fun update() {
        name = readLineOptional("Full Name ($name): ", name)
        phone = readLineOptional("Phone Number ($phone): ", phone)
        email = readLineOptional("Email ($email): ", email)
        linkedin = readLineOptional("LinkedIn URL${if (linkedin.isNotEmpty()) " ($linkedin): " else ": "}", linkedin)
        github = readLineOptional("GitHub URL${if (github.isNotEmpty()) " ($github): " else ": "}", github)
    }

}

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

    fun reorderBullets() {
        bullets = KInquirer.promptOrderableListObject(
            "Reorder Bullet Points:",
            bullets.map { Choice(it, it) }.toMutableList(),
            hint = "move using arrow keys"
        )
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

    fun reorderBullets() {
        bullets = KInquirer.promptOrderableListObject(
            "Reorder Bullet Points:",
            bullets.map { Choice(it, it) }.toMutableList(),
            hint = "move using arrow keys"
        )
    }
}

@Serializable
data class TechnicalSkills(
    var entries: MutableMap<String, MutableList<String>> = mutableMapOf()
) {
    companion object {
        fun collect(prompt: String, isCategorized: Boolean): TechnicalSkills {
            println(prompt)
            val entries = mutableMapOf<String, MutableList<String>>()

            if (isCategorized) {
                do {
                    val category = readLineRequired("Category Name (e.g., Languages, Frameworks): ")
                    val skills = readLineRequired("Skills in $category (comma-separated): ")
                        .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                    entries[category] = skills
                } while (KInquirer.promptConfirm("Add another category?", default = false))
            } else {
                val skills = readLineRequired("Technical Skills (comma-separated): ")
                    .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                entries["Technical Skills"] = skills
            }
            return TechnicalSkills(entries)
        }

    }

    fun remove(items: List<String>): String {
        var metaData = ""
        items.forEach { item ->

            val skillsToRemove = KInquirer.promptCheckbox(
                message = "Select skills to remove from $item:",
                choices = entries.getOrDefault(item, mutableListOf()),
                hint = "pick using spacebar"
            )
            entries[item]?.removeAll(skillsToRemove)
            if (entries[item]?.isEmpty() == true) {
                entries.remove(item)
            }
            metaData += skillsToRemove.toCommitMessage("Removed from $item")
        }
        return metaData
    }

    fun update(items: List<String>): String {
        var metadata = ""
        var newName: String
        items.forEach { item ->
            newName = readLineOptional(
                "Category Name ($item): ",
                item,
                validation = { it.isNotEmpty() && !entries.containsKey(it) }
            )

            if (newName.isNotEmpty() && newName != item) {
                entries[newName] = entries[item] ?: mutableListOf()
                entries.remove(item)
                metadata += "Category name updated from '$item' to '$newName'.\n"
            }
        }
        if (!isFlattened()) {
            KInquirer.promptConfirm("Do you want to flatten your technical skills?", default = false)
                .let { confirm ->
                    if (confirm) {
                        flatten()
                        metadata += "Flattened technical skills into a single category."
                    }
                }
        } else {
            KInquirer.promptConfirm("Do you want to categorize your technical skills?", default = false)
                .let { confirm ->
                    if (confirm) {
                        categorize()
                        metadata += "Categorized technical skills."
                    }
                }
        }

        return metadata
    }

    private fun flatten() {
        if (entries.size > 1) {
            val allSkills = entries.values.flatten().distinct()
            entries.clear()
            entries["Technical Skills"] = allSkills.toMutableList()
        }
    }

    private fun categorize() {
        val allSkills = entries.values.flatten().distinct().toMutableList()
        entries.clear()
        do {
            val category = readLineRequired("Category Name (e.g., Languages, Frameworks): ")
            val skills = KInquirer.promptCheckbox(
                message = "Select skills to add to $category:",
                choices = allSkills,
                minNumOfSelection = 1,
                hint = "pick using spacebar"
            )
            entries[category] = skills.toMutableList()
            allSkills.removeAll(skills)

        } while (allSkills.isNotEmpty() && KInquirer.promptConfirm(
                "Add another category?",
                default = false
            )
        )
        if (allSkills.isNotEmpty()) {
            entries["Uncategorized"] = allSkills
        }
    }

    operator fun plus(other: TechnicalSkills): TechnicalSkills {
        other.entries.forEach { (category, skills) ->
            if (entries.containsKey(category)) {
                entries[category]?.addAll(skills)
            } else {
                entries[category] = skills.distinct().toMutableList()
            }
        }
        return this
    }

    fun isFlattened(): Boolean {
        return entries.size == 1 && entries.containsKey("Technical Skills")
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
    var orderedSections: List<SectionType> = SectionType.entries.filter { !it.isFixed },
    var personalInfo: PersonalInfo = PersonalInfo(),
    var education: MutableList<Education> = emptyList(),
    var experience: MutableList<Experience> = emptyList(),
    var projects: MutableList<Project> = emptyList(),
    var technicalSkills: TechnicalSkills = TechnicalSkills(),
    var certifications: MutableList<Certification> = emptyList()
) {
    fun reorderEducation() {
        education = KInquirer.promptOrderableListObject(
            "Reorder Education Entries:",
            education.map { Choice(it.toString(), it) }.toMutableList(),
            hint = "move using arrow keys"
        ) as MutableList<Education>
    }

    fun reorderExperience() {
        experience = KInquirer.promptOrderableListObject(
            "Reorder Experience Entries:",
            experience.map { Choice(it.toString(), it) }.toMutableList(),
            hint = "move using arrow keys"
        ) as MutableList<Experience>
    }

    fun reorderProjects() {
        projects = KInquirer.promptOrderableListObject(
            "Reorder Project Entries:",
            projects.map { Choice(it.toString(), it) }.toMutableList(),
            hint = "move using arrow keys"
        ) as MutableList<Project>
    }
}
