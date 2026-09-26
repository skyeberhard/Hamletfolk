plugins {
    // Provides ./gradlew runServer: downloads Paper and starts a local test server with this plugin.
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

val minecraftVersion = property("minecraftVersion") as String

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$minecraftVersion-R0.1-SNAPSHOT")
    implementation(project(":core"))
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

// Bundle the core classes into the plugin jar so the server only needs one file.
tasks.jar {
    archiveBaseName.set("MCSocieties")
    dependsOn(":core:jar")
    from(project(":core").sourceSets["main"].output)
}

tasks.runServer {
    minecraftVersion(minecraftVersion)
    // The test server lives in paper/run/ (git-ignored). Delete it for a fresh world.
    runDirectory.set(layout.projectDirectory.dir("run"))
}
