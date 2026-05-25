import dev.scaffoldit.hytale.wire.HytaleManifest

val pluginGroup = providers.gradleProperty("plugin_group").getOrElse("net.ofatech")
val pluginName = providers.gradleProperty("plugin_name").getOrElse("HytaleMod")
val pluginVersion = providers.gradleProperty("plugin_version").getOrElse("0.0.1")
val pluginDescription = providers.gradleProperty("plugin_description").getOrElse("A Hytale Mod")
val pluginWebSite = providers.gradleProperty("plugin_website").getOrElse("")
val serverVersion = providers.gradleProperty("server_version").getOrElse("*")
val pluginMain = providers.gradleProperty("plugin_main").getOrElse("net.ofatech.controlcore.TemplatePlugin")

val pluginAuthorNames = providers.gradleProperty("plugin_author_names").orNull
val pluginAuthorEmails = providers.gradleProperty("plugin_author_emails").orNull
val pluginAuthorUrls = providers.gradleProperty("plugin_author_urls").orNull

val authorNames = pluginAuthorNames
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.ifEmpty { listOf("") }
    ?: listOf("")
val authorEmails = pluginAuthorEmails
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: emptyList()
val authorUrls = pluginAuthorUrls
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: emptyList()
val authors = authorNames.mapIndexed { index, name ->
    val email = authorEmails.getOrNull(index)
    val url = authorUrls.getOrNull(index)
    HytaleManifest.Author(name, email, url)
}

rootProject.name = pluginName

plugins {
    // See documentation on https://scaffoldit.dev
    id("dev.scaffoldit") version "0.2.+"
}

// Would you like to do a split project?
// Create a folder named "common", then configure details with `common { }`

hytale {
    usePatchline("release")
    useVersion("latest")

    repositories {
        // Any external repositories besides: MavenLocal, MavenCentral, HytaleMaven, and CurseMaven
    }

    dependencies {
        // Any external dependency you also want to include
        implementation("com.google.code.gson:gson:2.10.1")
    }

    manifest {
        Group = pluginGroup
        Name = pluginName
        Description = pluginDescription
        Main = pluginMain
        Version = pluginVersion
        Website = pluginWebSite
        ServerVersion = serverVersion
        Authors = authors
    }
}
