# Notes techniques — Chroni

## Architecture
- `core/` : logique pure Kotlin sans Android (physique, détection, repères, modèle de mesure) → testée sur JVM.
- `data/` : `MeasurementRepository` (fichiers `files/measurements/<id>.json` et `files/audio/<id>.pcm`, écriture atomique), `Settings` (SharedPreferences).
- `audio/AudioCapture` : thread dédié haute priorité, tampon pré-alloué (`fréquence × durée max`), lecture bloquante, compteur atomique d'échantillons lu par l'interface.
- `ui/` : `MainViewModel` (état, orchestration), écrans Compose, `WaveformChart` (Canvas + gestes).
- Navigation : accueil → enregistrement → résultat ⇄ analyse ; historique ; paramètres.
- Rotation : état dans le ViewModel, repères et vue du graphique dans `rememberSaveable` (indices d'échantillons).
- Arrière-plan pendant une capture : arrêt propre puis analyse de ce qui a été capté (Android couperait le micro).

## Modèle physique
- Δt = D/v + D/c ⇒ v = D/(Δt − D/c) ; c = 331,3 √(1 + T/273,15) ; E = ½ m v².
- Géométrie : téléphone à la bouche. Téléphone à une distance d derrière la bouche sur l'axe : le départ arrive à d/c, l'impact à (D+d)/c + D/v ⇒ d s'annule. Écart latéral L (téléphone à côté de la bouche) : Δt réel = D/v + (√(D²+L²) − L)/c, soit une erreur ≈ −L/c sur Δt quand L ≪ D (1 m → ≈ −2,8 ms ; 10 cm → −0,3 ms). Le téléphone doit donc être derrière, dans l'axe, ou très près de la bouche.
- Incertitude : u(Δt) = √(u_D² + u_I²), u_repère = incertitude d'attaque du détecteur (ou 2 échantillons si placé à la main) + 0,05 ms ; température ±2 °C saisie, ±10 °C nominale ; u(v) = v²/D · √(u(Δt)² + (D·u(c)/c²)²) ; u(E) = m v u(v). La distance est supposée exacte (signalé à l'écran).
- Affichage : 0, 1 ou 2 décimales selon u ; les valeurs internes restent en Double non arrondi.

## Détection (EventDetector)
1. Passe-haut 1er ordre 150 Hz.
2. RMS par trames de 1 ms ; bruit de fond = 30ᵉ percentile.
3. Déclenchement si RMS ≥ bruit + 15 dB **et** ≥ moyenne des 20 ms précédentes (garde 3 ms) + 10 dB → les traînes de réverbération ne déclenchent pas, un nouvel événement dans une traîne oui.
4. Contrôles : durée ≥ 2 trames au-dessus du seuil et ≥ 8 échantillons forts sur 2 ms (rejet des clics isolés), contenu HF sur 3 ms, saturation (≥ 3 échantillons ≥ 32000).
5. Attaque à l'échantillon : premier échantillon ≥ max(4×bruit, 5 % du pic) dans les 2 ms précédant le passage à 20 % du pic ; incertitude = moitié de l'écart entre ces deux instants.
6. Score = SNR + 0,5 × min(montée, 20 dB) + 10 × HF.
7. Départ = premier événement ≥ 50 % du plus fort (téléphone près du tireur). Début (0,35 s, doigt sur l'écran) et fin d'un arrêt manuel (0,15 s) ignorés.
8. Impact = événements dans la fenêtre [D/vmax + D/c ; D/vmin + D/c] (par défaut 20–1500 m/s) ; le plus précoce dont le score est à 3 dB du meilleur ; rival à 3 dB → « ambigu ».
9. Qualité : échec (pas de départ/impact), faible (ambigu ou SNR impact < 20 dB), moyenne (plusieurs bruits forts ou SNR < 28 dB), bonne.
Pendant l'enregistrement, le même détecteur tourne toutes les 200 ms sur le tampon partiel ; arrêt auto quand départ + fin de fenêtre + 0,4 s sont captés.

## Graphique (WaveformChart)
- Pyramide min/max par blocs de 64 échantillons : dessin fluide même sur 30 s (1,44 M échantillons).
- `Viewport` (pur) : premier échantillon visible + échantillons/pixel, zoom centré sur le doigt, limites (max 8 px par échantillon).
- Gestes : poignée/ligne de repère (zone ≈ 28 dp) → déplacement accumulé en échantillons (précision = zoom) ; ailleurs → défilement ; 2 doigts → zoom ; double-toucher → ×2 ; toucher un triangle → placer le repère sélectionné.
- Positions auto d'origine en pointillés quand elles diffèrent.
- Échelle linéaire normalisée sur la zone visible, ou dB (−60 à 0).

## Données d'une mesure
Paramètres, source de température, fréquence et nombre d'échantillons, source audio, audio conservé ?, repères auto et finaux (échantillons), événements candidats (attaque, pic, SNR, score, rôle, incertitude), bruit de fond, qualité, avertissements de détection et d'enregistrement, plage de vitesses, date de création/modification, version, instantané Δt/v/E (recalculé à chaque modification).
