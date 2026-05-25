/**
 * NOTE: This is entirely optional and basics can be done in `settings.gradle.kts`
 */

group = providers.gradleProperty("plugin_group").getOrElse("net.ofatech")
version = providers.gradleProperty("plugin_version").getOrElse("0.0.1")

repositories {
    // Any external repositories besides: MavenLocal, MavenCentral, HytaleMaven, and CurseMaven
}

dependencies {
    implementation("com.google.code.gson:gson:2.14.0")
    // Any external dependency you also want to include
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("org.mockito:mockito-core:5.12.0")
}

tasks.test {
    useJUnitPlatform()
}
