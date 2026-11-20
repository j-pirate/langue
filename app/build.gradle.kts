plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    kotlin("kapt")
}

// Quote Android string literals so names can contain apostrophes, @, quotes and whitespace.
fun androidString(value: String): String = "\"" + value
    .replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

val dedicatedPack = providers.gradleProperty("dedicatedPack")
val generatedAssets = layout.buildDirectory.dir("generated/dedicatedAssets")
val publicationVersionCode = providers.gradleProperty("appVersionCode").getOrElse("1").toIntOrNull()
    ?: error("appVersionCode must be a positive integer")
require(publicationVersionCode > 0) { "appVersionCode must be a positive integer" }
val publicationVersionName = providers.gradleProperty("appVersionName").getOrElse("1.0.0")
require(publicationVersionName.isNotBlank()) { "appVersionName must not be blank" }

// Optional local/CI signing. Credentials are never stored in the project or printed.
val signingVariableNames = listOf("LANGUE_KEYSTORE_FILE", "LANGUE_KEYSTORE_PASSWORD", "LANGUE_KEY_ALIAS", "LANGUE_KEY_PASSWORD")
val releaseSigning = signingVariableNames.associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = releaseSigning.values.any { it != null }
require(!hasReleaseSigning || releaseSigning.values.all { !it.isNullOrBlank() }) {
    "Release signing requires all four LANGUE_KEYSTORE_FILE, LANGUE_KEYSTORE_PASSWORD, LANGUE_KEY_ALIAS and LANGUE_KEY_PASSWORD variables"
}
android {
    namespace = "org.langue.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.langue.universal"
        minSdk = 26
        targetSdk = 35
        versionCode = publicationVersionCode
        versionName = publicationVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("universal") {
            dimension = "distribution"
            resValue("string", "app_name", "Langue")
            resValue("string", "app_subtitle", "Les mots ouvrent le monde.")
            resValue("string", "about_text", "Un carnet de langues personnel, entièrement hors ligne.")
            resValue("color", "brand_accent", "#A44836")
        }
        create("dedicated") {
            dimension = "distribution"
            applicationId = providers.gradleProperty("dedicatedId").getOrElse("org.langue.dedicated")
            resValue("string", "app_name", androidString(providers.gradleProperty("dedicatedName").getOrElse("Langue dédiée")))
            resValue("string", "app_subtitle", androidString(providers.gradleProperty("dedicatedSubtitle").getOrElse("Votre carnet de langues.")))
            resValue("string", "about_text", androidString(providers.gradleProperty("dedicatedAbout").getOrElse("Apprendre à son rythme, hors ligne.")))
            resValue("color", "brand_accent", providers.gradleProperty("dedicatedAccent").getOrElse("#A44836"))
        }
    }
    sourceSets.getByName("dedicated").assets.srcDir(generatedAssets)
    providers.gradleProperty("dedicatedBrandingDir").orNull?.let {
        sourceSets.getByName("dedicated").res.srcDir(file(it))
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            val testUserHome = gradle.gradleUserHomeDir.resolve("langue-test-user-home")
            test.systemProperty("user.home", testUserHome.absolutePath)
            test.doFirst { testUserHome.mkdirs() }
            test.maxHeapSize = "2g"
            test.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            // Java test processes need the same optional proxy settings as the build JVM.
            listOf("https.proxyHost", "https.proxyPort", "http.proxyHost", "http.proxyPort", "javax.net.ssl.trustStore").forEach { key ->
                System.getProperty(key)?.let { test.systemProperty(key, it) }
            }
        }
    }
    if (hasReleaseSigning) {
        signingConfigs.create("production") {
            storeFile = file(releaseSigning.getValue("LANGUE_KEYSTORE_FILE")!!)
            storePassword = releaseSigning.getValue("LANGUE_KEYSTORE_PASSWORD")
            keyAlias = releaseSigning.getValue("LANGUE_KEY_ALIAS")
            keyPassword = releaseSigning.getValue("LANGUE_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("production")
        }
    }
}

evaluationDependsOn(":engine")
val validateDedicatedPack by tasks.registering(JavaExec::class) {
    dependsOn(":engine:classes")
    classpath = project(":engine").extensions.getByType<SourceSetContainer>()["main"].runtimeClasspath
    mainClass.set("org.langue.engine.ValidatePackKt")
    if (dedicatedPack.isPresent) args(file(dedicatedPack.get()).absolutePath)
    doFirst { require(dedicatedPack.isPresent) { "Provide -PdedicatedPack=/absolute/path/to/pack.zip" } }
}

// No educational data in universal builds. A dedicated build must embed a valid external pack.
val prepareDedicatedPack by tasks.registering(Sync::class) {
    dependsOn(validateDedicatedPack)
    into(generatedAssets.map { it.dir("packs") })
    if (dedicatedPack.isPresent) from(file(dedicatedPack.get())) { rename { "initial.zip" } }
    doFirst {
        require(dedicatedPack.isPresent && file(dedicatedPack.get()).isFile) {
            "Provide -PdedicatedPack=/absolute/path/to/validated-pack.zip for dedicated builds."
        }
    }
}
tasks.configureEach {
    if (name.startsWith("preDedicated") && name.endsWith("Build")) dependsOn(prepareDedicatedPack)
}

dependencies {
    implementation(project(":engine"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
