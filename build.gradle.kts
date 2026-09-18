import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.lapis)
}

val namespace = "io.github.diskria"
val modId = "advancements_search"
val modPackage = "$namespace.$modId"

group = namespace
version = "1.3.1"

loom {
    accessWidenerPath = file("src/main/resources/$modId.classtweaker")
}

lapis {
    uniqueModPrefix = "$modId$"
    mixinConfig = file("src/main/resources/$modId.mixins.json")
    nullableAnnotation = "org.jspecify.annotations.Nullable"
    nonNullAnnotation = "org.jspecify.annotations.NonNull"
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.kotlin)
}

tasks.withType<Jar> {
    exclude("**/*.kotlin_module")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public") {
        name = "SpongePublic"
        content { includeGroup("org.spongepowered") }
    }
}
