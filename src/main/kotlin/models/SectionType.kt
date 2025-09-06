package models

enum class SectionType(val displayName: String, val isFixed: Boolean = false) {
    PERSONAL_INFO("Personal Info", true),
    EDUCATION("Education"),
    EXPERIENCE("Experience"),
    PROJECTS("Projects"),
    TECHNICAL_SKILLS("Technical Skills"),
    CERTIFICATIONS("Certifications"),
}