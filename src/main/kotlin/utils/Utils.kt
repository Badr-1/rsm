package utils

object Utils {
    fun List<Any>.toCommitMessage(title: String): String {
        return if (this.isEmpty()) {
            ""
        } else {
            title + this.joinToString(prefix = "\n- ", separator = "\n- ", postfix = "\n") { it.toString() }
        }
    }
}