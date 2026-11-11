import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

/**
 * ⭐⭐ Release signing, read from OUTSIDE the repository.
 *
 * ⚠⚠ `../.secrets/nightmare-keystore/` is a sibling of the repo, not a
 * gitignored path inside it. Gitignore is one `git add -f` away from
 * committing a private key; a file that is not in the tree at all cannot be
 * committed by accident.
 *
 * ⚠⚠ A MISSING keystore is not an error. The release build still runs and
 * produces an unsigned APK, so a fresh clone on another machine can build
 * and test the release variant without holding the key. Failing here would
 * make the project unbuildable for everyone except one laptop, which is a
 * strange thing for a public repository to do.
 *
 * ⚠ Android identifies an app by its signature: lose this key and no future
 * build can update an installed copy. `facefusion-mobile` lost one already.
 * The README beside the keystore says what to do about it.
 */
val keystoreProps = Properties().apply {
    val f = rootProject.file("../.secrets/nightmare-keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}


android {
    namespace = "com.abrah.nightmare"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.abrah.nightmare"
        // 31 to match DreamUI. The backend and QNN runtime set the real floor;
        // there is no reason to support anything the NPU path cannot run on.
        minSdk = 31
        targetSdk = 35
        // ⚠ Bump on EVERY push. The harness prints versionName, and two builds
        // sharing a version makes a failure report unattributable -- DreamUI lost
        // five releases to this exact mistake.
        //
        // ⚠⚠ THREE-part versionName, and an ordinary push increments the
        // PATCH: 1.2.0 -> 1.2.1 -> 1.2.2. ⚠⚠ Getting the FORMAT right is not
        // getting the GRANULARITY right -- this went 1.0.12 -> 1.1.0 -> 1.2.0,
        // a minor bump per push, which is what the rule exists to stop. The
        // minor moves only when a release is called a release. ⚠ versionCode
        // stays a plain incrementing integer; Android requires that.
        versionCode = 660
        versionName = "1.6.353"
        // ⭐ fix31 (friend's audit P1-1): the touch/frame probe is a diagnostic
        // tool, not a product feature — release builds must not write to
        // external storage from every touch. dev keeps it (this push build is
        // what the phone runs; the probe is how every stall report got made).
        buildConfigField("boolean", "PROBE", "false")
        ndk { abiFilters += "arm64-v8a" }

        // The plugin runtime and the NPU runner, both built from source.
        // ⚠ arm64 only, like everything else here: the NPU path has no other
        // target, and building quickjs.c (2.1 MB of C) four times for ABIs that
        // can never run a model is pure build time.
        //
        // ⚠⚠ `c++_static`, not `none`. It was `none` while the only native code
        // was `nmjs.c` (C, no STL); `nmqnn.cpp` uses std::string/vector/mutex
        // and does not compile without one. ⭐ STATIC rather than shared so the
        // STL is linked into libnmqnn.so and no `libc++_shared.so` has to be
        // packaged -- libnmjs.so is C and links none of it either way.
        // (2026-11-04: on-device build — the .so set, libnmjs/libnmqnn
        // included, is grafted from the installed 1.6.272 APK into jniLibs
        // instead of compiled; no NDK/CMake on this phone.)
    }

// ⚠ Our own C, unlike the backend: libstable_diffusion_core.so is BUILT
    // ELSEWHERE and copied into jniLibs by tools/stage_backend.ps1 (it is an
    // executable, and its tree is CC BY-NC and uncommittable). libnmjs.so is
    // ours plus MIT QuickJS, so it is a normal Gradle native build -- and that
    // means it cannot silently go stale the way a staged binary can.
    // (2026-11-04: on-device build — externalNativeBuild removed entirely; the
    // full .so set ships from jniLibs, grafted from the installed APK.)

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(
                    "../.secrets/nightmare-keystore/" + keystoreProps.getProperty("storeFile")
                )
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // ⚠ AGP leaves v3 OFF by default, and v3 is the scheme that
                // makes KEY ROTATION possible later. It cannot be added
                // retroactively to an APK people have already installed, so it
                // goes on before the first release rather than after.
                //
                // ⚠⚠ MEASURED 2026-09-11: with v3 on, AGP emits a v3 block
                // INSTEAD of v2, not alongside it -- `apksigner verify` reports
                // v2 false, v3 true however `enableV2Signing` is set. That is
                // fine here and only here: v3 needs API 28 and minSdk is 31, so
                // every device that can install this app can verify it. Lower
                // the minSdk and this line becomes a bug.
                //
                // v1 is dead weight at minSdk 31; v4 needs a separate .idsig
                // file that a sideloaded APK has no use for.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        // ⚠ Not minified, unlike DreamUI's debug build -- and that is only
        // tenable because the dex is small. MEASURED 2026-09-07: with
        // material-icons-extended on the classpath the unminified APK was
        // 55.6 MB, of which 55.0 MB was dex (44.4 + 10.6). Dropping that one
        // unused dependency took it to the size below. DreamUI hit the identical
        // wall at 43.8 MB and solved it by minifying debug instead.
        // ⇒ Revisit the moment the dex grows again; unminified is a convenience,
        // not a principle, and stack traces are what it buys.
        debug {
            isMinifyEnabled = false
        }
        release {
            // ⭐ fix31 (friend's audit P1-1): the probe ships OFF in release —
            // see the PROBE field in defaultConfig.
            buildConfigField("boolean", "PROBE", "false")
            // ⚠ Null when there is no keystore, which leaves the APK unsigned
            // rather than failing the build.
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // ⭐ The PUSH build: what goes to the phone between releases. Signed with the
        // RELEASE key -- so it installs over the phone's release build and keeps the
        // 12 GB of models and every flow (notes/HANDOFF.md §3b) -- but with no R8 and
        // no lint. Measured 2026-09-27: assembleRelease was 89 s, of which R8 was 61 s
        // and lintVital 67 s running beside it; Kotlin itself was 11 s.
        //
        // ⚠ Not a substitute for a release: R8 cannot see calls made from C
        // (`nmjs.c` looks its JNI entry points up by string), so an R8-only failure
        // shows up ONLY in assembleRelease. A GitHub release is always built with it.
        // Same applicationId and version on purpose: it is the same app to Android.
        create("dev") {
            initWith(getByName("release"))
            // ⭐ fix35n: R8 ON for the push build too. The dex had grown to
            // 23.8 MB unminified (the revisit the note above promised), and
            // the JNI surface the shrinker could break is pinned by keep
            // rules in proguard-rules.pro. Stack traces come back obfuscated;
            // that is the price, and it is worth 19 MB on every push.
            isMinifyEnabled = true
            isShrinkResources = true
            matchingFallbacks += listOf("release")
            // ⭐ fix33 (user 2026-11-06): the probe is RETIRED. It is a
            // diagnostic tool, and every measurement it could give has been
            // taken — but it also cost the thing it was measuring: three
            // `pointerInput` pass observers sat in the hit-test path of every
            // touch, and one of them (the `bar` layer, fix32) intercepted the
            // whole screen. The instrumentation stays in the source (off, not
            // deleted) so a future stall report can flip one line and get the
            // same black box back.
            buildConfigField("boolean", "PROBE", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // foxlet-release.aar ships Kotlin 2.4 metadata; 2.0.21 compiler reads up
        // to 2.1 natively - skip the version gate (proto schema is compatible).
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Roborazzi renders through Robolectric, which needs real resources.
            isIncludeAndroidResources = true
        }
    }

    packaging {
        jniLibs {
            // extractNativeLibs=true: the forked backend must exist as a real
            // file in nativeLibraryDir so it can be exec'd. Execution from the
            // writable app data dir is blocked; nativeLibraryDir is not
            // writable, so it is the one allowed location. Set now so the
            // packaging is right before the binary arrives.
            useLegacyPackaging = true
            keepDebugSymbols += "**/libstable_diffusion_core.so"
            // proot-on-arm64: NDK strip binaries are x86_64, un-runnable.
            // Grafted .so files are official release artifacts (already
            // stripped), so keep symbols for all of them and skip stripping.
            keepDebugSymbols += "**/*.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ⚠ Compose BOM 2024.10.01 gives material3 1.3.1, which is NOT Material 3
    // Expressive (needs 1.4+). Pinned here anyway because this exact set is
    // already in the Gradle cache, so the first build proves the scaffold rather
    // than dependency resolution. The Expressive bump is a separate, isolated
    // change -- see docs/UI.md section 1.
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    // ⭐ Prompt translation (Russian/Chinese → English) — Foxlet, built FROM SOURCE
    // into app/libs by tools/build_foxlet.sh and gitignored like jniLibs
    // (docs/TRANSLATE.md). A local AAR carries no dependency metadata, so its
    // coroutines dependency is declared here.
    implementation(files("libs/foxlet-release.aar"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // ⚠ material-icons-extended is deliberately ABSENT. It is thousands of
    // generated vector classes and costs ~55 MB of dex on its own (measured
    // above) whether or not a single icon is referenced. Add individual icons,
    // or the base `material-icons-core`, if one is actually needed.

    // ⭐ Tap to select (`docs/SEGMENTER.md`): SAM 2.1 on ORT's CPU build, as
    // DreamUI. NOT onnxruntime-android-qnn — QNN cannot create an HTP device on
    // this SoC through ORT, and the split graph costs 28 ms a tap on the CPU.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.29.0")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // ⭐ The inner loop of docs/UI.md: previews are the agent's cheap eyes, so
    // the tooling that renders them is a first-class dependency, not an extra.
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Roborazzi: composables to PNG on the JVM, no device, no IDE.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.26.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.26.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.26.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// ⚠ lintVital runs on every non-debuggable variant, and `dev` is one (the release
// config minus R8). It is the RELEASE gate, not the push gate.
tasks.configureEach {
    if (name.startsWith("lintVital") && name.contains("Dev")) enabled = false
}

// ⭐⭐ The unit suite in PARALLEL JVMs that are RECYCLED (2026-10-14). One JVM running every class let
// something build up (no single class does it: a bisect over the 54 suspects found none alone) until
// Compose never went idle, and 22 later Compose tests each sat out Espresso's 60 s timeout: 28 minutes
// for a 6-minute suite. A fresh JVM every [forkEvery] classes leaves nothing to build up; three at once
// use the cores (16 GB PC: ~1 GB each beside the 4 GB daemon). `FastIdle` caps any hang at 5 s.
// `-PtestOrder` logs each class as it starts: the way to bisect the next leak.
tasks.withType<Test>().configureEach {
    maxParallelForks = 3
    forkEvery = 30
    if (project.hasProperty("testOrder")) testLogging { events("started") }
}

// ⭐⭐ `ResultsFullscreenTest` runs in its OWN JVM, after the main unit tests. It
// composes the real `HarnessScreen` (sheet window, view model, viewer Dialog), and
// in the shared JVM something it starts outlived it: later Compose classes failed
// "Compose did not get idle" in the full suite only (2026-10-01). Isolated, it
// still runs on every `testDebugUnitTest` / `verifyRoborazziDebug`.
afterEvaluate {
    val base = tasks.named<Test>("testDebugUnitTest")
    base.configure { filter { excludeTestsMatching("*ResultsFullscreenTest") } }
    val isolated = tasks.register<Test>("testResultsFullscreen") {
        val b = base.get()
        testClassesDirs = b.testClassesDirs
        classpath = b.classpath
        jvmArgumentProviders.addAll(b.jvmArgumentProviders)
        jvmArgs(b.jvmArgs ?: emptyList<String>())
        systemProperties(b.systemProperties)
        javaLauncher.set(b.javaLauncher)
        dependsOn(b.dependsOn)
        mustRunAfter(b)
        filter { includeTestsMatching("*ResultsFullscreenTest") }
    }
    base.configure { finalizedBy(isolated) }
}
