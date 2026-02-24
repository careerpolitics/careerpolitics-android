// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    plugins.withId("io.gitlab.arturbosch.detekt") {
        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            buildUponDefaultConfig = true
            allRules = false
            config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        }
    }
}

gradle.projectsEvaluated {
    val featurePrefix = ":feature:"
    rootProject.subprojects
        .filter { it.path.startsWith(featurePrefix) }
        .forEach { featureProject ->
            val forbidden = featureProject.configurations
                .flatMap { configuration ->
                    configuration.dependencies.mapNotNull { dependency ->
                        dependency as? org.gradle.api.artifacts.ProjectDependency
                    }
                }
                .filter { dependency -> dependency.dependencyProject.path.startsWith(featurePrefix) }
                .filter { dependency -> dependency.dependencyProject.path != featureProject.path }
            check(forbidden.isEmpty()) {
                val modules = forbidden
                    .map { it.dependencyProject.path }
                    .distinct()
                    .sorted()
                    .joinToString()
                "Module boundary violation in ${featureProject.path}. Feature modules must not depend on feature modules directly. Found: $modules"
            }
        }
}
