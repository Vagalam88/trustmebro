# TrustMeBro — File Manifest

Every file in the project. Claude Code should create ALL of these.

## Root

```
TrustMeBro/
├── build.gradle.kts                          # AGP 9.1.0 plugin declaration only
├── settings.gradle.kts                       # pluginManagement + dependencyResolutionManagement + include(:app)
├── gradle.properties                         # org.gradle.jvmargs, android.useAndroidX, etc.
├── README.md                                 # Project documentation
├── CLAUDE.md                                 # This spec (copy from the one provided)
└── gradle/
    └── wrapper/
        └── gradle-wrapper.properties         # Gradle 9.3.1
```

## App Module

```
app/
├── build.gradle.kts                          # com.android.application + compose compiler plugin + all deps
├── proguard-rules.pro                        # Bouncy Castle keep rules
└── src/
    └── main/
        ├── AndroidManifest.xml               # INTERNET permission, MainActivity, APK intent-filter
        ├── java/com/trustmebro/
        │   ├── MainActivity.kt               # Splash screen + Compose setContent
        │   ├── crypto/
        │   │   ├── HashEngine.kt             # Streaming hash with MessageDigest
        │   │   ├── PGPEngine.kt              # Bouncy Castle PGP verification
        │   │   └── APKParser.kt              # ZIP parsing + cert extraction
        │   ├── utils/
        │   │   └── FileClassifier.kt         # File type detection
        │   └── ui/
        │       ├── MainViewModel.kt          # MVVM state management
        │       ├── theme/
        │       │   └── Theme.kt              # Dark theme + TMB colors
        │       ├── components/
        │       │   └── Components.kt         # FileCard, HashDisplay, StatusBadge, etc.
        │       └── screens/
        │           └── MainScreen.kt         # Main unified screen
        └── res/
            ├── values/
            │   ├── strings.xml               # app_name = TrustMeBro
            │   ├── colors.xml                # TMB color palette
            │   └── themes.xml                # Base theme + splash theme
            ├── drawable/
            │   ├── ic_splash.xml             # Splash screen vector icon
            │   ├── ic_launcher_foreground.xml # Adaptive icon foreground (shield)
            │   └── ic_launcher_background.xml # Adaptive icon background (dark)
            ├── mipmap-hdpi/
            │   ├── ic_launcher.xml           # Adaptive icon ref
            │   └── ic_launcher_round.xml     # Adaptive icon ref (round)
            ├── mipmap-xhdpi/
            │   ├── ic_launcher.xml
            │   └── ic_launcher_round.xml
            ├── mipmap-xxhdpi/
            │   ├── ic_launcher.xml
            │   └── ic_launcher_round.xml
            ├── mipmap-xxxhdpi/
            │   ├── ic_launcher.xml
            │   └── ic_launcher_round.xml
            └── xml/
                ├── data_extraction_rules.xml # Backup rules (API 31+)
                └── backup_rules.xml          # Backup rules (legacy)
```

## Total: 33 files

## Key Technical Decisions

- NO `org.jetbrains.kotlin.android` plugin — AGP 9.1 has built-in Kotlin
- YES `org.jetbrains.kotlin.plugin.compose` version `2.2.10` — required for Compose with Kotlin 2.x
- NO `composeOptions {}` block — the compose plugin handles compiler config
- `FlowRow` needs `@OptIn(ExperimentalLayoutApi::class)`
- Use `HorizontalDivider` not deprecated `Divider`
- Gradle 9.3.1 (not 9.0.1)
- All crypto runs on `Dispatchers.IO` with coroutines
- File access via SAF (`OpenMultipleDocuments`) — no runtime permissions needed
