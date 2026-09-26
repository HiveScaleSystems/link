plugins {
    id("com.gradleup.shadow") version "9.3.0" apply false
}

val gsonVersion by extra("2.13.2")

allprojects {
    group = "net.beehivesys.link"
    version = (findProperty("linkVersion") as String?)?.takeIf { it.isNotBlank() } ?: "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
