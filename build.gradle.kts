plugins {
    id("com.gradleup.shadow") version "9.3.0" apply false
}

val gsonVersion by extra("2.13.2")

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
