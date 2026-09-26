plugins {
    `java-library`
    id("com.gradleup.shadow")
}


// Link targets the current public Hytale release.
// Hytale rotates old builds off its Maven repo, so check
// https://maven.hytale.com/release/com/hypixel/hytale/Server/maven-metadata.xml before changing it.
val hytaleServerVersion = "0.6.8"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

base {
    archivesName.set("link-hytale")
}

repositories {
    mavenCentral()
    maven {
        name = "hytale-release"
        url = uri("https://maven.hytale.com/release")
    }
}

dependencies {
    api(project(":LinkCore"))
    compileOnly("com.hypixel.hytale:Server:$hytaleServerVersion")
}

tasks.processResources {
    val pluginVersion = version.toString()
    inputs.property("pluginVersion", pluginVersion)
    filesMatching("manifest.json") {
        filter { line -> line.replace("@version@", pluginVersion) }
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    // Gson comes from the server. Everything else Link needs is bundled and relocated, so it cannot
    // clash with another plugin shipping its own Jedis.
    dependencies {
        exclude(dependency("com.google.code.gson:gson"))
        exclude(dependency("com.google.errorprone:error_prone_annotations"))
    }
    relocate("redis.clients", "net.beehivesys.link.libs.jedis")
    relocate("org.apache.commons.pool2", "net.beehivesys.link.libs.pool2")
    relocate("org.json", "net.beehivesys.link.libs.json")
    relocate("org.slf4j", "net.beehivesys.link.libs.slf4j")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
