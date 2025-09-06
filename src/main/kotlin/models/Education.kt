package models

import com.github.kinquirer.KInquirer
import com.github.kinquirer.components.promptConfirm
import kotlinx.serialization.Serializable
import utils.Utils

@Serializable
data class Education(
    var institution: String = "",
    var degree: String = "",
    var location: String = "",
    var graduationDate: String = "",
    var gpa: String = ""
) {
    companion object {
        fun collect(prompt: String): MutableList<Education> {
            println(prompt)
            val educationList = mutableListOf<Education>()

            do {
                val institution = Utils.readLineRequired("Institution: ")
                val degree = Utils.readLineRequired("Degree: ")
                val location = Utils.readLineRequired("Location: ")
                val graduationDate = Utils.readLineRequired("Graduation Date: ")
                val gpa = Utils.readLineOptional("GPA: ")

                educationList.add(
                    Education(
                        institution = institution,
                        degree = degree,
                        location = location,
                        graduationDate = graduationDate,
                        gpa = gpa
                    )
                )

                val addMore = KInquirer.promptConfirm("Add another education entry?", default = false)
            } while (addMore)

            return educationList
        }
    }

    fun update(): String {
        var metaData = ""
        val newInstitution = Utils.readLineOptional("Institution ($institution): ")
        val newDegree = Utils.readLineOptional("Degree ($degree): ")
        val newLocation = Utils.readLineOptional("Location ($location): ")
        val newGraduationDate = Utils.readLineOptional("Graduation Date ($graduationDate): ")
        val newGpa = Utils.readLineOptional("GPA ($gpa): ")

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
