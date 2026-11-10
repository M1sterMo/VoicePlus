plugins {
  id("voice.library")
  id("voice.compose")
  alias(libs.plugins.metro)
}

dependencies {
  implementation(projects.core.common)
  implementation(projects.core.strings)
  implementation(projects.core.ui)
  implementation(projects.core.data.api)
  implementation(projects.core.initializer)

  testImplementation(libs.bundles.testing.jvm)
}
