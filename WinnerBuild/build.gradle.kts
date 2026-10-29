import org.gradle.kotlin.dsl.*

plugins {
    id("fabric-loom") version "1.13.3"
}

loom {
    accessWidenerPath.set(file("src/main/resources/winnerclient.accesswidener"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

group = "com.winnerclient"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net")
    maven("https://jitpack.io")
}

dependencies {
    "minecraft"("com.mojang:minecraft:1.21.11")
    "mappings"("net.fabricmc:yarn:1.21.11+build.1:v2")
    "modImplementation"("net.fabricmc:fabric-loader:0.16.5")
    "modApi"("net.fabricmc.fabric-api:fabric-api:0.141.4+1.21.11")
    
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.slf4j:slf4j-api:2.0.17")

    modImplementation("club.minnced:java-discord-rpc:2.0.2") {
        exclude("net.java.dev.jna")
        exclude("club.minnced", "discord-rpc-release")
    }
    include("club.minnced:java-discord-rpc:2.0.2")
    include("net.java.dev.jna:jna:5.14.0")

    modImplementation("com.github.MinnDevelopment:discord-rpc-release:v3.3.0")
    include("com.github.MinnDevelopment:discord-rpc-release:v3.3.0")
    implementation("org.lwjgl:lwjgl-glfw:3.3.4")
    runtimeOnly("org.lwjgl:lwjgl-glfw:3.3.4:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw:3.3.4:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-glfw:3.3.4:natives-macos")
}

sourceSets {
    main {
        java {
            srcDir("src/main/java")
            exclude("**/agent/**")
            exclude("**/stub/**")
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation", "-parameters", "--release", "21"))
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Fabric-Mod-Loader" to "fabric",
            "Fabric-Mod-Version" to "1.0.0",
            "Fabric-Mod-Id" to "winnerclient",
            "Manifest-Version" to "1.0",
            "Implementation-Version" to project.version.toString(),
            "Implementation-Title" to "WinnerClient",
            "Implementation-Vendor" to "WinnerClient"
        )
    }
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("module-info.class")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/MANIFEST.MF")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/NOTICE*")
}