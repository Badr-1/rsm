package models

import kotlinx.serialization.Serializable
import utils.Utils

@Serializable
data class PersonalInfo(
    var name: String = "",
    var phone: String = "",
    var email: String = "",
    var linkedin: String = "",
    var github: String = ""
) {
    companion object {
        fun collect(): PersonalInfo {
            println("\n👤 Personal Information:")
            val name = Utils.readLineRequired("Full Name: ")
            val phone = Utils.readLineRequired("Phone Number: ")
            val email = Utils.readLineRequired("Email: ")
            val linkedin = Utils.readLineOptional("LinkedIn URL: ")
            val github = Utils.readLineOptional("GitHub URL: ")

            return PersonalInfo(name, phone, email, linkedin, github)
        }
    }

    fun update() {
        name = Utils.readLineOptional("Full Name ($name): ", name)
        phone = Utils.readLineOptional("Phone Number ($phone): ", phone)
        email = Utils.readLineOptional("Email ($email): ", email)
        linkedin =
            Utils.readLineOptional("LinkedIn URL${if (linkedin.isNotEmpty()) " ($linkedin): " else ": "}", linkedin)
        github = Utils.readLineOptional("GitHub URL${if (github.isNotEmpty()) " ($github): " else ": "}", github)
    }
}
