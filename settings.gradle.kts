pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://maven.fabricmc.net") { name = "Fabric" }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
    }
}
