# VibePop — des popups réalistes pour vos écouteurs Bluetooth

[![Version](https://img.shields.io/badge/Version-1.1.2-blue.svg)](https://github.com/daloyanf/vibepop/releases/tag/v1.1.2)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple.svg)](https://kotlinlang.org)
[![TargetSDK](https://img.shields.io/badge/TargetSDK-34-orange.svg)](https://developer.android.com/about/versions/14)

VibePop est une application Android légère, élégante et très personnalisable qui affiche un popup façon AirPods à la connexion de vos écouteurs Bluetooth (thème sombre Material). Elle gère une feuille qui glisse depuis le bas comme sur iOS, le rendu de médias (vidéo MP4/WebM, GIF image par image, images fixes, animations vectorielles Lottie), une animation propre à chaque écouteur, un indicateur de batterie réel en haut à droite avec alerte de niveau faible, le réveil de l'écran et l'affichage par-dessus l'écran verrouillé, ainsi que le glissement élastique pour fermer et un anti-rebond intelligent.

---

## 📥 Téléchargement

- **Lanzou cloud (hébergeur chinois)** : [télécharger le dernier APK](https://wwbag.lanzouu.com/b0umfyvad) (mot de passe : `g3t5`)
- **GitHub Releases** : [page des releases](https://github.com/daloyanf/vibepop/releases)

---

## 🌟 Points clés

### 1. 🎨 Quatre panneaux
- **Console** : aperçu de l'animation en un geste, état du service de surveillance, résumé de la liste blanche et diagnostic Bluetooth en direct.
- **Atelier (thèmes et médias)** :
  - animation vectorielle AirPods classique et thème vidéo *Grande Victoire* intégrés ;
  - une animation différente par écouteur appairé ;
  - import depuis la galerie de vidéos MP4/WebM, GIF (rendu image par image), images WebP/PNG/JPG et fichiers Lottie JSON ;
  - `VideoCropDetector` détecte et découpe les bandes noires à l'import, et les conteneurs non pris en charge (MKV/AVI/MOV) sont refusés avec un message clair.
- **Appareils** : cartes des écouteurs appairés, interrupteurs Material, badges de connexion ; réponse pilotée par liste blanche (rien ne répond par défaut), renommage par appareil et « tout désactiver » en un geste.
- **Réglages** : panneau de permissions, détection des autorisations d'écran verrouillé et de popup en arrière-plan sur Xiaomi/HyperOS (AppOps), sortie haut-parleur forcée, curseur de durée d'affichage.

### 2. 🎧 Personnalisation par écouteur
- Chaque écouteur appairé (identifié par adresse MAC) peut avoir son thème, sa vidéo ou son image, et son nom d'affichage.
- Une configuration globale par défaut s'applique à tous les écouteurs non personnalisés.

### 3. 🔋 Indicateur de batterie réel
- Lit le niveau remonté par le matériel des écouteurs et par les diffusions Bluetooth du système, affiché dans une pastille translucide en haut à droite.
- Couleur selon le niveau (🟢 plus de 40 % / 🟡 21-40 % / 🔴 20 % ou moins), avec une icône éclair quand l'appareil signale la charge.

### 4. ⚡ Réveil et écran verrouillé
- **WakeLock** : l'écran s'allume quand les écouteurs se connectent pendant la veille.
- **PopupActivity** : `setShowWhenLocked` et `setTurnScreenOn` permettent au popup transparent de s'afficher directement sur l'écran verrouillé.

### 5. 🎬 Vidéo plein cadre et détection des bandes noires
- **`FillVideoView`** : lecteur basé sur `TextureView` qui calcule sa matrice à la mesure, la vidéo remplit donc la carte à 100 % (center-crop) sans bandes.
- **`VideoCropDetector`** : échantillonne la première image à l'import, scanne les bords noirs et produit un rectangle de découpe normalisé.

### 6. 🖼️ Import et rendu des médias
- Utilise le sélecteur natif plein écran, en évitant le tiroir PhotoPicker à mi-hauteur.
- Détecte le type MIME et l'extension, conserve le format réel ; le GIF est décodé image par image (`ImageDecoder` dès l'API 28, repli `Movie` sur API 26-27) ; les images sont échantillonnées hors du thread principal et pivotées selon l'EXIF, sans saccade ni dépassement mémoire sur les gros fichiers.

### 7. 👆 Glisser pour fermer
- La carte suit le doigt ; relâchée sous le seuil, elle revient avec un ressort (`OvershootInterpolator`) ; au-delà du seuil de vitesse, elle disparaît.

### 8. 🛡️ Ne pas déranger et robustesse
- **Diffusions découplées** : les remontées de batterie ne rafraîchissent que la pastille, elles ne relancent jamais le popup.
- **Renforcement** : les receveurs sont déclarés `android:exported="false"`, une application tierce ne peut donc pas forger une diffusion de connexion.
- **Limitation** : une fenêtre de 3 secondes absorbe les rafales de reconnexion Bluetooth.

---

## 📊 Formats pris en charge

| Type | Formats | Moteur | Remarques |
| :--- | :--- | :--- | :--- |
| **Vidéo** | `.mp4`, `.webm` | `FillVideoView` (TextureView + MediaPlayer) | Center-crop plein cadre, sortie haut-parleur forcée, fermeture après lecture |
| **Animé** | `.gif` | `ImageDecoder` / `GifMovieDrawable` | Décodage système dès l'API 28, repli canvas sur API 26-27 |
| **Image fixe** | `.png`, `.jpg`, `.jpeg`, `.webp`, `.bmp`, `.heic` | `BitmapDrawable` | Échantillonnage en arrière-plan (1600 px max), rotation EXIF |
| **Vectoriel** | `.json` | `LottieAnimationView` | Mise à l'échelle sans perte, boucle infinie |

---

## 📁 Architecture

```
com.vibepop
├── VibePopApp.kt                      # Initialisation de l'Application et canal de notification
├── data/
│   ├── model/
│   │   ├── DeviceBatteryState.kt      # Écouteur et état de batterie réel
│   │   └── PopupConfig.kt             # Préférences du popup et politique de fermeture
│   └── repository/
│       └── PreferencesRepository.kt   # Persistance SharedPreferences et config par appareil
├── overlay/
│   ├── PopupWindowManager.kt          # Cycle de vie de la fenêtre WindowManager (singleton)
│   ├── FillVideoView.kt               # Lecteur vidéo center-crop plein cadre
│   ├── SlideDismissTouchListener.kt   # Suivi du glissement et retour élastique
│   ├── PopupAutoDismissManager.kt     # Fermeture par compte à rebours
│   ├── PopupInteractionController.kt  # Contrôleur gestes et fermeture auto (overlay et verrouillage)
│   ├── PopupMetrics.kt                # Dimensionnement de la carte (40 % de la hauteur d'écran)
│   ├── GifMovieDrawable.kt            # Rendu GIF (repli Movie sur API 26-27)
│   └── PopupViewBinder.kt             # Liaison de vues, décodage, routage audio, pastille batterie
├── receiver/
│   ├── HeadsetBroadcastReceiver.kt    # Écoute des connexions Bluetooth et de la batterie
│   ├── PopupTriggerLimiter.kt         # Limitation des déclenchements
│   └── BootReceiver.kt                # Démarrage au boot
├── service/
│   └── HeadsetMonitorService.kt       # Service de premier plan (type connectedDevice)
├── ui/
│   ├── MainActivity.kt                # Interface à quatre onglets
│   ├── MainViewModel.kt               # État MVVM et médias par appareil
│   └── PopupActivity.kt               # Activité transparente pour verrouillage et écran éteint
└── util/
    ├── PermissionHelper.kt            # Aides superposition / Bluetooth / notifications / AppOps constructeur
    └── VideoCropDetector.kt           # Détection des bandes noires et calcul du ratio de découpe

app/src/test/java/com/vibepop
├── data/model/PopupConfigTest.kt      # Mode de fermeture et calcul du délai
├── receiver/PopupTriggerLimiterTest.kt# Fenêtre de limitation
└── ui/MediaTypeDetectionTest.kt       # Détection MIME/extension et conteneurs refusés
```

---

## 🚀 Compiler et lancer

### 1. Prérequis
- **Android Studio** Hedgehog (2023.1.1) ou plus récent
- **JDK** 17
- **Gradle** 8.2+
- **Android minimum** : 8.0 (API 26)
- **SDK cible** : Android 14 (API 34)

### 2. Cloner
```bash
git clone https://github.com/daloyanf/vibepop.git
cd vibepop
```

### 3. Signature (facultatif)
Pour générer une release signée, copiez `keystore.properties.example` en `keystore.properties` et renseignez vos informations :
```bash
cp keystore.properties.example keystore.properties
```

### 4. Compiler et tester
```bash
# Tests unitaires
./gradlew test

# APK debug
./gradlew assembleDebug

# APK release
./gradlew assembleRelease
```

---

## 📄 Licence

Publié sous [licence MIT](LICENSE).
