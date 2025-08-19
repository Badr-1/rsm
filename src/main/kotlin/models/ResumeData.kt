package models

import kotlinx.serialization.Serializable
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
    val institution: String = "",
    val degree: String = "",
    val location: String = "",
    val graduationDate: String = "",
    val gpa: String = "",
) {
    override fun toString(): String {
        return "$degree at $institution"
    }
}

@Serializable
data class Experience(
    val company: String = "",
    val position: String = "",
    val location: String = "",
    val date: String = "",
    val bullets: List<String> = emptyList()
) {
    override fun toString(): String {
        return "$position at $company (${date})"
    }
}

@Serializable
data class Project(
    val name: String = "",
    val technologies: String = "",
    val date: String = "",
    val bullets: List<String> = emptyList()
) {
    override fun toString(): String {
        return "$name (${date})"
    }
}

@Serializable
data class TechnicalSkills(
    val languages: MutableList<String> = emptyList(),
    val frameworks: MutableList<String> = emptyList(),
    val technologies: MutableList<String> = emptyList(),
    val libraries: MutableList<String> = emptyList()
)
{
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
    val name: String = "",
    val issuingOrganization: String = "",
    val issueDate: String = "",
) {
    override fun toString(): String {
        return "$name by $issuingOrganization ($issueDate)"
    }
}


@Serializable
data class ResumeData(
    val personalInfo: PersonalInfo = PersonalInfo(),
    val education: MutableList<Education> = emptyList(),
    val experience: MutableList<Experience> = emptyList(),
    val projects: MutableList<Project> = emptyList(),
    var technicalSkills: TechnicalSkills = TechnicalSkills(),
    val certifications: MutableList<Certification> = emptyList()
)
