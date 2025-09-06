package models

import com.github.kinquirer.KInquirer
import com.github.kinquirer.components.promptCheckbox
import com.github.kinquirer.components.promptConfirm
import com.github.kinquirer.components.promptOrderableListObject
import com.github.kinquirer.core.Choice
import kotlinx.serialization.Serializable
import utils.Utils
import utils.Utils.toCommitMessage

@Serializable
data class TechnicalSkills(
    var entries: MutableMap<String, MutableList<String>> = mutableMapOf()
) {
    companion object {
        fun collect(prompt: String, isCategorized: Boolean): TechnicalSkills {
            println(prompt)
            val entries = mutableMapOf<String, MutableList<String>>()

            if (isCategorized) {
                do {
                    val category = Utils.readLineRequired("Category Name (e.g., Languages, Frameworks): ")
                    val skills = Utils.readLineRequired("Skills in $category (comma-separated): ")
                        .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                    entries[category] = skills
                } while (KInquirer.promptConfirm("Add another category?", default = false))
            } else {
                val skills = Utils.readLineRequired("Technical Skills (comma-separated): ")
                    .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                entries["Technical Skills"] = skills
            }
            return TechnicalSkills(entries)
        }
    }

    fun remove(items: List<String>): String {
        var metaData = ""
        items.forEach { item ->

            val skillsToRemove = KInquirer.promptCheckbox(
                message = "Select skills to remove from $item:",
                choices = entries.getOrDefault(item, mutableListOf()),
                hint = "pick using spacebar"
            )
            entries[item]?.removeAll(skillsToRemove)
            if (entries[item]?.isEmpty() == true) {
                entries.remove(item)
            }
            metaData += skillsToRemove.toCommitMessage("Removed from $item")
        }
        return metaData
    }

    fun update(items: List<String>): String {
        var metadata = ""
        var newName: String
        items.forEach { item ->
            newName = Utils.readLineOptional(
                "Category Name ($item): ",
                item,
                validation = { it.isNotEmpty() && !entries.containsKey(it) }
            )

            if (newName.isNotEmpty() && newName != item) {
                entries[newName] = entries[item] ?: mutableListOf()
                entries.remove(item)
                metadata += "Category name updated from '$item' to '$newName'.\n"
            }
        }
        if (!isFlattened()) {
            KInquirer.promptConfirm("Do you want to flatten your technical skills?", default = false)
                .let { confirm ->
                    if (confirm) {
                        flatten()
                        metadata += "Flattened technical skills into a single category."
                    }
                }
        } else {
            KInquirer.promptConfirm("Do you want to categorize your technical skills?", default = false)
                .let { confirm ->
                    if (confirm) {
                        categorize()
                        metadata += "Categorized technical skills."
                    }
                }
        }

        return metadata
    }

    fun reorder() {
        if (isFlattened()) {
            println("Technical skills are flattened. Reordering not applicable.")
            return
        } else {
            val categories = entries.keys.toList()
            val reorderedCategories = KInquirer.promptOrderableListObject(
                "Reorder Technical Skill Categories:",
                categories.map { Choice(it, it) }.toMutableList(),
                hint = "move using arrow keys"
            )
            val newEntries = linkedMapOf<String, MutableList<String>>()
            reorderedCategories.forEach { category ->
                entries[category]?.let { skills ->
                    newEntries[category] = skills
                }
            }
            entries = newEntries
        }
    }

    private fun flatten() {
        if (entries.size > 1) {
            val allSkills = entries.values.flatten().distinct()
            entries.clear()
            entries["Technical Skills"] = allSkills.toMutableList()
        }
    }

    private fun categorize() {
        val allSkills = entries.values.flatten().distinct().toMutableList()
        entries.clear()
        do {
            val category = Utils.readLineRequired("Category Name (e.g., Languages, Frameworks): ")
            val skills = KInquirer.promptCheckbox(
                message = "Select skills to add to $category:",
                choices = allSkills,
                minNumOfSelection = 1,
                hint = "pick using spacebar"
            )
            entries[category] = skills.toMutableList()
            allSkills.removeAll(skills)
        } while (allSkills.isNotEmpty() && KInquirer.promptConfirm(
                "Add another category?",
                default = false
            )
        )
        if (allSkills.isNotEmpty()) {
            entries["Uncategorized"] = allSkills
        }
    }

    operator fun plus(other: TechnicalSkills): TechnicalSkills {
        other.entries.forEach { (category, skills) ->
            if (entries.containsKey(category)) {
                entries[category]?.addAll(skills)
            } else {
                entries[category] = skills.distinct().toMutableList()
            }
        }
        return this
    }

    fun isFlattened(): Boolean {
        return entries.isEmpty() || (entries.size == 1 && entries.containsKey("Technical Skills"))
    }
}
