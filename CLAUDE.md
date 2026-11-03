# Chroni — instructions du projet
(Règles générales : skill developpement-pro. En cas de conflit, ce fichier et la sécurité priment.)

## 1. Présentation
Objectif : chronographe acoustique Android — estimer vitesse et énergie d'un projectile à partir du délai entre le bruit du départ et celui de l'impact sur une cible sonore.
Plateforme cible : Android (téléphone de référence : Google Pixel 6a, Android 17 / API 37).
Livrable attendu : APK installable, autonome, hors ligne.
Public : usage personnel de Doanh.
Cahier des charges complet : `docs/CAHIER_DES_CHARGES.md`.

## 2. Environnement (vérifié le 02/11/2026)
Machine de compilation : espace cloud de Claude (Linux x86_64, 2 CPU, 7 Go RAM). Pas de compilation sur le PC de l'utilisateur.
Emplacement du projet : `C:\Users\Doanh\Desktop\Programme IA\Chroni` (copie livrée) ; travail dans le cloud dans `/home/claude/Chroni`.
Emplacement des livrables : `livrables/` (APK) à la racine du dossier.
Le cloud est éphémère : à chaque nouvelle session, recopier le dossier depuis le PC et réinstaller le SDK (voir §8).

## 3. Outils installés (cloud, vérifiés)
- OpenJDK 21, Gradle 8.14.3 (wrapper inclus), Kotlin 2.2.21, AGP 8.13.2.
- Android SDK installé à la main dans `/home/claude/android-sdk` : cmdline-tools, platforms;android-36, build-tools;36.0.0, platform-tools.
- Robolectric 4.17 (tests d'interface sur JVM) — son jar Android (≈ 200 Mo) se télécharge dans `~/.m2`.

## 4. Appareils
- Pas d'émulateur utilisable dans le cloud (pas de /dev/kvm).
- Le Pixel 6a n'est pas joignable depuis le cloud (pas d'ADB) : installation et essais réels faits par l'utilisateur.
- Pas de shell sur le PC de l'utilisateur dans la session du 02/11 (seulement lecture/écriture de fichiers).

## 5. Réseau et autorisations
- Autorisés : dl.google.com / maven.google.com, repo.maven.apache.org (souvent HTTP 429 : relancer après 30 s), services.gradle.org.
- Bloqués : plugins.gradle.org (403), repo1.maven.org — d'où `robolectric.dependency.repo.url` réglé sur repo.maven.apache.org.
- L'application n'utilise aucun réseau (pas de permission INTERNET).
- Actions nécessitant l'utilisateur : installer l'APK sur le téléphone, essais de tir réels, comparaison avec un chronographe de référence, signature release avec sa propre clé.

## 6. Architecture
Kotlin + Jetpack Compose (Material 3), une activité, Navigation Compose, un `MainViewModel`.
```
app/src/main/java/com/doanh/chroni/
  core/physics/   Physics.kt (modèle, incertitudes), Inputs.kt (saisie)       — pur, testé
  core/dsp/       EventDetector.kt (détection), RecordingMonitor.kt (arrêt auto) — pur, testé
  core/markers/   MarkerEditor.kt (repères en échantillons), Viewport (zoom)   — pur, testé
  core/model/     Measurement.kt (données sérialisées, recalcul, fabrique)       — pur, testé
  data/           MeasurementRepository.kt (JSON + PCM privés), Settings.kt
  audio/          AudioCapture.kt (AudioRecord, thread dédié)
  ui/             écrans Compose + WaveformChart.kt (graphique interactif)
```
Dépendances : AndroidX (Compose BOM 2025.12.01, activity, lifecycle, navigation, core-splashscreen), kotlinx-serialization, coroutines. Aucune dépendance propriétaire.

## 7. Contraintes
- Temps mesuré UNIQUEMENT en indices d'échantillons ; repères stockés en échantillons, jamais en pixels.
- Jamais de résultat affiché comme valide si délai ≤ 0, ≤ D/c, ou vitesse hors 5–2000 m/s.
- Hors ligne, pas de pub, pas de télémétrie, stockage privé, sauvegarde cloud désactivée.
- Hors périmètre V1 : signature release (clé de l'utilisateur), validation sur tirs réels (à faire par l'utilisateur).

## 8. Commandes
Préparer le SDK (nouvelle session cloud) :
```
mkdir -p ~/android-sdk/cmdline-tools && cd ~/android-sdk && curl -o clt.zip https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip
unzip -q clt.zip -d cmdline-tools && mv cmdline-tools/cmdline-tools cmdline-tools/latest
yes | cmdline-tools/latest/bin/sdkmanager --licenses; cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
echo "sdk.dir=$HOME/android-sdk" > local.properties
```
Tests : `./gradlew :app:testDebugUnitTest` (50 tests ; rapports dans `app/build/test-results`, captures dans `docs/screenshots`).
APK debug : `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
APK release (non signée) : `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release-unsigned.apk`
Analyse : `./gradlew :app:lintDebug`

## 9. Particularités
- Robolectric : `AndroidViewModelFactory` garde l'Application en statique → réinitialisée dans `AppFlowTest` ; le micro est simulé par `ShadowAudioRecord` (la source est redemandée à chaque lecture).
- Si Robolectric échoue en 429 : télécharger `android-all-instrumented-15-robolectric-13954326-i7.jar` et `.pom` avec curl dans `~/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7/`.
