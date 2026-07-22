plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.11"

stonecutter parameters {
    replacements {
        // Mojang renamed ResourceLocation to Identifier in 1.21.11.
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
        // Inventory click types were renamed in 26.1.
        string(current.parsed >= "26.1") {
            replace("ClickType", "ContainerInput")
        }
    }
}

// Builds every version node and copies the release jars into build/libs.
tasks.register("buildAll") {
    group = "build"
    description = "Builds all version nodes and collects the jars in build/libs"
    dependsOn(stonecutter.tasks.named("collectJar"))
}
