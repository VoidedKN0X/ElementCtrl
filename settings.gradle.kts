pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // TLS certificate checks for the Rust SDK go through Android's own trust store via this small library.
        exclusiveContent {
            forRepository {
                maven { url = uri("https://github.com/rustls/rustls-platform-verifier/raw/maven-archive/android-release-support/maven/") }
            }
            filter { includeModule("org.rustls", "rustls-platform-verifier") }
        }
        google()
        mavenCentral()
        // Recent builds of the Matrix Rust SDK bindings are also published as GitHub release assets.
        ivy {
            url = uri("https://github.com/matrix-org/matrix-rust-components-kotlin/releases/download")
            patternLayout { artifact("sdk-[revision]/[artifact]-[revision](-[classifier])(.[ext])") }
            metadataSources { gradleMetadata() }
            content { includeModule("org.matrix.rustcomponents", "sdk-android") }
        }
    }
}
rootProject.name = "ElementCtrl"
include(":app")
