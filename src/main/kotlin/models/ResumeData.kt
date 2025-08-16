package models

import kotlinx.serialization.Serializable
import java.util.Collections.emptyList

enum class SectionType {
    EDUCATION,
    EXPERIENCE,
    PROJECTS,
    CERTIFICATIONS
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
)

@Serializable
data class Experience(
    val company: String = "",
    val position: String = "",
    val location: String = "",
    val date: String = "",
    val bullets: List<String> = emptyList()
)

@Serializable
data class Project(
    val name: String = "",
    val technologies: String = "",
    val date: String = "",
    val bullets: List<String> = emptyList()
)

@Serializable
data class TechnicalSkills(
    val languages: List<String> = emptyList(),
    val frameworks: List<String> = emptyList(),
    val technologies: List<String> = emptyList(),
    val libraries: List<String> = emptyList()
)

@Serializable
data class Certification(
    val name: String = "",
    val issuingOrganization: String = "",
    val issueDate: String = "",
)


@Serializable
data class ResumeData(
    val personalInfo: PersonalInfo = PersonalInfo(),
    val education: MutableList<Education> = emptyList(),
    val experience: MutableList<Experience> = emptyList(),
    val projects: MutableList<Project> = emptyList(),
    val technicalSkills: TechnicalSkills = TechnicalSkills(),
    val certifications: MutableList<Certification> = emptyList()
)
