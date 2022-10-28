pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
    }
}

rootProject.name = "pv-addon-sculk"
