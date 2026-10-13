// Cloud-container workaround (installed by scripts/cloud-setup.sh into ~/.gradle/init.d/).
// Through the container's egress proxy, repo.maven.apache.org often answers 429 Too Many
// Requests, while repo1.maven.org (the same Maven Central) does not. This adds repo1 in
// front of the project's repositories; Google's groups are excluded so they go to google().
// Not needed on a normal developer machine.
val repo1 = "https://repo1.maven.org/maven2/"

fun org.gradle.api.artifacts.dsl.RepositoryHandler.addRepo1() {
    maven(repo1) {
        name = "MavenCentralRepo1"
        content {
            excludeGroupByRegex("androidx\\..*")
            excludeGroupByRegex("com\\.android\\..*")
            excludeGroupByRegex("com\\.google\\.android\\..*")
        }
    }
}

beforeSettings {
    pluginManagement.repositories.addRepo1()
    dependencyResolutionManagement.repositories.addRepo1()
}
