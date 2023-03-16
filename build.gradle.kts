import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

plugins {
  kotlin("jvm")
  kotlin("kapt")
  kotlin("plugin.serialization")
  application
  id("com.bmuschko.docker-java-application")
}

group = "org.jraf"
version = "1.0.0"

repositories {
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


  implementation(Square.okHttp3.okHttp)
  implementation(Square.okHttp3.loggingInterceptor)

  implementation(Square.retrofit2.retrofit)
  implementation(Square.retrofit2.converter.moshi)
  implementation(Square.moshi)
  kapt(Square.Moshi.kotlinCodegen)

  implementation(KotlinX.cli)

  testImplementation(kotlin("test"))
}

application {
  mainClass.set("org.jraf.slackchatgptbot.MainKt")
}

docker {
  javaApplication {
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
  val originalInstructions = instructions.get().toMutableList()
  val fromInstructionIndex = originalInstructions
    .indexOfFirst { item -> item.keyword == Dockerfile.FromInstruction.KEYWORD }
  originalInstructions.removeAt(fromInstructionIndex)
  val baseImage = Dockerfile.FromInstruction(Dockerfile.From("adoptopenjdk/openjdk11-openj9:x86_64-ubuntu-jre-11.0.18_10_openj9-0.36.1"))
  originalInstructions.add(0, baseImage)
  instructions.set(originalInstructions)

  environmentVariable("MALLOC_ARENA_MAX", "4")
}

// `./gradlew refreshVersions` to update dependencies
// `./gradlew distZip` to create a zip distribution
// `DOCKER_USERNAME=<your docker hub login> DOCKER_PASSWORD=<your docker hub password> ./gradlew dockerPushImage` to build and push the image
