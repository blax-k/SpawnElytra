plugins {
    java
    id("io.github.goooler.shadow") version "8.1.8"
}

group = "com.blaxk"
version = "1.5"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "spigotmc-repo"
        url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }
    maven {
        name = "sonatype-oss-snapshots1"
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
    maven {
        name = "placeholderapi"
        url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }
}

val adventureVersion = "4.17.0"
val adventurePlatformVersion = "4.3.4"

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    compileOnly("org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.kyori:adventure-api:$adventureVersion")
    
    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation("net.kyori:adventure-text-minimessage:$adventureVersion")
    implementation("net.kyori:adventure-text-serializer-plain:$adventureVersion")
    implementation("net.kyori:adventure-platform-bukkit:$adventurePlatformVersion")
    implementation("com.google.code.gson:gson:2.11.0")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    
    processResources {
        filteringCharset = "UTF-8"
        filesMatching(listOf("plugin.yml")) {
            expand(
                "version" to project.version
            )
        }
    }
    
    shadowJar {
        archiveClassifier.set("")
        
        relocate("org.bstats", "com.blaxk.spawnelytra.metrics")
    }
    
    build {
        dependsOn(shadowJar)
    }

    jar {
        enabled = false
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
