package utils

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

object GitUtils {

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

}