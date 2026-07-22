plugins {
    // Applies the correct Loom variant for the node: fabric-loom-remap with Mojang
    // mappings on obfuscated 1.21.x, the non-remapping fabric-loom on 26.x.
    id("dev.kikugie.loom-back-compat")
}

val modVersion = sc.properties.get<String>("mod.version")
val mcLabel = sc.properties.get<String>("mod.mc_label")

version = "$modVersion+$mcLabel"
base.archivesName = "spawnelytra-fabric"

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://maven.nucleoid.xyz/", "Nucleoid", "eu.pb4")
    mavenCentral()
}

dependencies {
    fun fapi(vararg modules: String) {
        for (it in modules) modImplementation(fabricApi.module(it, sc.properties.get<String>("deps.fabric_api")))
    }

    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")
    fapi(
        "fabric-api-base",
        "fabric-lifecycle-events-v1",
        "fabric-command-api-v2",
        "fabric-networking-api-v1",
        "fabric-entity-events-v1",
        "fabric-events-interaction-v0",
        "fabric-data-attachment-api-v1",
    )

    // Permissions (bundled): same nodes and defaults as the Paper plugin.yml.
    modImplementation("me.lucko:fabric-permissions-api:${sc.properties.get<String>("deps.permissions")}")
    include("me.lucko:fabric-permissions-api:${sc.properties.get<String>("deps.permissions")}")

    // Text Placeholder API (optional at runtime, soft-detected).
    modCompileOnly("eu.pb4:placeholder-api:${sc.properties.get<String>("deps.placeholders")}")

    // MiniMessage / Adventure (bundled, platform independent) and SnakeYAML for
    // Bukkit-compatible, comment-preserving YAML configuration files.
    val adventure = sc.properties.get<String>("deps.adventure")
    val bundled = listOf(
        "net.kyori:adventure-api:$adventure",
        "net.kyori:adventure-key:$adventure",
        "net.kyori:adventure-text-minimessage:$adventure",
        "net.kyori:adventure-text-serializer-commons:$adventure",
        "net.kyori:adventure-text-serializer-json:$adventure",
        "net.kyori:adventure-text-serializer-gson:$adventure",
        "net.kyori:adventure-text-serializer-plain:$adventure",
        "net.kyori:examination-api:1.3.0",
        "net.kyori:examination-string:1.3.0",
        "net.kyori:option:1.1.0",
        "org.yaml:snakeyaml:${sc.properties.get<String>("deps.snakeyaml")}",
    )
    for (dep in bundled) {
        implementation(dep) { isTransitive = false }
        include(dep)
    }
    compileOnly("org.jetbrains:annotations:26.0.2")
}

// Platform-neutral 1.6 core (zones, geometry, migration, tiers, stats, editor and screen
// models) shared with the Paper plugin; compiled into every node.
sourceSets.main {
    java.srcDir(rootProject.file("../common/src/main/java"))
}

java {
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = requiredJava.majorVersion.toInt()
    }

    processResources {
        val props = mapOf(
            "version" to modVersion,
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
            "loader" to Regex("\\d+\\.\\d+").find(sc.properties.get<String>("deps.fabric_loader"))!!.value,
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        inputs.property("mixinJava", mixinJava)
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }

        // The Paper plugin's config.yml and lang files are the single source of truth.
        from(rootProject.file("../src/main/resources")) {
            include("config.yml", "lang/*.yml")
        }
    }

    withType<Jar> {
        from(rootProject.file("../LICENSE")) { rename { "LICENSE-spawnelytra" } }
    }

    register<Copy>("collectJar") {
        group = "build"
        description = "Copies this node's release jar to the root build/libs directory"
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs"))
    }
}
