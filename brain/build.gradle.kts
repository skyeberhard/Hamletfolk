// R9.1: the brain module. The ONLY code in the project that touches the server's internal classes (net.minecraft.*,
// org.bukkit.craftbukkit.*). It is compiled against the exact Paper server this project runs (paper/run, made by
// ./gradlew runServer), so run that once before building. The paper module never links against this one: it loads it
// by name only after the self-check passes, so if the internals change (or this module is missing) the rest of the
// plugin still runs. Its classes are packed into the plugin jar by the paper module.
val minecraftVersion = property("minecraftVersion") as String
val serverRun = rootProject.layout.projectDirectory.dir("paper/run")
val serverJar = serverRun.file("versions/$minecraftVersion/paper-$minecraftVersion.jar")

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

evaluationDependsOn(":paper")

dependencies {
    // The plugin's classes (for the BrainModule interface), not its jar: the jar packs this module's classes in.
    compileOnly(rootProject.project(":paper").the<SourceSetContainer>()["main"].output)
    compileOnly(project(":core"))
    compileOnly("io.papermc.paper:paper-api:$minecraftVersion.build.+")
    // The patched server itself, and the libraries it runs with (Guava, DataFixerUpper, fastutil...), as Paper's
    // launcher downloaded them. The older paper-api copies in there are left out: the one above is the one we build on.
    compileOnly(files(serverJar))
    compileOnly(fileTree(serverRun.dir("libraries")) {
        include("**/*.jar")
        exclude("**/io/papermc/paper/paper-api/**")
    })
}

if (!serverJar.asFile.exists()) {
    // A fresh clone with no local server yet: build the plugin without the brain module (it then reports itself missing
    // and stays off) rather than failing. ./gradlew runServer once, then build again, to include it.
    logger.warn("Brain module skipped: ${serverJar.asFile} is not there yet (run ./gradlew runServer once).")
    tasks.named("compileJava") { enabled = false }
}
