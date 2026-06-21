subprojects {
    tasks.withType<org.gradle.api.tasks.testing.Test> {
        filter {
            excludeTestsMatching("*ApplicationTests")
        }
    }
}
