import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
  id("java") // Java support
  alias(libs.plugins.kotlin) // Kotlin support
  kotlin("plugin.serialization") version "1.9.22" // Kotlin Serialization
  alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
  alias(libs.plugins.changelog) // Gradle Changelog Plugin
  alias(libs.plugins.qodana) // Gradle Qodana Plugin
  alias(libs.plugins.kover) // Gradle Kover Plugin
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

val signingConfigPath = providers.gradleProperty("signingConfigPath")
  .orElse("E:/theOne/app-key/config.txt")
val signingValuesProvider = providers.provider {
  loadSigningValues(signingConfigPath.get(), layout.buildDirectory.get().asFile)
}
val signingAvailableProvider = signingValuesProvider.map { it.isComplete() }

// Set the JVM language level used to build the project.
kotlin {
  jvmToolchain(17)
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
  }
}

// Configure project's dependencies
repositories {
  mavenCentral()

  // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
  intellijPlatform {
    defaultRepositories()
  }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/version_catalogs.html
dependencies {
  testImplementation(libs.junit)
  testImplementation(libs.opentest4j)

  // Quick Note Plugin Dependencies
  // SnakeYAML for YAML parsing (Front Matter)
  implementation("org.yaml:snakeyaml:2.2")

  // CommonMark for Markdown parsing and rendering
  implementation("org.commonmark:commonmark:0.22.0")
  implementation("org.commonmark:commonmark-ext-gfm-tables:0.22.0")
  implementation("org.commonmark:commonmark-ext-heading-anchor:0.22.0")

  // Lucene query parsing; core/analysis come from the IDE-bundled Lucene.
  implementation("org.apache.lucene:lucene-queryparser:9.11.1") {
    exclude(group = "org.apache.lucene", module = "lucene-core")
  }

  // Mock API Server Dependencies
  // JDK HttpServer (no external server dependency)

  // Kotlin serialization for JSON
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

  // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
  intellijPlatform {
    intellijIdea(providers.gradleProperty("platformVersion"))
    bundledLibrary("lib/modules/intellij.libraries.lucene.common.jar")

    // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
    bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
    plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

    // Module Dependencies. Uses `platformBundledModules` property from the gradle.properties file for bundled IntelliJ Platform modules.
    bundledModules(providers.gradleProperty("platformBundledModules").map { it.split(',') })

    testFramework(TestFrameworkType.Platform)
  }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
  pluginConfiguration {
    name = providers.gradleProperty("pluginName")
    version = providers.gradleProperty("pluginVersion")

    // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
    description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
      val start = "<!-- Plugin description -->"
      val end = "<!-- Plugin description end -->"

      with(it.lines()) {
        if (!containsAll(listOf(start, end))) {
          throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
        }
        subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
      }
    }

    val changelog = project.changelog // local variable for configuration cache compatibility
    // Get the latest available change notes from the changelog file
    changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
      with(changelog) {
        renderItem(
          (getOrNull(pluginVersion) ?: getUnreleased())
            .withHeader(false)
            .withEmptySections(false),
          Changelog.OutputType.HTML,
        )
      }
    }

    ideaVersion {
      sinceBuild = providers.gradleProperty("pluginSinceBuild")
    }
  }

  signing {
    val signingValues = signingValuesProvider.get()
    signingValues.certificatePath?.let { certificateChainFile.set(file(it)) }
    signingValues.privateKeyPath?.let { privateKeyFile.set(file(it)) }
    signingValues.password?.let { password.set(it) }
  }

  publishing {
    token = providers.environmentVariable("PUBLISH_TOKEN")
    // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
    // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
    // https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html#specifying-a-release-channel
    channels = providers.gradleProperty("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
  }

  pluginVerification {
    ides {
      recommended()
    }
  }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
  groups.empty()
  repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

// Configure Gradle Kover Plugin - read more: https://kotlin.github.io/kotlinx-kover/gradle-plugin/#configuration-details
kover {
  reports {
    total {
      xml {
        onCheck = true
      }
    }
  }
}

tasks {
  wrapper {
    gradleVersion = providers.gradleProperty("gradleVersion").get()
  }

  val searchableOptionsEnabled = providers.gradleProperty("enableSearchableOptions")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)

  withType<org.jetbrains.intellij.platform.gradle.tasks.BuildSearchableOptionsTask> {
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    systemProperty("user.variant", "")
    systemProperty("idea.locale", "en")
    jvmArgs("-Duser.language=en", "-Duser.country=US", "-Duser.variant=")
    environment("LANG", "en_US")
    environment("LC_ALL", "en_US")
    onlyIf { searchableOptionsEnabled.get() }
  }

  named("prepareJarSearchableOptions") {
    onlyIf { searchableOptionsEnabled.get() }
  }

  named("jarSearchableOptions") {
    onlyIf { searchableOptionsEnabled.get() }
  }

  val signingAvailable = signingAvailableProvider

  val pluginOutputDir = providers.gradleProperty("pluginOutputDir")
    .map { file(it) }
    .orElse(layout.buildDirectory.dir("out").map { it.asFile })
  val pluginArchiveName = providers.gradleProperty("pluginArchiveName")
    .orElse("quick-note-${project.version}.zip")
  val unsignedPluginZip = layout.buildDirectory
    .file("distributions/${rootProject.name}-${project.version}.zip")
  val signedPluginZip = layout.buildDirectory
    .file("distributions/${rootProject.name}-${project.version}-signed.zip")
  val pluginZip = signingAvailableProvider.flatMap { available ->
    if (available) signedPluginZip else unsignedPluginZip
  }

  register<Copy>("packagePlugin") {
    dependsOn("buildPlugin")
    from(layout.buildDirectory.dir("distributions")) {
      include(pluginZip.get().asFile.name)
    }
    into(pluginOutputDir)
    rename { pluginArchiveName.get() }
    doLast {
      val outputFile = pluginOutputDir.get().resolve(pluginArchiveName.get())
      println("Packaged plugin: ${outputFile.absolutePath}")
    }
  }

  named("buildPlugin") {
    finalizedBy("signPlugin", "packagePlugin")
  }

  named("signPlugin") {
    onlyIf { signingAvailable.get() }
  }

  named("packagePlugin") {
    mustRunAfter("signPlugin")
  }

  publishPlugin {
    dependsOn(patchChangelog)
  }
}

data class SigningValues(
  val certificatePath: String?,
  val privateKeyPath: String?,
  val password: String?
) {
  fun isComplete(): Boolean {
    return !certificatePath.isNullOrBlank() &&
            File(certificatePath).exists() &&
            !privateKeyPath.isNullOrBlank() &&
            File(privateKeyPath).exists() &&
            !password.isNullOrBlank()
  }
}

fun loadSigningValues(configPath: String, buildDir: File): SigningValues {
  val envChain = System.getenv("CERTIFICATE_CHAIN")?.takeIf { it.isNotBlank() }
  val envKey = System.getenv("PRIVATE_KEY")?.takeIf { it.isNotBlank() }
  val envPassword = System.getenv("PRIVATE_KEY_PASSWORD")?.takeIf { it.isNotBlank() }

  val config = readConfigFile(configPath)
  val certificatePath = if (envChain != null) {
    writeTempPem(buildDir, "certificate-chain.pem", envChain)
  } else {
    config["CERTIFICATE_PATH"]
  }
  val privateKeyPath = if (envKey != null) {
    writeTempPem(buildDir, "private-key.pem", envKey)
  } else {
    config["PRIVATE_KEY_PATH"]
  }

  val password = envPassword
    ?: config["PRIVATE_KEY_PASSWORD_VALUE"]
    ?: config["KEY_PASSWORD"]
    ?: config["KEYSTORE_PASSWORD"]

  return SigningValues(certificatePath, privateKeyPath, password)
}

fun readConfigFile(path: String): Map<String, String> {
  val file = File(path)
  if (!file.exists()) {
    return emptyMap()
  }

  return file.readLines(Charsets.UTF_8)
    .mapNotNull { line ->
      val trimmed = line.trim()
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        return@mapNotNull null
      }
      val index = trimmed.indexOf('=')
      if (index <= 0) {
        return@mapNotNull null
      }
      val key = trimmed.substring(0, index).trim()
      val value = trimmed.substring(index + 1).trim()
      key to value
    }
    .toMap()
}

fun writeTempPem(buildDir: File, fileName: String, content: String): String {
  val dir = File(buildDir, "tmp/signing")
  if (!dir.exists()) {
    dir.mkdirs()
  }
  val file = File(dir, fileName)
  file.writeText(content, Charsets.UTF_8)
  return file.absolutePath
}

intellijPlatformTesting {
  runIde {
    register("runIdeForUiTests") {
      task {
        jvmArgumentProviders += CommandLineArgumentProvider {
          listOf(
            "-Drobot-server.port=8082",
            "-Dide.mac.message.dialogs.as.sheets=false",
            "-Djb.privacy.policy.text=<!--999.999-->",
            "-Djb.consents.confirmation.enabled=false",
          )
        }
      }

      plugins {
        robotServerPlugin()
      }
    }
  }
}
