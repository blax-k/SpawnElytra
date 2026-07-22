pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Applies fabric-loom-remap (Mojang mappings) on obfuscated 1.21.x and
    // the non-remapping fabric-loom on unobfuscated 26.x.
    id("dev.kikugie.loom-back-compat") version "0.4.2"
}

stonecutter {
    create(rootProject) {
        // Each node produces one jar; see stonecutter.properties.toml for the
        // exact Minecraft range every node declares in fabric.mod.json.
        versions(
            "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.8", "1.21.10", "1.21.11",
            "26.1.2", "26.3"
        )
        vcsVersion = "1.21.11"
    }
}

rootProject.name = "spawnelytra-fabric"
