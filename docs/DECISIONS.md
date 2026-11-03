# Décisions techniques — Chroni

## 02/11/2026 — Kotlin + Jetpack Compose, minSdk 26, targetSdk 36
Raison : natif, maintenable, Compose permet un graphique sur mesure (Canvas + gestes). minSdk 26 (Android 8) : icônes adaptatives natives, `AudioRecord` complet ; couvre largement le Pixel 6a (Android 17).
Écarté : Flutter/React Native (latence et accès audio moins directs).

## 02/11/2026 — AudioRecord PCM 16 bits mono, 48 kHz (repli 44,1 kHz)
Raison : capture continue, accès aux échantillons bruts. Source UNPROCESSED si le téléphone la propose, sinon VOICE_RECOGNITION (sans contrôle automatique de gain), sinon MIC. Fréquence réelle relue sur l'objet AudioRecord.
Conséquence : Δt = (indice impact − indice départ) / fréquence ; la latence du micro s'annule. L'horloge système ne sert qu'à détecter une perte d'échantillons.
Écarté : AAudio/Oboe (C++, complexité inutile ici : on ne mesure pas une latence absolue).

## 02/11/2026 — Détecteur maison, sans bibliothèque
Raison : besoin d'un contrôle fin (fronts montants relatifs au bruit et aux 20 ms précédentes, rejet des pics d'un échantillon, contenu haute fréquence, fenêtre de vitesses plausibles). Code pur Kotlin, testable sur JVM.
Règles : départ = premier événement ≥ 50 % du plus fort ; impact = dans la fenêtre [D/vmax + D/c ; D/vmin + D/c], meilleur score, le plus précoce à 3 dB près ; ambiguïté signalée.

## 02/11/2026 — Stockage fichiers JSON + PCM brut, dossier privé
Raison : simple, lisible, sans serveur ni Room/KSP ; écriture atomique. Repères stockés en échantillons, positions auto et finales séparées.
Écarté : Room (dépendance et génération de code non justifiées pour quelques centaines de mesures).

## 02/11/2026 — Audio conservé par défaut, avec information explicite
Raison : nécessaire pour rouvrir/corriger le graphique. Dialogue d'information au premier lancement, réglage pour désactiver, suppression globale, espace affiché.

## 02/11/2026 — Sauvegarde cloud Android désactivée
Raison : données personnelles, pas de transmission hors du téléphone. Conséquence : désinstaller efface l'historique.

## 02/11/2026 — Release non signée
Raison : la clé de signature appartient à l'utilisateur ; aucun secret fabriqué. APK debug fournie pour les essais ; procédure de signature dans le README.

## 02/11/2026 — Tests d'interface avec Robolectric (sans émulateur)
Raison : pas d'émulateur ni d'appareil accessibles. Robolectric exécute les vrais écrans sur JVM, avec micro simulé (`ShadowAudioRecord`) et captures d'écran.
Limite : ne remplace pas un essai sur téléphone réel (son réel, performances, lanceur).
