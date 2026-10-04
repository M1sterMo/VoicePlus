@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ManagedVirtualDevice
import java.util.Properties

plugins {
  id("voice.app")
  id("voice.compose")
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.metro)
  alias(libs.plugins.aboutlibraries)
}

val keystoreProps = Properties()
val keystorePropsFile = rootProject.file("signing/keystore.properties")
val debugVersionNameOverride = providers.gradleProperty("voice.debugVersionName")
val releaseVersionNameOverride = providers.gradleProperty("voice.releaseVersionName")
val releaseVersionCodeOverride = providers.gradleProperty("voice.releaseVersionCode")
check(releaseVersionNameOverride.isPresent == releaseVersionCodeOverride.isPresent) {
  "voice.releaseVersionName and voice.releaseVersionCode must be supplied together"
}
if (keystorePropsFile.exists()) {
  keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}

android {

  namespace = "voice.app"

  androidResources {
    generateLocaleConfig = true
  }

  dependenciesInfo {
    // disable the dependencies info in apks to allow reproducible builds
    // see https://github.com/VoiceAudiobook/Voice/discussions/2862#discussioncomment-13622836
    includeInApk = false
  }

  defaultConfig {
    applicationId = "com.github.mistermo_vibecode.voiceplus"
    // Keep these as literals so F-Droid's tag checker can discover releases.
    versionName = "1.29"
    versionCode = 5408022

    testInstrumentationRunner = "voice.app.VoiceJUnitRunner"
  }

  flavorDimensions += "distribution"
  productFlavors {
    register("libre") {
      dimension = "distribution"
    }
    register("proprietary") {
      dimension = "distribution"
    }
  }

  if (keystorePropsFile.exists()) {
    signingConfigs {
      create("release") {
        storeFile = rootProject.file(keystoreProps.getProperty("storeFile") as String)
        storePassword = keystoreProps.getProperty("storePassword") as String
        keyAlias = keystoreProps.getProperty("keyAlias") as String
        keyPassword = keystoreProps.getProperty("keyPassword") as String
      }
    }
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      if (keystorePropsFile.exists()) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    getByName("debug") {
      isMinifyEnabled = false
      isShrinkResources = false
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
    }
    all {
      setProguardFiles(
        listOf(
          getDefaultProguardFile("proguard-android-optimize.txt"),
          "proguard.pro",
        ),
      )
    }
  }

  testOptions {
    unitTests {
      isReturnDefaultValues = true
      isIncludeAndroidResources = true
    }
    animationsDisabled = true
    execution = "ANDROIDX_TEST_ORCHESTRATOR"
    managedDevices {
      allDevices.create("voiceDevice", ManagedVirtualDevice::class.java) {
        device = "Pixel 9"
        apiLevel = 33
      }
    }
  }

  lint {
    checkDependencies = true
    ignoreTestSources = true
    checkReleaseBuilds = false
    warningsAsErrors = providers.gradleProperty("voice.warningsAsErrors").get().toBooleanStrict()
    baseline = file("lint-baseline.xml")
  }

  packaging {
    jniLibs {
      keepDebugSymbols += "**/*.so"
    }
    with(resources.pickFirsts) {
      add("META-INF/atomicfu.kotlin_module")
      add("META-INF/core.kotlin_module")
    }
  }

  buildFeatures {
    buildConfig = true
  }
}

androidComponents {
  onVariants(selector().withBuildType("release")) { variant ->
    if (releaseVersionNameOverride.isPresent) {
      variant.outputs.forEach { output ->
        output.versionName.set(releaseVersionNameOverride)
        output.versionCode.set(releaseVersionCodeOverride.map(String::toInt))
      }
    }
  }

  onVariants(selector().withBuildType("debug")) { variant ->
    if (debugVersionNameOverride.isPresent) {
      variant.outputs.forEach { output ->
        output.versionName.set(debugVersionNameOverride)
      }
    }
  }
}

dependencies {
  implementation(projects.core.strings)
  implementation(projects.core.ui)
  implementation(projects.core.common)
  implementation(projects.core.data.api)
  implementation(projects.core.data.impl)
  implementation(projects.core.playback)
  implementation(projects.core.scanner)
  implementation(projects.core.featureflag)
  implementation(projects.core.initializer)
  implementation(projects.features.playbackScreen)
  implementation(projects.navigation)
  implementation(projects.core.sleepTimer.api)
  implementation(projects.core.sleepTimer.impl)
  implementation(projects.features.sleepTimer)
  implementation(projects.features.settings)
  implementation(projects.features.folderPicker)
  implementation(projects.features.bookOverview)
  implementation(projects.core.search)
  implementation(projects.features.cover)
  implementation(projects.features.chapterEditor)
  implementation(projects.features.backup)
  implementation(projects.features.characterList)
  implementation(projects.features.hiddenBooks)
  implementation(projects.features.listeningLog)
  implementation(projects.features.listeningStats)
  implementation(projects.core.documentfile)
  implementation(projects.features.onboarding)
  implementation(projects.features.bookmark)
  implementation(projects.features.widget)

  implementation(libs.appCompat)
  implementation(libs.datastore)

  implementation(libs.navigation3.ui)

  implementation(libs.serialization.json)

  implementation(libs.coil)

  implementation(projects.features.review.noop)

  debugImplementation(projects.core.logging.debug)

  implementation(libs.androidxCore)

  testImplementation(libs.junit)
  testImplementation(libs.mockk)

  implementation(libs.media3.exoplayer)
  implementation(libs.media3.session)

  testImplementation(libs.androidX.test.runner)
  testImplementation(libs.androidX.test.junit)
  testImplementation(libs.androidX.test.core)
  testImplementation(libs.robolectric)
  testImplementation(libs.coroutines.test)
  testImplementation(kotlin("reflect"))

  debugImplementation(libs.compose.ui.testManifest)

  androidTestImplementation(platform(libs.compose.bom))
  androidTestImplementation(libs.androidX.test.espresso.core)
  androidTestImplementation(libs.androidX.test.runner)
  androidTestImplementation(libs.androidX.test.rules)
  androidTestImplementation(libs.androidX.test.junit)
  androidTestImplementation(libs.media3.testUtils.core)
  androidTestImplementation(libs.koTest.assert)
  androidTestImplementation(libs.androidX.test.services)
  androidTestImplementation(libs.androidX.test.uiautomator)
  androidTestImplementation(libs.compose.ui.testJunit)
  androidTestImplementation(libs.coroutines.test)
  androidTestUtil(libs.androidX.test.orchestrator)
}
