import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  application
  id("com.bmuschko.docker-java-application")
}

group = "org.jraf"
version = "1.0.0"

repositories {
  mavenLocal()
  mavenCentral()
}

dependencies {
  // Slf4j
  implementation("org.slf4j", "slf4j-api", "_")
  implementation("org.slf4j", "slf4j-simple", "_")

  implementation(KotlinX.coroutines.jdk9)

  // Ktor
  implementation(Ktor.client.core)
  implementation(Ktor.client.contentNegotiation)
  implementation(Ktor.client.auth)
  implementation(Ktor.client.logging)
  implementation("io.ktor:ktor-client-websockets:_")
  implementation(Ktor.plugins.serialization.kotlinx.json)
  implementation(Ktor.client.okHttp)

  implementation(KotlinX.serialization.json)

  // Slack
  implementation("org.jraf.klibslack", "klibslack", "_")

  implementation(KotlinX.cli)

  testImplementation(kotlin("test"))
}

application {
  mainClass.set("org.jraf.slackchatgptbot.MainKt")
}

docker {
  javaApplication {
    // Use OpenJ9 instead of the default one
    baseImage.set("adoptopenjdk/openjdk11-openj9:x86_64-ubuntu-jre-11.0.18_10_openj9-0.36.1")
    maintainer.set("BoD <BoD@JRAF.org>")
    ports.set(emptyList())
    images.add("bodlulu/${rootProject.name}:latest")
    jvmArgs.set(listOf("-Xms16m", "-Xmx128m"))
  }
  registryCredentials {
    username.set(System.getenv("DOCKER_USERNAME"))
    password.set(System.getenv("DOCKER_PASSWORD"))
  }
}

tasks.withType<DockerBuildImage> {
  platform.set("linux/amd64")
}

tasks.withType<Dockerfile> {
  // See https://github.com/bmuschko/gradle-docker-plugin/issues/1173
  instructions.set(
    instructions.get().map { item ->
      if (item.keyword == Dockerfile.EntryPointInstruction.KEYWORD) {
        Dockerfile.GenericInstruction("""ENTRYPOINT ["java", "-Xms16m", "-Xmx128m", "-cp", "/app/resources:/app/classes:/app/libs/*", "org.jraf.slackchatgptbot.MainKt"]""")
      } else {
        item
      }
    }
  )

  environmentVariable("MALLOC_ARENA_MAX", "4")
}

// `./gradlew refreshVersions` to update dependencies
// `./gradlew distZip` to create a zip distribution
// `DOCKER_USERNAME=<your docker hub login> DOCKER_PASSWORD=<your docker hub password> ./gradlew dockerPushImage` to build and push the image
