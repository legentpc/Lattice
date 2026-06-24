import earth.terrarium.cloche.api.target.CommonTarget
import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SonatypeHost
import earth.terrarium.cloche.api.attributes.TargetAttributes

plugins {
    id("earth.terrarium.cloche") version "0.18.8+beta-2"
    id("com.vanniktech.maven.publish") version("0.28.0") // `maven-publish` doesn't support new maven central
}

version = project.version
group = project.group

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    cloche.librariesMinecraft()

    mavenCentral()

    cloche {
        main()
        mavenFabric()
        mavenNeoforgedMeta()
        mavenNeoforged()
        mavenForge()
    }
}

dependencies {
    compileOnly("org.jetbrains:annotations:23.0.0")
}

var fabricJarOutputs = mutableListOf<Provider<out Jar>>()
var forgeLikeJarOutputs = mutableListOf<Provider<out Jar>>()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

cloche {
    metadata {
        modId = "lattice"
        name = "Lattice"
        description = "Library for creating configuration GUIs"
        license = "MIT"
        icon = "assets/lattice/icon.png"
        author("Moulberry")

        custom("modmenu" to mapOf("badges" to listOf("library")))
    }

    mappings {
        official()
    }

    common {
        dependencies {
            compileOnly("org.jetbrains:annotations:23.0.0")
        }

        mixins.from("src/26.1/main/mixins/lattice261.mixins.json")
        mixins.from("src/26.2/main/mixins/lattice262.mixins.json")
    }

    val commonMinecraftVersion: Attribute<String> = Attribute.of("com.moulberry.commonMinecraftVersion", String::class.java)

    fun createCommon(version: String): CommonTarget {
        val rawVersion = version.split("-")[0]
        return common(rawVersion) {
            attributes {
                attribute(commonMinecraftVersion, rawVersion)
            }
        }
    }

    fun create(version: String, fabricLoader: String) {
        val commonTarget = createCommon(version)

        val rawVersion = version.split("-")[0]
        fabric("fabric:${rawVersion}") {
            minecraftVersion = version
            loaderVersion = fabricLoader

            dependsOn(commonTarget)

            includedClient()

            fabricJarOutputs.add(finalJar)
        }
    }

    create("26.2", "0.19.3")
    create("26.1", "0.18.4")
}

tasks.register<Jar>("buildMergedFabric") {
    archiveBaseName.set("lattice")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    fabricJarOutputs.forEach { jarProvider ->
        from(project.zipTree(jarProvider.get().archiveFile))
    }

    manifest {
        attributes["Fabric-Jar-Type"] = "classes"
    }
}

fun baseFabricConfiguration(configuration: Configuration) {
    configuration.outgoing.capability("com.moulberry:lattice:${rootProject.version}")
    configuration.outgoing.artifact(tasks.named<Jar>("buildMergedFabric").get())

    configuration.attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
    configuration.attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    configuration.attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    configuration.attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
}

var fabricConfigurationApi = configurations.create("fabricApi") {
    baseFabricConfiguration(this)
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
}

var fabricConfigurationRuntime = configurations.create("fabricRuntime") {
    baseFabricConfiguration(this)
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
}

val javaComponent = components.findByName("java") as AdhocComponentWithVariants

// Hack to remove all cloche variants from publication
rootProject.configurations.forEach {
    try {
        javaComponent.withVariantsFromConfiguration(it) {
            skip()
        }
    } catch (ignored: Exception) {}
}

javaComponent.addVariantsFromConfiguration(fabricConfigurationApi) {
    mapToMavenScope("runtime")
    mapToOptional()
}
javaComponent.addVariantsFromConfiguration(fabricConfigurationRuntime) {
    mapToMavenScope("runtime")
    mapToOptional()
}

mavenPublishing {
    configure(JavaLibrary(JavadocJar.Javadoc(), true))

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()

    coordinates("com.moulberry", "lattice", version.toString())

    pom {
        name = "Lattice"
        description = "Library for creating Minecraft configuration GUIs"
        url = "https://github.com/Moulberry/Lattice"
        inceptionYear = "2025"
        packaging = "jar"

        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/license/mit"
            }
        }

        developers {
            developer {
                name = "Moulberry"
                url = "https://github.com/Moulberry"
            }
        }

        issueManagement {
            system = "GitHub"
            url = "https://github.com/Moulberry/Lattice/issues"
        }

        scm {
            url = "https://github.com/Moulberry/Lattice/"
            connection = "scm:git:git://github.com/Moulberry/Lattice.git"
            developerConnection = "scm:git:ssh://git@github.com/Moulberry/Lattice.git"
        }
    }
}
