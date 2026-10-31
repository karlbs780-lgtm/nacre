# Architecture et système visuel

## Pourquoi Android natif

Le besoin central est l'intégration au stockage et au cycle de vie Android : MediaStore, permissions audio, foreground service, commandes multimédia, audio focus, Bluetooth. Kotlin évite ici une passerelle de plugins entre une interface multiplateforme et ces composants. Compose apporte des animations pilotées par l'état, des listes paresseuses et les transitions partagées sans créer un second moteur graphique applicatif. Cela ne fournit pas d'application iOS ; ce serait un projet distinct.

## Couches et responsabilité

```text
Compose → LibraryViewModel → LibraryRepository → ContentResolver / MediaStore / SAF
                          → LibraryDao → Room / SQLite
Compose → PlaybackConnection → MediaController → MediaSessionService → ExoPlayer
PCM → AudioMeter StateFlow → Canvas du lecteur
Preferences → Settings StateFlow → thème / apparence
```

- **UI** : navigation locale, autorisations système via Activity Result API, affichage et gestes. Aucun objet ExoPlayer dans un écran.
- **Présentation** : ViewModel conservé lors des rotations. Il transforme les Flow Room en index, regroupe hors thread principal, limite les rafales d'actualisations et gère les commandes utilisateur.
- **Domaine** : fonctions déterministes de normalisation/recherche, exclusions, durée et déplacement de liste, testées sans Android.
- **Données** : Repository pour lire les sources externes sur Dispatchers.IO. Room pour les données structurées. SharedPreferences pour les petits réglages et le point de reprise audio ; une évolution DataStore est possible.
- **Audio** : un ExoPlayer unique dans MediaSessionService. La connexion UI est libérée à la destruction du ViewModel, mais la lecture reste détenue par le service.
- **Composition** : NacreApp crée les dépendances applicatives par injection manuelle. Un module Gradle unique et des packages séparés gardent cette première version lisible. Il ne s'agit pas d'une architecture multi-module Clean Architecture exhaustive.

## Modèle local

- `tracks` : clé primaire SHA-256 des octets audio. URI de lecture, tags, durée, source, chemin, taille/date, favoris, compteurs, dernière lecture, paroles et disponibilité.
- `playlists` : identifiant stable et nom.
- `playlist_tracks` : clé composite playlist/piste, position et clés étrangères. Retirer une playlist cascade uniquement sur ses associations.
- `searches` : requête unique, date, huit entrées visibles et nettoyage à l'ajout.

Les noms d'albums sont regroupés avec l'artiste afin de ne pas fusionner deux albums homonymes d'artistes différents. Une compilation multi-artistes peut par conséquent apparaître dans plusieurs groupes : un futur champ albumArtist améliorerait ce cas.

`merge()` est transactionnel : les nouveaux tags n'écrasent ni les favoris, ni l'historique, ni les paroles. Si une URI connue change d'empreinte, `replaceChanged()` transfère également les associations de playlist et l'état personnel vers la nouvelle identité ; il ne supprime que l'ancienne référence en base, pas les octets du fichier. L'écriture de paroles utilise un UPDATE ciblé. Les réordonnancements sont atomiques. L'ordre n'est pas unique au niveau SQL pendant une mise à jour, et la transaction empêche l'observation d'un état partiel.

## Scan, déduplication et sécurité

1. Vérifier READ_MEDIA_AUDIO (API 33+) ou READ_EXTERNAL_STORAGE (API 26–32).
2. Interroger l'index audio externe de MediaStore. Aucune exploration des dossiers privés, aucun MANAGE_EXTERNAL_STORAGE.
3. Appliquer les exclusions de chemin ; comparer URI/taille/date aux entrées connues.
4. Pour un fichier nouveau/modifié : SHA-256 en lecture séquentielle puis MediaMetadataRetriever pour les tags. Les fichiers identiques convergent vers la même clé.
5. Fusionner les métadonnées ; préserver l'état utilisateur en transaction.
6. Après un parcours intégral réussi seulement, marquer les anciennes entrées scannées non retrouvées comme indisponibles.

Les octets sont hashés une seule fois tant que les indicateurs restent inchangés. Deux encodages ou deux tags embarqués différents donnent des hashes différents : ce n'est pas un algorithme de détection perceptuelle de chanson.

Le SAF obtient des URI avec un droit persistant. Si le fournisseur ne l'accorde pas, l'import signale l'erreur plutôt que de présenter une disponibilité durable fictive. Aucun fichier utilisateur n'est supprimé ou réécrit. Les droits persistent tant que le fournisseur et le fichier restent accessibles. Il n'y a pas de prise en charge explicite de dossiers SAF récursifs ; l'import est multi-fichiers.

Un Mutex sérialise les scans/imports. Un ContentObserver regroupe les changements sur 1,2 seconde. Les erreurs unitaires sont comptées et ne font pas disparaître une entrée précédemment connue. Un échec global ne vide jamais la base. Les scans sont actualisés à la reprise ; aucune garantie de surveillance hors processus.

## Lecture native

- ExoPlayer possède un AudioAttributes média/musique et gère l'audio focus.
- `setHandleAudioBecomingNoisy(true)` demande la pause à la déconnexion de la sortie audio.
- MediaSessionService gère l'intégration système ; la notification n'est pas dessinée par l'UI Compose.
- La session accepte l'application et les contrôleurs considérés de confiance par Media3, pas tout contrôleur arbitraire.
- File/position/répétition/aléatoire sont sauvegardés périodiquement et lors des événements, puis restaurés sans auto-play.
- Le minuteur est une commande de session exécutée par une coroutine du service ; fermer le lecteur ne l'annule pas. Un arrêt du service, en revanche, l'annule.
- Le PCM décodé passe dans un TeeAudioProcessor. Son sink calcule la valeur RMS 16 bits, bornée en fréquence de mise à jour, et expose 24 amplitudes chronologiques.

Le Canvas ne traite pas de buffers audio. Le thread audio ne lit ni base, ni image, ni réseau. Le visualiseur est une enveloppe d'amplitude, pas une analyse fréquentielle. Aucune permission RECORD_AUDIO.

## Performance : décisions et limites

- LazyColumn/LazyRow, clés stables et types de contenu pour les listes de pistes ; pas de création d'une vue par morceau hors écran.
- Regroupements, tri et recherche hors thread principal ; frappe débouncée à 120 ms.
- Images embarquées décodées sur IO, au plus trois décodages simultanés, échantillonnage vers ~640 px et cache LRU de 12 MiB.
- Pas de téléchargement d'images ni de police à l'exécution. Les pochettes absentes reçoivent un visuel procédural déterministe, pas une image d'album inventée.
- Animations de transformation/opacité et transition partagée ; aucune rotation permanente, aucun flou plein écran animé.
- Canvas RMS borné à 25 actualisations/s. Il ne constitue pas une mesure du taux de rafraîchissement de navigation.
- Les listes complètes de métadonnées résident encore en mémoire ; le scan écrit une piste par transaction et Room réémet l'index. La base est adaptée au démarrage d'un lecteur local, mais le passage à 10 000+ titres doit être mesuré. Paging 3, index FTS, requêtes de groupes agrégées et imports batch sont les optimisations envisagées.

Aucun budget de batterie/FPS n'est revendiqué sans appareil. Voir QA pour la méthode de mesure.

## Identité « Nacre »

### Tokens

| Token | Valeur |
|---|---|
| Fond | #090B13 |
| Surface | #141825 |
| Surface élevée | #1D2235 |
| Texte | #F4F2FA |
| Texte secondaire | #989DB3 |
| Iris | #C1B3FF |
| Glacier | #8CDED0 |
| Aube | #FFBEA3 |
| Rayons | cartes 26 dp, pochettes 22 dp, disque circulaire |
| Espacements | base 4 dp, rythmes 8/12/16/20/24/32 |
| Typographie | sans système, titre 28–32 sp, hero 29/34 sp, labels 10–12 sp |

Les cartes utilisent un dégradé translucide et une bordure éclairée, sans véritable échantillonnage/flou de ce qui est derrière. C'est un arbitrage assumé pour la lisibilité et le coût GPU.

### Composants

- `Glass` : surface élevée, coins doux, bordure lumineuse et interaction optionnelle.
- `PageTitle` : surtitre espacé, grand titre, action contextuelle.
- `Cover` : image locale, fallback procédural, décodage/cache partagé.
- `TrackRow` : zone de lecture, métadonnées ellipsées, favori et action ; piste indisponible désactivée.
- `ActionIcon` : bouton natif avec description accessible.
- `EmptyState` : état sans contenu avec explication et action réelle.
- `MiniPlayer` : état audio commun au lecteur plein écran, progression et contrôle direct.
- `PcmWaveform` : Canvas à 24 barres de l'amplitude réellement mesurée.

### Mouvement et adaptation

- Navigation : fondu de 220 ms ; plein écran : fondu 300 ms et élément de pochette partagé.
- Pochette : parallaxe/inclinaison liées au glissement, retour interpolé en 120 ms ; pas d'animation autonome permanente.
- Gestes : balayage suivant/précédent, appui long sur poignée pour playlist, alternatives Monter/Descendre accessibles par clic.
- Haptique : lecture/pause plein écran et début de réordonnancement.
- Option animations désactivées : transitions de durée nulle, pas de parallaxe ni d'onde en mouvement ; autres contrôles restent utilisables.
- Insets système respectés, contenu centré sur grand écran et largeur limitée pour le lecteur. Les petits écrans peuvent faire défiler le lecteur verticalement. Pas de promesse d'expérience tablette à double panneau dans cette version.

## Évolution

Introduire des interfaces de source de bibliothèque pour d'éventuelles fonctions réseau, sans donner au lecteur local une dépendance à un compte. Isoler migrations DB, workers d'indexation, navigation typée et audio resumption système pour une prochaine itération. L'état actuel restaure la file à l'ouverture de l'application mais n'implémente pas le protocole avancé de resumption après redémarrage du téléphone.
