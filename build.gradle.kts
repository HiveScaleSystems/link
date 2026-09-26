plugins {
    id("com.gradleup.shadow") version "9.3.0" apply false
}

val gsonVersion by extra("2.13.2")

allprojects {
    group = "net.beehivesys.link"
    // Releases pass -Pversion=1.2.3 (the tag without its "v"); local builds are 0.1.0-dev.
    version = (findProperty("version") as String?)?.takeIf { it != "unspecified" && it.isNotBlank() } ?: "0.1.0-dev"

    repositories {
        mavenCentral()
    }
}

subprojects {
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
