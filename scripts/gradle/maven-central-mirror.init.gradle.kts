// Cloud-container workaround (installed by scripts/cloud-setup.sh into ~/.gradle/init.d/).
// Through the container's egress proxy both repo.maven.apache.org and repo1.maven.org often
// answer 429 Too Many Requests; Google's official Maven Central mirror does not. This adds the
// mirror in front of the project's repositories (mavenCentral() stays as the fallback); Google's
// own groups are excluded so they still resolve from google(). Not needed on a normal machine.
val mavenCentralMirror = "https://maven-central.storage-download.googleapis.com/maven2/"

fun org.gradle.api.artifacts.dsl.RepositoryHandler.addMavenCentralMirror() {
    maven(mavenCentralMirror) {
        name = "MavenCentralGoogleMirror"
        content {
            excludeGroupByRegex("androidx\\..*")
            excludeGroupByRegex("com\\.android\\..*")
            excludeGroupByRegex("com\\.google\\.android\\..*")
        }
    }
}

beforeSettings {
    pluginManagement.repositories.addMavenCentralMirror()
    dependencyResolutionManagement.repositories.addMavenCentralMirror()
}
