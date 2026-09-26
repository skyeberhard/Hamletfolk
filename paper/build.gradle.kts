repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
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
