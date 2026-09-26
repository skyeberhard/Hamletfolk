// Pure-Java simulation core. No Minecraft dependencies, so it can be unit tested
// and reused by other platforms (e.g. a Fabric port for single-player).
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
