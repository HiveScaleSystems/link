// Engine-agnostic core of Link: tickets, config, matchmaking and the registry backends. No game
// server API on the classpath, so the same core backs the Hytale plugin, a future Paper port, and
// Hive's own connector.
plugins {
    `java-library`
}

val gsonVersion: String by rootProject.extra


java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

dependencies {
    // Provided at runtime by the game server (Hytale ships Gson), so not bundled.
    compileOnly("com.google.code.gson:gson:$gsonVersion")
    implementation("redis.clients:jedis:5.2.0")

    testImplementation("com.google.code.gson:gson:$gsonVersion")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
