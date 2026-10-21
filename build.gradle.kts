plugins {
    java
    `maven-publish`
    id("com.azuredoom.hytale-tools") version "1.0.51"
    id("com.diffplug.spotless") version "8.10.4"
}

fun prop(name: String): String = project.property(name).toString()

group = prop("group")
version = prop("version")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(prop("java_version").toInt()))
    withSourcesJar()
    withJavadocJar()
}

hytaleTools {
    javaVersion = prop("java_version").toInt()
    hytaleVersion = prop("hytale_version")
    manifestServerVersion = prop("manifestServerVersion")
    manifestGroup = prop("manifest_group")
    modId = prop("mod_id")
    modDescription = prop("mod_description")
    modUrl = prop("mod_url")
    mainClass = prop("main_class")
    modCredits = prop("mod_author")
    manifestDependencies = prop("manifest_dependencies")
    manifestOptionalDependencies = prop("manifest_opt_dependencies")
    curseforgeId = prop("curseforgeID")
    disabledByDefault = prop("disabled_by_default").toBoolean()
    includesPack = prop("includes_pack").toBoolean()
    patchline = prop("patchline")
    injectServerJavadocsIntoSources = prop("injectServerJavadocsIntoSources").toBoolean()
    generateAssetsBinary = prop("generateAssetsBinary").toBoolean()
    findProperty("hytaleHomeOverride")?.let { hytaleHomeOverride = it.toString() }
}

repositories {
    mavenCentral()
}

spotless {
    java {
        target("src/*/java/**/*.java")
        palantirJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

dependencies {
    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")

    // Shipped by the Hytale server, so it is not bundled.
    compileOnly("com.google.code.gson:gson:2.14.0")
    testImplementation("com.google.code.gson:gson:2.14.0")

    // HyUI (Ellie:HyUI, CurseForge 1431415) can render the control panel instead of the built-in page. It is a
    // separate plugin and an optional manifest dependency (see infrastructure/platforms/hyui), never bundled.
    // HyUI 0.9.8 is not compatible with Hytale 0.7, so runServer does not load it. Once a compatible HyUI exists,
    // bump the file id and switch compileOnly to vineImplementation (not vineMod: runServer loads our mod from
    // the classpath, so HyUI's classes must be on that classpath too).
    compileOnly("curse.maven:hyui-1431415:8363317")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
    testCompileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
    testAnnotationProcessor("com.google.auto.service:auto-service:1.1.1")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
    // CI only: incremental builds re-read @AutoService classes from bytecode, where
    // @SuppressWarnings("rawtypes") is lost, so the processor warns again on local rebuilds.
    if (providers.gradleProperty("strict").isPresent) {
        options.compilerArgs.add("-Werror")
    }
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:-missing", "-quiet")
}

// runServer links run/mods/<Group>_<Name> (the plugin's data folder) to src/main/resources,
// so runtime files written there must never be packaged.
val runtimeDataFiles = listOf("Settings.json", "Settings.json.bak", "players.json", "players.json.tmp",
    "SettingValues.json", "SettingValues.json.tmp", "KeyBinds.json", "KeyBinds.json.tmp",
    "CustomKeyBinds.json", "CustomKeyBinds.json.tmp",
    "SettingPermissions.json", "SettingPermissions.json.tmp")

tasks.processResources {
    exclude(runtimeDataFiles)
}

tasks.test {
    useJUnitPlatform()
}

// The run directory holds the server's log lock files (run/logs/*.lck), which Windows will not let Gradle
// hash while a server runs or after one crashed. Tracking it gains nothing: the task must always run.
tasks.matching { it.name == "prepareRunServer" }.configureEach {
    doNotTrackState("The run directory contains locked server log files")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set(prop("mod_name"))
    archiveVersion.set(prop("version"))
}

publishing {
    publications {
        create<MavenPublication>("mod") {
            artifactId = prop("mod_id").lowercase()
            // Only the jars: the Hytale tooling adds a dev-only runtime dependency that mods must not inherit,
            // and everything else ControlCore uses is provided by the server.
            artifact(tasks.named("jar"))
            artifact(tasks.named("sourcesJar"))
            artifact(tasks.named("javadocJar"))
            pom {
                name.set(prop("mod_name"))
                description.set(prop("mod_description"))
                url.set(prop("mod_url"))
                licenses {
                    license { name.set(prop("mod_license")) }
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/${prop("github_repository")}")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
