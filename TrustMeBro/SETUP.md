# TrustMeBro — Setup

## Ouvrir dans Android Studio

1. Ouvrir Android Studio
2. **File > Open** → sélectionner ce dossier `TrustMeBro/`
3. Laisser Gradle sync se terminer (il télécharge Gradle 9.3.1 + dépendances)
4. **Build > Make Project**
5. Lancer sur un device/émulateur (API 26+)

## Notes

- Java 21 (Android Studio JBR) est configuré automatiquement via `gradle.properties`
- Pas besoin d'installer Gradle séparément — le wrapper le gère
- L'APK de release est signé manuellement via **Build > Generate Signed APK**
