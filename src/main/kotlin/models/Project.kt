package models

import com.github.kinquirer.KInquirer
import com.github.kinquirer.components.promptCheckbox
import com.github.kinquirer.components.promptConfirm
import com.github.kinquirer.components.promptOrderableListObject
import com.github.kinquirer.core.Choice
import kotlinx.serialization.Serializable
import utils.Utils
import java.util.Collections

@Serializable
data class Project(
    var name: String = "",
    var technologies: String = "",
    var date: String = "",
    var bullets: List<String> = Collections.emptyList()
): OrderableBullets {
    companion object {
        fun collect(prompt: String): MutableList<Project> {
            println(prompt)
            val projectsList = mutableListOf<Project>()

            do {
                val name = Utils.readLineRequired("Project Name: ")
                val technologies = Utils.readLineRequired("Technologies: ")
                val date = Utils.readLineRequired("Date: ")

                println("Project Details (press Enter on empty line to finish):")
                val bullets = mutableListOf<String>()
                do {
                    val bullet = Utils.readLineOptional("• ")
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
        val newName: String = Utils.readLineOptional("Project Name ($name): ")
        val newTechnologies: String = Utils.readLineOptional("Technologies ($technologies): ")
        val newDate: String = Utils.readLineOptional("Date ($date):")

        val newBullets = mutableListOf<String>()
        newBullets += KInquirer.promptCheckbox(
            "Old Project Details (choose what to keep)",
            choices = bullets,
            hint = "pick using spacebar"
        )
        println("Add More (press Enter on empty line to finish):")
        do {
            val bullet = Utils.readLineOptional("• ")
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

    override fun reorderBullets() {
        bullets = KInquirer.promptOrderableListObject(
            "Reorder Bullet Points:",
            bullets.map { Choice(it, it) }.toMutableList(),
            hint = "move using arrow keys"
        )
    }
}