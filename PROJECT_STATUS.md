# État du projet Chroni — 02/11/2026 (v1.0.0)

## Terminé
- Projet Android complet (Kotlin, Compose), compilation debug et release réussies.
- Module de calcul indépendant : modèle Δt = D/v + D/c, vitesse du son selon température, énergie, conversions, validation des entrées, refus des délais nuls/négatifs/plus courts que le son et des vitesses non physiques, incertitude propagée et nombre de décimales adapté.
- Capture micro continue (AudioRecord, 48 kHz), temps en échantillons, gestion permission, micro occupé/coupé, interruption (arrière-plan), perte d'échantillons, durée maximale.
- Détecteur d'événements : enveloppe, bruit de fond, fronts montants, rejet pics isolés, échos, saturation, fenêtre de vitesses, ambiguïté, qualité (bonne / moyenne / faible / échec).
- Écrans : accueil, enregistrement (état, niveau, événements détectés, arrêt), résultat, analyse (graphique interactif complet), historique, paramètres et informations. Aucun bouton sans action.
- Historique local (JSON + audio PCM privé), positions automatiques et corrigées séparées, gestion de l'espace audio.
- Identité visuelle : icône adaptative + monochrome + PNG par densité, écran de lancement, thème clair/sombre.
- 50 tests automatisés réussis, dont un parcours complet de l'interface (Robolectric, micro simulé).

## Pas encore vérifié (nécessite le téléphone / l'utilisateur)
- Installation et lancement sur Pixel 6a, affichage de l'icône dans le lanceur.
- Capture avec le vrai micro et détection sur de vrais tirs.
- Comparaison avec un chronographe de référence.
- APK release : non signée et non testée sur appareil (minification R8).

## Problèmes restants / risques
- Seuils du détecteur calibrés sur signaux synthétiques uniquement.
- Désinstallation = perte de l'historique (sauvegarde Android volontairement désactivée).

## Livrables
- `livrables/Chroni-1.0.0-debug.apk` — à installer pour les essais (signée clé debug).
- `livrables/Chroni-1.0.0-release-unsigned.apk` — à signer avec ta clé (procédure dans le README).
- Sources complètes dans ce dossier.
