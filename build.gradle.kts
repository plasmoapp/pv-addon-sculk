import net.fabricmc.loom.api.LoomGradleExtensionAPI

val mavenGroup: String by rootProject
val buildVersion: String by rootProject

plugins {
    java
    idea
    id("fabric-loom") version "1.0-SNAPSHOT"
}

group = mavenGroup
version = buildVersion

var mappingsDependency: Dependency? = null

configure<LoomGradleExtensionAPI> {
    mappingsDependency = layered {
        officialMojangMappings()
    }
}

dependencies {
    "minecraft"("com.mojang:minecraft:1.19.2")
    mappingsDependency?.let { "mappings"(it) }
    modImplementation("net.fabricmc:fabric-loader:0.14.10")

    compileOnly("io.papermc.paper:paper-api:1.19.2-R0.1-SNAPSHOT")

    compileOnly("com.google.guava:guava:31.1-jre")
    compileOnly("org.jetbrains:annotations:23.0.0")
    compileOnly("org.projectlombok:lombok:1.18.24")

    compileOnly("su.plo.voice.api:server:2.0.0+ALPHA")
    compileOnly("su.plo.config:config:1.0.0")

    annotationProcessor("org.projectlombok:lombok:1.18.24")
    annotationProcessor("su.plo.voice.api:server:2.0.0+ALPHA")
    annotationProcessor("com.google.guava:guava:31.1-jre")
    annotationProcessor("com.google.code.gson:gson:2.9.0")
}

repositories {
    mavenCentral()
    mavenLocal()

    maven {
        url = uri("https://repo.plo.su")
    }
}

tasks {
    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    }
}
