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
data class Experience(
    var company: String = "",
    var position: String = "",
    var location: String = "",
    var date: String = "",
    var bullets: List<String> = Collections.emptyList()
) : OrderableBullets {
    companion object {
        fun collect(prompt: String): MutableList<Experience> {
            println(prompt)
            val experienceList = mutableListOf<Experience>()

            do {
                val company = Utils.readLineRequired("Company: ")
                val position = Utils.readLineRequired("Position: ")
                val location = Utils.readLineRequired("Location: ")
                val date = Utils.readLineRequired("Date: ")

                println("Bullet Points (press Enter on empty line to finish):")
                val bullets = mutableListOf<String>()
                do {
                    val bullet = Utils.readLineOptional("• ")
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
        val newCompany = Utils.readLineOptional("Company ($company): ")
        val newPosition = Utils.readLineOptional("Position ($position): ")
        val newLocation = Utils.readLineOptional("Location ($location): ")
        val newDate = Utils.readLineOptional("Date ($date): ")
        val newBullets = mutableListOf<String>()

        newBullets += KInquirer.promptCheckbox(
            "Old Bullet Points (choose what to keep)",
            choices = bullets,
            hint = "pick using spacebar"
        )
        println("Add More (press Enter on empty line to finish):")
        do {
            val bullet = Utils.readLineOptional("• ")
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

    override fun reorderBullets() {
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