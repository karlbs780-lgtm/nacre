# Nacre · votre musique, libre

Application **Android native** de musique locale, sans compte, sans abonnement et sans permission Internet. Projet Kotlin/Compose, bibliothèque Room, scan MediaStore/import SAF, moteur Media3 dans un service de lecture.

> **État : version initiale de développement, pas une livraison certifiée production.** Les fonctionnalités ci-dessous sont implémentées dans les sources, pas simulées par des données de démonstration. L'environnement de création n'avait pas de SDK Android : aucun APK n'a été compilé, aucune instrumentation n'a été exécutée et aucun débit d'animation n'a été mesuré sur téléphone. Les tests JVM de domaine et la validation de syntaxe Kotlin ont été exécutés séparément. Voir `VALIDATION.txt` dans l'archive et la section Limites avant diffusion.

## 1. Technologies et périmètre

| Élément | Choix |
|---|---|
| Plateforme | Android 8.0+ (API 26), cible/compilation API 35 |
| UI | Kotlin 2.0.21, Jetpack Compose BOM 2024.12.01, Material 3 |
| Audio | AndroidX Media3 1.5.1, ExoPlayer, MediaSessionService, MediaController |
| Données | Room 2.6.1 / SQLite, Flow, coroutines |
| Fichiers | MediaStore pour l'index Android ; Storage Access Framework pour l'import |
| Construction | JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, KSP 2.0.21-1.0.28 |

Versions fixées pour reproductibilité, **pas une affirmation qu'il s'agit des dernières versions**. Le projet cible Android, pas iOS. Vérifier les règles de publication et la cible SDK exigée au moment d'une mise en store.

## 2. Démarrage et compilation

### Prérequis

- JDK **17**, avec `JAVA_HOME` pointant vers le JDK, pas un simple JRE.
- Android Studio prenant en charge AGP 8.7.3, ou SDK Android en ligne de commande.
- Dans le SDK Manager : Android SDK Platform **35**, Android SDK Build-Tools **34.0.0**, Android SDK Platform-Tools ; accepter les licences.
- Définir `ANDROID_HOME` vers le SDK ou créer `local.properties` à la racine avec `sdk.dir=/chemin/vers/Android/Sdk`. Sur Windows, utiliser des `/` dans ce fichier.
- Connexion Internet pour télécharger Gradle et les dépendances lors de la première construction. **L'application installée n'en a pas besoin.**

### Linux / macOS

Depuis le dossier `nacre/` extrait :

```sh
sh gradlew --version
sh gradlew testDebugUnitTest lintDebug assembleDebug
```

### Windows (terminal dans `nacre/`)

```bat
gradlew.bat --version
gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Les lanceurs fournis sont des **scripts d'amorçage légers**, pas le wrapper Gradle binaire standard : ils téléchargent la distribution Gradle 8.9 officielle, vérifient son SHA-256 fixé, puis l'utilisent dans le cache utilisateur. Unix requiert `curl`, `unzip` et `sha256sum` ou `shasum`; Windows utilise PowerShell. En cas de proxy d'entreprise, configurer l'accès aux dépôts Google/Maven et à la distribution Gradle.

APK debug attendu après **réussite** de la construction :

```text
app/build/outputs/apk/debug/app-debug.apk
```

Aucun APK n'est préinclus dans cette archive. Le premier appel peut prendre plusieurs minutes.

### Android Studio

Pour obtenir le wrapper standard reconnu par l'IDE, lancer d'abord :

```sh
sh gradlew wrapper --gradle-version 8.9 --distribution-type bin --gradle-distribution-sha256-sum d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab
```

Sous Windows, remplacer `sh gradlew` par `gradlew.bat`. Cette tâche remplace les lanceurs légers par les fichiers officiels du wrapper. Ensuite : ouvrir `nacre/`, choisir JDK 17 comme Gradle JDK, synchroniser, sélectionner le module `app`, puis Run sur un appareil/émulateur API 26+.

### Installer et lancer par ADB

Activer le débogage USB, connecter et autoriser le téléphone :

```sh
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.nacre/.MainActivity
```

### Tests Android et version release

Avec un appareil/émulateur connecté :

```sh
sh gradlew connectedDebugAndroidTest
sh gradlew assembleRelease
```

Le build release n'a volontairement **aucune clé privée ni configuration de signature**. Utiliser Android Studio → Generate Signed Bundle / APK avec votre propre clé avant diffusion. Le debug n'est pas une version de distribution.

Les rapports Gradle sont placés dans `app/build/reports/`. La CI `.github/workflows/android.yml` prévoit tests JVM, lint et assemblage debug ; elle n'a pas été déclenchée ici. Placer le contenu de `nacre/` à la racine d'un dépôt pour activer ce workflow.

## 3. Première utilisation

1. Ouvrir **Accueil → Enrichir ma bibliothèque**, ou **Bibliothèque → icône Scan**.
2. **Autoriser et scanner** demande l'accès audio adapté à Android. Aucun accès « tous les fichiers » n'est demandé.
3. **Choisir des fichiers** fonctionne sans permission globale : sélectionner des fichiers audio avec un fournisseur accordant un accès persistant. Préférer le stockage local pour le fonctionnement hors ligne.
4. Laisser le premier scan se terminer au premier plan. Le calcul SHA-256 peut être long sur une grosse bibliothèque ; les scans suivants réutilisent les fichiers inchangés.
5. Toucher un morceau pour lire sa collection ; toucher le mini-lecteur pour agrandir le lecteur. Glisser sur la pochette pour changer de titre.
6. Créer une playlist dans **Playlists**, puis utiliser l'icône d'ajout sur les morceaux. Réordonner par appui long/glissement de la poignée ; un simple appui expose aussi Monter/Descendre/Retirer.
7. Ajouter un cœur pour les favoris. Les sélections intelligentes Favoris, Dernières écoutes et Nouveautés se mettent à jour localement.
8. Dans le lecteur : progression, répétition, aléatoire, volume média système, file d'attente et minuteur. **Paroles** permet d'importer du texte ou un LRC pour le morceau courant.
9. Dans **Paramètres**, choisir Iris/Glacier/Aube, pochette/disque, animations et onde audio. Saisir les dossiers ou chemins de fichiers exclus du scan, un par ligne.

Refuser une permission ne bloque pas l'import manuel. Après un refus permanent, utiliser le bouton ouvrant les réglages Android. Les copies strictement identiques ne créent qu'une entrée ; des encodages différents du même titre restent distincts.

## 4. Fonctions implémentées

- Accueil local : reprise des dernières écoutes, albums à redécouvrir, derniers ajouts. Aucun moteur de recommandation distant.
- Bibliothèque, artistes, albums et détails, genres, dossiers, favoris, récents, historique.
- Recherche locale multi-mots, accents/casse ignorés, titre/artiste/album/genre ; historique de huit recherches.
- Scan MediaStore, actualisation manuelle et à la reprise, observation des changements pendant la vie du processus.
- Imports multiples SAF avec permission persistante, métadonnées, déduplication SHA-256, conservation des favoris et compteurs lors d'une réindexation.
- Playlists persistantes, ajout/retrait, ordre sauvegardé en transaction, suppression confirmée sans suppression des fichiers audio.
- Service Media3 : audio focus, pause au débranchement, commandes de session pour notification/écran verrouillé/Bluetooth, restauration de la file **sans lecture automatique**.
- Lecteur et mini-lecteur, transition partagée de pochette, balayage, parallaxe discrète, retour haptique sur lecture et début de déplacement de piste.
- Visualiseur : historique de l'**amplitude RMS réelle du PCM**. Ce n'est ni une animation aléatoire ni un spectre FFT. Pas d'enregistrement du micro.
- Minuteur de 5 à 90 minutes ; extinction par pause dans le service, indépendante de l'écran. Annulation disponible.
- Paroles locales attachées au morceau, importées en texte brut ; fichiers LRC affichés tels quels, **pas de synchronisation temporelle**.
- États vides, activité du scan/chargement audio, erreurs d'accès/import/lecture, message de fichier indisponible dans les playlists.

Les mécanismes natifs audio sont implémentés mais restent à tester sur appareil et selon les politiques batterie du fabricant. La compatibilité MP3, FLAC, WAV, AAC, OGG, M4A dépend du conteneur/codec exposé par Android et Media3. Pas de DRM.

## 5. Structure et design

Voir `docs/ARCHITECTURE.md` pour le flux de données, les choix de performance, la confidentialité et le système visuel. Voir `docs/QA.md` pour les cas limites et critères de validation Android.

```text
app/src/main/java/app/nacre/
  data/       Room, repository MediaStore/SAF, préférences
  domain/     recherche, exclusions, durée, ordre des listes
  audio/      service Media3, connexion UI, mesure PCM
  ui/         design partagé, navigation, bibliothèque, lecteur, paramètres
  LibraryViewModel.kt
  MainActivity.kt
  NacreApp.kt
app/src/test/         14 tests métier JVM
app/src/androidTest/  tests Room et composant Compose
```

## 6. Ce qui a réellement été vérifié

- Exécution des **14 tests JUnit** des fonctions métier avec Kotlin 2.0.21/JRE 17. La classe de données Track a été extraite des sources en retirant uniquement les annotations Room pour cette exécution isolée.
- Analyse syntaxique PSI des fichiers Kotlin/KTS, sans compilation/type-check Android.
- Validation XML, contrôles SQL SQLite et syntaxe shell décrits dans le rapport final `VALIDATION.txt`.

**Non exécutés :** compilation complète Gradle/Android, KSP/Room généré, lint Android, tests instrumentés, lecture réelle, codec/Bluetooth/notification/verrouillage, benchmark mémoire/batterie, performances d'animation. Le rapport ne remplace pas ces étapes.

## 7. Limites et travail avant production

- Projet initial natif, non certifié « complet prêt store » : il faut d'abord compiler puis suivre la matrice QA. Les problèmes de type/API que seule une compilation Android détecte restent possibles.
- Pas d'iOS, pas d'APK joint, pas de synchronisation cloud, pas de lecture DRM, pas de paroles récupérées automatiquement, pas de FFT ni d'égaliseur.
- Style verre obtenu par surfaces translucides, bordures et dégradés. **Pas de flou dynamique de l'arrière-plan** coûteux sur les appareils modestes.
- Les listes sont virtualisées et les regroupements calculés hors du thread UI ; les métadonnées sont néanmoins conservées en mémoire. Pas encore de Paging 3/FTS ni de preuve de charge sur 10 000 titres. Ces optimisations doivent être décidées après mesures.
- Le premier hash lit l'intégralité des nouveaux fichiers : coût de batterie/I/O proportionnel à la taille. Les scans ultérieurs comparent URI/taille/date ; un fichier modifié sans changement de ces indicateurs peut nécessiter une réimportation.
- Le scan est lié au processus, pas à WorkManager ni à un service de scan de longue durée. Si Android tue le processus pendant l'indexation, relancer le scan. Le prochain scan reprend les fichiers déjà enregistrés.
- Le scan ne voit que les fichiers exposés par l'index Android. Les dossiers privés d'autres applications ne sont pas accessibles ; utiliser l'import quand un fournisseur le permet.
- Les imports sont des références persistantes, **pas des copies privées**. Déplacer/supprimer les fichiers, révoquer les accès ou retirer un stockage peut interrompre leur lecture. Réimporter depuis un emplacement accessible.
- Un import provenant d'un fournisseur cloud n'est pas garanti hors ligne. Choisir des fichiers réellement présents sur le téléphone.
- Exclusions : chemins par préfixe, dossiers ou fichiers précis. Une exclusion ne supprime jamais un fichier ; elle rend l'entrée scannée indisponible, conserve ses références de playlist et la masque dans la bibliothèque principale. Les imports explicitement choisis restent disponibles.
- Les fichiers disparus de MediaStore sont marqués indisponibles seulement après un scan intégral réussi. Pas de suppression automatique de pistes ou de playlists.
- Historique : un passage en lecture par transition de piste est compté, pas une écoute qualifiée après 30 secondes. L'historique affiché est un classement par dernière écoute, pas un journal de chaque événement.
- Le minuteur est annulé à la recréation du service. Aucune promesse de réveil planifié après arrêt forcé/reboot.
- La restauration de file supprime les pistes indisponibles ; après une suppression, l'index de reprise peut correspondre à un autre élément. Aucun auto-play après redémarrage.
- L'onde PCM n'est utile qu'en lecture décodée via le sink natif configuré. Sa mise à jour est bornée à 25 Hz ; pas de promesse 60 FPS pour l'application sans benchmark.
- Room est en version 1 ; toute évolution de schéma devra fournir une migration explicite. Pas de migration destructive silencieuse.

Priorité suivante : vraie construction Android/CI, correction des diagnostics éventuels, tests audio sur API 26/32/33/35, puis profilage et durcissement de l'indexation. Ne pas publier sous promesse de compatibilité/performance sans ces résultats.
