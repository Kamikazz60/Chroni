# PROJET CHRONI — CHRONOGRAPHE ACOUSTIQUE ANDROID

## 1. Mission

Tu es un développeur Android senior spécialisé en traitement du signal audio, instrumentation physique, interfaces mobiles et tests logiciels.

Ta mission est de développer **Chroni**, une application Android permettant d'estimer la vitesse et l'énergie cinétique d'un projectile à partir du délai entre le bruit du départ et le bruit de l'impact sur une cible.

Le projet doit aller jusqu'à la livraison d'une **APK installable, autonome et entièrement utilisable**, et pas seulement jusqu'à la création d'un prototype visuel ou d'un projet compilable.

L'application est destinée à un usage personnel.

## 2. Règles de collaboration

- Réponds en français par défaut.
- Explique clairement les décisions techniques importantes.
- Travaille de manière autonome jusqu'à obtenir une première version fonctionnelle.
- Ne t'arrête pas après avoir créé les écrans ou le squelette du projet.
- Ne prétends jamais qu'une fonctionnalité fonctionne sans l'avoir vérifiée.
- En cas de problème, identifie sa cause, tente une correction et vérifie le résultat.
- Ne demande mon intervention que lorsqu'une information ou une action réellement inaccessible est indispensable.
- Privilégie une architecture simple, robuste, maintenable et adaptée à un smartphone Android.
- N'ajoute aucune publicité, aucun abonnement, aucune télémétrie et aucun service distant obligatoire.
- L'application doit fonctionner hors ligne, une fois installée.
- Ne remplace pas une fonctionnalité demandée par une simple maquette.

## 3. Objectif physique

L'utilisateur renseigne :
- la masse du projectile en grammes ;
- la distance entre la bouche du canon et la cible, en mètres.

L'application enregistre le son avec le microphone du téléphone.

Elle tente de détecter :
1. le bruit du départ du tir ;
2. le bruit de l'impact sur une cible sonore.

Elle mesure le délai entre les deux événements, corrige le temps de propagation du son et estime la vitesse du projectile.

Modèle physique de départ :

Δt = D/v + D/c

Donc :

v = D / (Δt − D/c)

Avec :
- D : distance de tir en mètres ;
- Δt : délai mesuré en secondes ;
- c : vitesse du son dans l'air, en m/s ;
- v : vitesse estimée en m/s.

La vitesse du son pourra être estimée à partir de la température lorsque celle-ci est disponible, ou utiliser une valeur nominale clairement indiquée.

L'énergie cinétique est calculée par :

E = ½ × m × v²

La masse doit être convertie de grammes en kilogrammes avant le calcul.

**Important :** le modèle suppose que le téléphone est suffisamment proche du départ du tir et que le trajet acoustique de l'impact est approximativement égal à la distance renseignée. La documentation et l'interface doivent expliciter cette hypothèse.

Le résultat est une estimation expérimentale, non une mesure certifiée.

## 4. Enregistrement audio et détection

Développe un véritable moteur de mesure audio.

Exigences :
- Utiliser le microphone du smartphone avec une API audio Android adaptée à une capture continue.
- Privilégier des horodatages basés sur les échantillons audio et leur fréquence d'échantillonnage réelle.
- Ne pas utiliser l'heure de réception des callbacks de l'interface pour mesurer le délai.
- Examiner les caractéristiques du signal pour détecter les événements : amplitude, enveloppe, variations temporelles et autres indicateurs utiles.
- Permettre de distinguer autant que possible les événements réels, les échos, les bruits parasites et les détections ambiguës.
- Éviter qu'un pic de volume isolé soit automatiquement considéré comme un événement valide sans vérification contextuelle.
- Prévoir une gestion explicite des échecs de détection.
- Ne jamais afficher un résultat numérique présenté comme valide lorsque le délai est nul, négatif ou physiquement incohérent.

L'utilisateur doit pouvoir démarrer et arrêter l'enregistrement, consulter le résultat et recommencer une mesure.

Gérer proprement les autorisations du microphone, les interruptions audio et les erreurs matérielles.

## 5. Graphique acoustique interactif — fonctionnalité obligatoire en V1

Afficher le signal audio enregistré sous forme de graphique temporel lisible.

Le graphique doit permettre :
- de visualiser les variations d'amplitude du signal ;
- d'identifier les deux événements détectés automatiquement ;
- d'afficher un repère distinct pour le départ et un autre pour l'impact ;
- de zoomer et de se déplacer dans le signal ;
- de sélectionner un repère et de le déplacer horizontalement avec le doigt ;
- de déplacer les repères avec une précision adaptée au signal enregistré ;
- de voir le temps correspondant à la position du repère ;
- de revenir aux positions détectées automatiquement ;
- de recalculer immédiatement le délai, la vitesse et l'énergie après chaque modification ;
- de confirmer et d'enregistrer le résultat corrigé.

La manipulation des repères doit rester confortable sur un petit écran.

Les positions des repères doivent être conservées sous forme d'indices d'échantillons ou d'horodatages audio fiables. Elles ne doivent jamais être enregistrées uniquement sous forme de coordonnées en pixels.

Prévoir un mécanisme pour sélectionner précisément un événement lorsque plusieurs pics proches apparaissent.

La position automatique d'origine doit être conservée séparément de la position corrigée manuellement. L'historique doit permettre de distinguer les mesures automatiques des mesures corrigées.

## 6. Interface utilisateur

Créer une interface moderne, sobre et lisible, adaptée à Android.

Écrans minimums :

### A. Écran principal
- Nom Chroni et icône de l'application.
- Champ de masse du projectile en grammes.
- Champ de distance en mètres.
- Paramètre de température facultatif, avec indication de sa source.
- Bouton de lancement de mesure.
- Accès à l'historique.

### B. Écran d'enregistrement
- État de l'enregistrement clairement visible.
- Indication de la détection des événements.
- Commande d'arrêt.
- Gestion des erreurs.

### C. Écran de résultat
- Vitesse estimée en m/s.
- Énergie estimée en joules.
- Délai brut mesuré en millisecondes.
- Temps estimé du projectile et temps de propagation du son.
- Masse et distance utilisées.
- Indication du mode de détection et de la qualité de la mesure.
- Accès au graphique acoustique.

### D. Écran d'analyse
- Graphique acoustique interactif.
- Repères départ et impact déplaçables.
- Zoom et déplacement.
- Recalcul immédiat.
- Réinitialisation des repères automatiques.
- Confirmation de la mesure.

### E. Historique
- Liste des mesures précédentes.
- Date et heure.
- Paramètres du tir.
- Vitesse et énergie.
- Possibilité de rouvrir une mesure et son graphique.
- Possibilité de supprimer une mesure.
- Conservation des positions automatiques et corrigées.

### F. Paramètres et informations
- Version de l'application.
- Informations sur le modèle physique.
- Explication des limites de précision.
- Gestion des paramètres de mesure et de la conservation des données.

Tous les écrans doivent être fonctionnels. Aucun bouton décoratif ou sans action ne doit subsister.

## 7. Calculs et qualité des résultats

Centraliser les calculs dans un module indépendant de l'interface.

Vérifier notamment :
- la conversion grammes/kilogrammes ;
- la conversion secondes/millisecondes ;
- la cohérence entre distance, délai et vitesse ;
- la gestion de la vitesse du son ;
- la détection des entrées invalides ;
- le cas d'un projectile dont le calcul aboutit à une vitesse non physique ;
- les cas où la détection automatique échoue.

Utiliser une précision numérique suffisante en interne. L'arrondi d'affichage ne doit pas modifier les valeurs utilisées dans les calculs.

Ne pas afficher artificiellement plusieurs décimales si la qualité de la mesure ne les justifie pas.

Si la qualité du signal est insuffisante, afficher un avertissement plutôt que masquer l'incertitude.

## 8. Historique et données

Conserver localement les données nécessaires à la consultation ultérieure des mesures.

Utiliser un stockage local adapté, sans serveur.

Chaque mesure doit pouvoir conserver :
- les paramètres de configuration ;
- la fréquence d'échantillonnage et les métadonnées audio pertinentes ;
- les positions automatiques des événements ;
- les positions finales éventuellement corrigées ;
- le délai mesuré ;
- la vitesse et l'énergie calculées ;
- les avertissements de qualité ;
- la date de la mesure.

Conserver les enregistrements audio si cela est nécessaire pour permettre la réouverture et la correction du graphique. Prévoir une gestion claire de l'espace de stockage et la possibilité de supprimer les enregistrements.

Ne pas conserver les enregistrements audio sans en informer l'utilisateur.

## 9. Identité graphique et icône propre

Créer une identité visuelle originale pour Chroni.

L'application doit posséder :
- un nom affiché : Chroni ;
- une icône de lancement originale, reconnaissable et cohérente avec un instrument de mesure acoustique ;
- les ressources d'icône Android nécessaires aux différentes densités et tailles ;
- une icône adaptative lorsque la version Android le permet ;
- un écran de lancement cohérent avec l'identité visuelle ;
- une interface soignée, sans assets provisoires.

L'icône doit être réellement intégrée au paquet Android final. Elle ne doit pas être remplacée par l'icône générique du framework.

Vérifier son affichage dans le lanceur Android après installation.

## 10. Architecture et compatibilité

Choisir une architecture Android native adaptée à la capture audio et au traitement du signal.

Kotlin est privilégié, avec Jetpack Compose si cela permet de conserver une interface maintenable.

Définir un niveau minimal d'Android cohérent avec les API retenues et expliquer ce choix.

Prévoir :
- les autorisations strictement nécessaires ;
- une gestion propre du cycle de vie Android ;
- une absence de dépendance à Internet au fonctionnement normal ;
- un stockage privé ;
- une gestion robuste des changements d'orientation et des interruptions ;
- une compatibilité vérifiée avec le Pixel 6a lorsque le matériel de test est disponible.

Ne pas imposer de dépendance inutile à un service propriétaire ou payant.

## 11. Tests obligatoires

Créer et exécuter des tests automatisés pour :
- les calculs physiques ;
- les conversions d'unités ;
- les cas limites ;
- la validation des paramètres ;
- la manipulation des repères ;
- le recalcul après correction ;
- la sauvegarde et la réouverture d'une mesure.

Prévoir également des tests audio avec des signaux synthétiques représentatifs et, lorsque possible, des enregistrements réels.

Vérifier que les événements correctement identifiés dans un signal synthétique produisent les résultats attendus.

Ne pas confondre la réussite de tests synthétiques avec la validation de la précision sur des tirs réels.

Comparer ensuite les résultats à ceux d'un chronographe de référence lorsque ce matériel est disponible. Documenter les erreurs constatées plutôt que d'inventer des résultats de validation.

## 12. Construction et livraison de l'APK

La mission ne sera pas considérée comme terminée tant que les étapes suivantes n'auront pas été réalisées :

1. Projet complet créé.
2. Compilation Android réussie.
3. Tests automatisés exécutés.
4. Correction des erreurs bloquantes.
5. APK finale générée.
6. Vérification de l'existence et de l'intégrité du fichier APK.
7. Installation et lancement testés sur un appareil ou un émulateur compatible, lorsque disponible.
8. Vérification des fonctionnalités essentielles.
9. Vérification de l'icône intégrée.
10. Livraison du fichier APK et des sources.

Produire une APK de débogage installable pour les essais, puis une APK de version release lorsque la configuration le permet.

Si la signature release nécessite une clé ou un secret que tu ne possèdes pas, ne fabrique pas de secret et ne prétends pas avoir signé l'application. Explique la limite et fournis une procédure simple pour finaliser la signature.

Fournir un chemin de livraison clair, par exemple :

`app/build/outputs/apk/debug/app-debug.apk`

Le chemin final doit correspondre au fichier réellement produit dans le projet.

Ne jamais annoncer une compilation, un test ou une installation réussis sans résultat vérifiable.

## 13. Organisation du projet

Créer une documentation locale maintenable comprenant au minimum :
- `README.md` : présentation, installation, utilisation et compilation ;
- `PROJECT_STATUS.md` : état actuel, éléments terminés et problèmes restants ;
- `TECHNICAL_NOTES.md` : architecture, modèle physique et décisions importantes ;
- `TEST_PLAN.md` : tests effectués, résultats et tests restant à réaliser.

Mettre à jour ces documents au fur et à mesure du développement.

Si le travail doit être interrompu, laisser un état suffisamment précis pour permettre sa reprise dans une nouvelle session.

## 14. Ordre de travail

Procéder dans cet ordre :

1. Examiner l'environnement de développement disponible.
2. Définir une architecture technique adaptée.
3. Vérifier la faisabilité de la capture audio et du graphique interactif.
4. Implémenter les calculs physiques et leurs tests.
5. Implémenter l'enregistrement et la détection audio.
6. Implémenter le graphique interactif et les corrections manuelles.
7. Implémenter les résultats et l'historique.
8. Créer l'identité visuelle et intégrer l'icône.
9. Exécuter les tests et corriger les problèmes.
10. Construire l'APK et vérifier le livrable.
11. Fournir un bilan honnête et les instructions d'installation.

Ne pas s'arrêter à un plan ou à une preuve de concept. Le plan est un moyen d'organiser le développement, pas le livrable final.

## 15. Critères d'acceptation

La première version est acceptable lorsque :

- l'application s'installe et se lance ;
- l'utilisateur peut saisir la masse et la distance ;
- le microphone enregistre réellement le signal ;
- le système tente de détecter les deux événements ;
- le graphique affiche le signal enregistré ;
- les deux repères sont visibles et déplaçables ;
- la correction recalcule effectivement les résultats ;
- les calculs et les conversions sont testés ;
- l'historique conserve les mesures ;
- les données peuvent être rouvertes et corrigées ;
- l'application fonctionne hors ligne ;
- l'icône personnalisée apparaît dans le lanceur ;
- l'APK finale est présente et son emplacement exact est communiqué ;
- les limitations de précision sont clairement documentées.

Commence par examiner l'environnement, puis développe et teste l'application jusqu'à la livraison. En cas de limitation réelle, indique-la explicitement et livre tout ce qui est effectivement fonctionnel, sans présenter une fonctionnalité inachevée comme terminée.