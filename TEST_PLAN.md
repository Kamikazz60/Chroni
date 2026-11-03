# Plan de test — Chroni

Commande : `./gradlew :app:testDebugUnitTest --rerun-tasks`
Dernière exécution : 02/11/2026 — **50 tests, 50 réussis, 0 échec**.

## Tests automatisés effectués
| Suite | Tests | Contenu |
|---|---|---|
| PhysicsTest | 13 | conversions g/kg, s/ms, échantillons/s ; vitesse du son ; aller-retour v → Δt → v ; énergie (0,55 g à 100 m/s = 2,75 J) ; température nominale ; délai nul/négatif/NaN ; délai < D/c ; vitesses non physiques ; entrées invalides ; incertitude ; supersonique ; arrondi d'affichage sans effet sur la valeur |
| InputsTest | 3 | virgule/point, espaces, saisies invalides, toutes les erreurs signalées à la fois |
| EventDetectorTest | 12 | signal propre (erreur ≤ 10 échantillons, v à 1 %) ; 44,1 kHz ; écho de mur avant l'impact ; pic isolé rejeté ; silence → échec explicite ; impact hors fenêtre ; deux impacts → ambigu ; impact faible signalé ; saturation ; bruit du bouton ignoré ; sans fenêtre ; tampon partiel |
| RecordingMonitorTest | 2 | état « prêt », arrêt automatique seulement après la fenêtre complète, désactivable |
| MarkerEditorTest | 9 | déplacement et bornes, réglage fin (1 ms = 48 éch.), retour auto, origine intacte, candidat le plus proche, zoom centré, limites, cadrage, précision sub-échantillon |
| MeasurementRepositoryTest | 7 | mesure auto calculée ; recalcul après correction ; correction incohérente sans résultat ; sauvegarde/réouverture (auto vs corrigé) ; tri et suppression ; suppression de l'audio en gardant la mesure ; fichier corrompu ignoré |
| AppFlowTest (Robolectric) | 4 | saisie invalide → messages ; **parcours complet** : saisie → info audio → enregistrement (micro simulé : tir à 120 m/s, 10 m) → résultat **120,0 m/s** → graphique → glisser la poignée I au doigt (Δt change, rien n'est enregistré) → Auto → +1 ms (Δt +1,00 ms) → confirmer → « Corrigée », auto conservé → historique → réouverture → suppression (JSON + audio) ; mode sombre ; paramètres |

Captures produites par les tests : `docs/screenshots/`.

## Important
La réussite sur signaux synthétiques et micro simulé **ne valide pas la précision sur des tirs réels**.

## Tests restant à réaliser
| Test | Qui | Statut |
|---|---|---|
| Installation, lancement, icône dans le lanceur (Pixel 6a) | Doanh | à faire |
| Permission micro (accepter, refuser, refus définitif) | Doanh | à faire |
| Capture réelle : claquement de mains + casserole | Doanh | à faire |
| Rotation d'écran pendant l'analyse ; arrière-plan pendant l'enregistrement | Doanh | à faire |
| Tirs réels sur cible métallique, plusieurs distances | Doanh | à faire |
| Comparaison avec chronographe de référence (écarts à consigner ci-dessous) | Doanh | à faire |
| APK release signée : installation et fonctionnement | Doanh | à faire |

## Résultats de validation réelle
Aucun pour l'instant. (Consigner ici : date, projectile, distance, température, v Chroni, v référence, écart.)
