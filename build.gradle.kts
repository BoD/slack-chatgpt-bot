
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.bmuschko.gradle.docker.tasks.image.Dockerfile.CopyFileInstruction

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  id("application")
  alias(libs.plugins.dockerJavaApplication)
  alias(libs.plugins.tapmoc)
}

group = "org.jraf"
version = "1.0.0"

application {
  mainClass.set("org.jraf.slackchatgptbot.MainKt")
}

dependencies {
  // Logging
  implementation(libs.klibnanolog)
  implementation(libs.logback)

  // Coroutines
  implementation(libs.kotlinx.coroutines.jdk9)

  // OpenAI
  implementation(libs.klibopenai)

  // Slack
  implementation(libs.klibslack)

  // CLI
  implementation(libs.kotlinx.cli)
}

docker {
  javaApplication {
    baseImage.set("eclipse-temurin:25")
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
  // Install curl
  runCommand("apt-get update")
  runCommand("apt-get install -y curl")

  // Download the OpenTelemetry Java agent
  runCommand("curl -L -O https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar")

  // OpenTelemetry Java agent configuration
  environmentVariable(
    mapOf(
      "JAVA_TOOL_OPTIONS" to "-javaagent:opentelemetry-javaagent.jar",
      "OTEL_SERVICE_NAME" to rootProject.name.lowercase(),
    )
  )

  // Move the COPY instructions to the end
  // See https://github.com/bmuschko/gradle-docker-plugin/issues/1093
  instructions.set(
    instructions.get().sortedBy { instruction ->
      if (instruction.keyword == CopyFileInstruction.KEYWORD) 1 else 0
    }
  )
}

tapmoc {
  java(25)
  kotlin(libs.versions.kotlin.get())
  checkDependencies()
}

// `./gradlew refreshVersions` to update dependencies
// `./gradlew distZip` to create a zip distribution
// `DOCKER_USERNAME=<your docker hub login> DOCKER_PASSWORD=<your docker hub password> ./gradlew dockerPushImage` to build and push the image
