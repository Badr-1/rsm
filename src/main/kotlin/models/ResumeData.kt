package models

import kotlinx.serialization.Serializable

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
    val relevantCourses: List<String> = emptyList()
)

@Serializable
data class Experience(
    val company: String = "",
    val position: String = "",
    val location: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val bullets: List<String> = emptyList()
)

@Serializable
data class Project(
    val name: String = "",
    val technologies: String = "",
    val startDate: String = "",
    val endDate: String = "",
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
    val expirationDate: String = "",
)


@Serializable
data class ResumeData(
    val personalInfo: PersonalInfo = PersonalInfo(),
    val education: List<Education> = emptyList(),
    val experience: List<Experience> = emptyList(),
    val projects: List<Project> = emptyList(),
    val technicalSkills: TechnicalSkills = TechnicalSkills(),
    val certifications: List<Certification> = emptyList()
)
