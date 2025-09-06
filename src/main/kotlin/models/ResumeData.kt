package models

import kotlinx.serialization.Serializable


@Serializable
data class ResumeData(
    var orderedSections: List<SectionType> = SectionType.entries.filter { !it.isFixed },
    var personalInfo: PersonalInfo = PersonalInfo(),
    var education: MutableList<Education> = mutableListOf(),
    var experience: MutableList<Experience> = mutableListOf(),
    var projects: MutableList<Project> = mutableListOf(),
    var technicalSkills: TechnicalSkills = TechnicalSkills(),
    var certifications: MutableList<Certification> = mutableListOf()
)
