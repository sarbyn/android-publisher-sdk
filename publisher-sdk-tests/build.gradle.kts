plugins {
  id("com.android.library")
  kotlin("android")
}

androidLibModule()

android {
  defaultConfig {
    multiDexEnabled = true
  }
}

dependencies {
  implementation(project(":publisher-sdk"))
  implementation(Deps.Android.Support.MultiDex)

  androidTestImplementation(project(":test-utils"))
  androidTestImplementation(Deps.Android.Support.SupportCoreUtils)
  androidTestImplementation(Deps.Android.Test.Runner)
  androidTestImplementation(Deps.Android.Test.Rules)
  androidTestImplementation(Deps.Mockito.Android)
  androidTestImplementation(Deps.Square.Tape.Tape)
  androidTestImplementation(Deps.Google.AdMob) {
    exclude(group = Deps.Android.Support.group)
  }

  androidTestImplementation(Deps.MoPub.Banner) {
    isTransitive = true
    exclude(group = Deps.Android.Support.group)
  }

  // Debug is needed because MoPub need some activities to be declare in the AndroidManifest.xml
  debugImplementation(Deps.MoPub.Interstitial) {
    isTransitive = true
    exclude(group = Deps.Android.Support.group)
  }
}
