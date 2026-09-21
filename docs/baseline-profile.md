# Baseline profile

`app/src/main/baseline-prof.txt` is currently a **hand-authored starter profile**
(`androidx.profileinstaller` applies it on first run). Hand-written profiles are a decent
first step, but they always miss the real hotspots: the list was written by reading the
code, not by measuring it (issue #37).

To get a profile that actually matches the device, generate it with Macrobenchmark:

## 1. Add a macrobenchmark module

`benchmark/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gothwad.launcher.benchmark"
    compileSdk = 34
    targetProjectPath = ":app"
    defaultConfig { minSdk = 24; targetSdk = 34; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    buildTypes { create("benchmark") { isDebuggable = false; matchingFallbacks += listOf("release") } }
    flavorDimensions += "benchmark"
}

dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.2.4")
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
```

Then in `settings.gradle.kts`: `include(":benchmark")`, and in `app/build.gradle.kts` add a
`benchmark` build type with `matchingFallbacks`.

## 2. Write the generator

`benchmark/src/main/java/.../BaselineProfileGenerator.kt`:

```kotlin
@OptIn(ExperimentalBaselineProfilesApi::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.gothwad.launcher") {
        pressHome()
        startActivityAndWait()
        // Walk the sections the way a user does, so scroll paths are included.
        repeat(3) { device.pressDPadDown() }
        device.pressDPadRight(); device.pressDPadDown()
    }
}
```

## 3. Generate and commit

```bash
./gradlew :benchmark:generateBaselineProfile
```

Copy the produced `baseline-prof.txt` over `app/src/main/baseline-prof.txt` and commit it.
Until then, treat the bundled profile as a starter, not as a measured optimisation.
