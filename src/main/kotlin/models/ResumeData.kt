package models

import kotlinx.serialization.Serializable
import java.util.Collections.emptyList


@Serializable
data class ResumeData(
    var orderedSections: List<SectionType> = SectionType.entries.filter { !it.isFixed },
    var personalInfo: PersonalInfo = PersonalInfo(),
    var education: MutableList<Education> = emptyList(),
    var experience: MutableList<Experience> = emptyList(),
    var projects: MutableList<Project> = emptyList(),
    var technicalSkills: TechnicalSkills = TechnicalSkills(),
    var certifications: MutableList<Certification> = emptyList()
)
