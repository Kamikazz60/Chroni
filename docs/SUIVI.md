# Suivi du projet Chroni

## Objectif
Application Android de chronographe acoustique (vitesse + énergie d'un projectile par le délai départ → impact), livrée en APK autonome et hors ligne.

## État actuel (02/11/2026) — V1.0.0
Niveaux : 1 implémenté · 2 compilé · 3 testé automatiquement · 4 émulateur/simulation · 5 appareil réel.

| Fonctionnalité | Niveau |
|---|---|
| Calculs physiques, conversions, incertitudes, cas limites | 3 |
| Saisie masse / distance / température (+ capteur si présent) | 3 (+ simulation Robolectric) |
| Capture micro AudioRecord 48 kHz, horodatage par échantillons | 2 + 4 (micro simulé Robolectric) — **à valider sur Pixel 6a** |
| Détection départ/impact, échos, pics isolés, saturation, qualité | 3 (signaux synthétiques) — **à valider sur tirs réels** |
| Arrêt automatique après la fenêtre de recherche | 3 + 4 |
| Écran résultat (v, E, Δt, temps de vol, retour du son, qualité, avertissements) | 4 |
| Graphique interactif : zoom, défilement, repères déplaçables au doigt, réglage fin, pics candidats, retour auto, recalcul immédiat, confirmation | 3 + 4 |
| Historique : liste, réouverture, correction, suppression, auto/corrigé distingués | 3 + 4 |
| Paramètres : audio conservé ou non, espace, suppression, durée max, arrêt auto, plage de vitesses, infos modèle/limites | 4 |
| Icône adaptative + monochrome + PNG par densité, écran de lancement | 2 (présence vérifiée dans l'APK) — **affichage lanceur à vérifier sur le téléphone** |
| APK debug signée (clé debug) | générée et vérifiée (apksigner, zip) |
| APK release minifiée | générée, **non signée**, non testée sur appareil |

Problèmes connus / limites :
- Aucune validation sur appareil réel ni sur tir réel à ce jour (pas d'accès au téléphone depuis le cloud).
- Détecteur réglé sur signaux synthétiques : les seuils (SNR 15 dB, montée 10 dB) devront peut-être être ajustés avec de vrais enregistrements.
- Sauvegarde Android désactivée : désinstaller l'appli efface l'historique.

## Feuille de route
- P0 terminé : projet, calculs, capture, détection, graphique, historique, icône, tests, APK debug.
- P0 à faire (utilisateur) : installer l'APK sur le Pixel 6a, check-list du README « Premiers essais ».
- P1 à venir : ajuster le détecteur avec de vrais enregistrements (export des mesures à prévoir) ; comparer à un chronographe de référence et documenter les écarts dans TEST_PLAN.md ; signature release.
- P2 idées : export CSV / WAV des mesures, séries de tirs avec moyenne/écart-type, paramètre « position du téléphone » (écart latéral).

## Dernière session — 02/11/2026
Travail fait : création complète du projet (architecture, 7 modules, 6 écrans), icône, tests, APK, docs.
Fichiers : tout le projet (création).
Tests : 50 tests automatiques, 50 réussis (`./gradlew :app:testDebugUnitTest --rerun-tasks`), dont parcours complet de l'interface sous Robolectric avec micro simulé (tir à 120 m/s mesuré 120,0 m/s). Lint : 0 erreur, 16 avertissements mineurs (versions de dépendances, style).
Blocages : pas d'émulateur ni de téléphone accessibles ; Maven Central renvoie parfois 429 (relancer).

## Prochaine tâche exacte
Récupérer le retour de Doanh après installation de `livrables/Chroni-1.0.0-debug.apk` sur le Pixel 6a (check-list README « Premiers essais ») : icône dans le lanceur, permission micro, une mesure avec claquement de mains + cible, réouverture du graphique. Puis corriger les éventuels problèmes et, avec de vrais enregistrements, ajuster les seuils du détecteur.
