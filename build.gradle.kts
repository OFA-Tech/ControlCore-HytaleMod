/**
 * NOTE: This is entirely optional and basics can be done in `settings.gradle.kts`
 */

group = providers.gradleProperty("plugin_group").getOrElse("net.ofatech")
version = providers.gradleProperty("plugin_version").getOrElse("0.0.1")

repositories {
    mavenCentral()

    maven {
        name = "CurseMaven"
        url = uri("https://www.cursemaven.com")
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("curse.maven:hyui-1431415:7820303")

    implementation("io.avaje:avaje-inject:12.5-javax")
    annotationProcessor("io.avaje:avaje-inject-generator:12.5-javax")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("io.avaje:avaje-inject-test:12.5-javax")
    testAnnotationProcessor("io.avaje:avaje-inject-generator:12.5-javax")
}

tasks.test {
    useJUnitPlatform()
}
