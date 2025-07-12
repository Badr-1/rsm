package utils

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

object GitUtils {
    fun isGitRepository(): Boolean {
        return File(".git").exists()
    }

    fun openRepository(): Git? {
        return try {
            val repo = FileRepositoryBuilder()
                .setGitDir(File(".git"))
                .readEnvironment()
                .findGitDir()
                .build()
            Git(repo)
        } catch (e: Exception) {
            null
        }
    }

    fun getCurrentBranch(): String? {
        return try {
            val git = openRepository()
            git?.repository?.branch
        } catch (e: Exception) {
            null
        }
    }

    fun listBranches(): List<String> {
        return try {
            val git = openRepository()
            git?.branchList()?.call()?.map {
                it.name.removePrefix("refs/heads/")
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun branchExists(branchName: String): Boolean {
        return listBranches().contains(branchName)
    }
}