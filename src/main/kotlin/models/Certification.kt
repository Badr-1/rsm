package models

import com.github.kinquirer.KInquirer
import com.github.kinquirer.components.promptConfirm
import com.github.kinquirer.components.promptOrderableListObject
import com.github.kinquirer.core.Choice
import kotlinx.serialization.Serializable
import utils.Utils

@Serializable
data class Certification(
    var name: String = "",
    var issuingOrganization: String = "",
    var issueDate: String = ""
) {
    companion object {
        fun collect(prompt: String): MutableList<Certification> {
            println(prompt)
            val certificationsList = mutableListOf<Certification>()

            do {
                val name = Utils.readLineRequired("Certification Name: ")
                val issuingOrganization = Utils.readLineRequired("Issuing Organization: ")
                val issueDate = Utils.readLineRequired("Issue Date: ")

                certificationsList.add(
                    Certification(
                        name = name,
                        issuingOrganization = issuingOrganization,
                        issueDate = issueDate
                    )
                )

                val addMore = KInquirer.promptConfirm("Add another certification entry?", default = false)
            } while (addMore)

            return certificationsList.reorganize()
        }

        fun MutableList<Certification>.reorganize(): MutableList<Certification> {
            val organized = this.groupBy { it.issuingOrganization }.flatMap { it.value }.toMutableList()
            this.clear()
            this.addAll(organized)
            return this
        }

        fun MutableList<Certification>.reorder() {
            val organized = KInquirer.promptOrderableListObject(
                "Reorder Certification Entries:",
                this.groupBy { it.issuingOrganization }.map { Choice(it.key, it) }.toMutableList(),
                hint = "move using arrow keys"
            ).flatMap { it.value }.toMutableList()
            this.clear()
            this.addAll(organized)
        }
    }

    fun update(): String {
        var metaData = ""
        val newName = Utils.readLineOptional("Certification Name ($name): ")
        val newIssuingOrganization = Utils.readLineOptional("Issuing Organization ($issuingOrganization): ")
        val newIssueDate = Utils.readLineOptional("Issue Date ($issueDate): ")
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
