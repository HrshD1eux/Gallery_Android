// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.gradle) apply false
}

tasks.register("syncScreenshots") {
    group = "documentation"
    description = "Synchronizes screenshots to docs/ (website) and README.md"
    doLast {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val pythonCmd = if (isWindows) "python" else "python3"
        try {
            project.exec {
                commandLine(pythonCmd, "${rootDir}/scripts/sync_screenshots.py")
            }
        } catch (e: Exception) {
            logger.warn("syncScreenshots: Python execution skipped (${e.message})")
        }
    }
}
