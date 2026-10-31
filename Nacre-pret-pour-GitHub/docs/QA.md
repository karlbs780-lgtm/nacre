# Plan de validation Android — à exécuter

Ce document est une **checklist**, pas un compte rendu de tests réussis. Les tests instrumentés et benchmarks n'ont pas été lancés dans l'environnement de création.

## 1. Matrice minimale

| Environnement | But |
|---|---|
| API 26 / Android 8 | permission historique, SAF, service et notifications |
| API 32 / Android 12L | stockage cloisonné et reprise |
| API 33 / Android 13 | READ_MEDIA_AUDIO, exemption notification média |
| API 35 / Android 15 | foreground mediaPlayback, edge-to-edge |
| Téléphone milieu de gamme réel | jank, batterie, cartes SD, Bluetooth |
| Petit écran + police 150–200 % | défilement, lisibilité, cibles tactiles |
| Tablette / paysage | largeur de contenu, insets, absence de débordement |

Utiliser des morceaux de test légalement disponibles. Ne pas inclure de musique commerciale dans le dépôt.

## 2. Automatisation

- [ ] JDK 17 + SDK 35 : `sh gradlew testDebugUnitTest lintDebug assembleDebug` sans erreur.
- [ ] Examiner tous les diagnostics lint, notamment les API Media3 instables, permissions et foreground services.
- [ ] API 26 et 35 connectées : `sh gradlew connectedDebugAndroidTest`.
- [ ] Room : cinq tests fournis (unicité par hash, ordre/cascade, changement d'empreinte, merge de métadonnées, favoris/historique).
- [ ] Compose : action d'état vide accessible et cliquable.
- [ ] Vérifier que le build release est signé avec une clé gérée hors du dépôt.

Le test JVM isolé exécuté ici n'exécute pas Room. Le parseur PSI fourni dans tools ne fait pas de résolution de types Android. Le workflow GitHub est fourni, non exécuté.

## 3. Import et bibliothèque

- [ ] Installation neuve sans aucun fichier : tous les états vides conduisent à une action utile.
- [ ] Autoriser puis refuser l'audio sur API 26/32/33/35 ; aucun crash, import SAF toujours utilisable.
- [ ] Refus permanent : bouton des réglages Android et retour à l'application.
- [ ] MP3/FLAC/WAV/AAC/OGG/M4A : durée, titre, artiste, album, genre et couverture si présents.
- [ ] Tags absents, Unicode, accents, titres très longs ; fallback lisible.
- [ ] Deux copies octet-identiques → une entrée ; deux encodages différents → deux entrées assumées.
- [ ] Importer deux fois la même URI : ni duplication ni perte de favori/playlist.
- [ ] Fournisseur SAF sans droit persistant : erreur explicite, pas de réussite fictive.
- [ ] Fournisseur cloud hors ligne : erreur lisible ; import local de secours.
- [ ] Nouveau fichier ajouté pendant la vie du processus : actualisation après debounce.
- [ ] Fichier ajouté pendant l'arrêt du processus : détection à la reprise.
- [ ] Exclure dossier, sous-dossier et fichier précis ; ne pas exclure un voisin de préfixe similaire.
- [ ] Arrêter le processus en plein scan ; relancer sans perdre les entrées déjà sauvegardées.
- [ ] Révoquer l'accès pendant un scan : pas de vidage global de la base.
- [ ] Fichier illisible/corrompu, carte SD retirée, provider indisponible : erreurs et compte de fichiers ignorés.
- [ ] Supprimer un fichier via le gestionnaire du téléphone puis scanner : référence conservée mais marquée indisponible, non proposée dans la bibliothèque.
- [ ] Changer un tag et rescanner : conservation du favori, des compteurs et des paroles.
- [ ] Rotation/changement de thème pendant indexation : travail non relancé par chaque recomposition.

## 4. Lecture et intégration système

- [ ] Lire/pause/reprise ; durée nulle avant préparation ; buffering ; piste illisible.
- [ ] Chercher à 0 %, 50 % et fin ; slider ne saute pas pendant la manipulation.
- [ ] Précédent après 3 s = retour début ; précédent au début = morceau précédent.
- [ ] Suivant, fin de file, répétition off/un/tous, shuffle et file réelle cohérents.
- [ ] Mini-lecteur et plein écran montrent exactement le même état.
- [ ] Passer en arrière-plan, verrouiller l'écran et lire 30 min ; notification et commandes fonctionnelles.
- [ ] Écouteurs filaires et Bluetooth : pause, lecture, suivant/précédent ; déconnexion déclenche la pause.
- [ ] Appel téléphonique et autre lecteur prenant le focus : interruption correcte, pas de double lecture.
- [ ] Volume matériel et slider cohérents ; volume zéro sans confusion avec pause.
- [ ] Minuteur 5 min : pause à échéance même écran verrouillé ; annuler avant échéance.
- [ ] Quitter le lecteur et naviguer : minuteur toujours actif tant que le service vit.
- [ ] Fermer l'UI pendant lecture : service maintenu ; fermer pendant pause : arrêt propre.
- [ ] Retour à l'app : file/position restaurées, pas d'auto-play.
- [ ] Force-stop/reboot : aucune promesse de maintien ; reprise explicite et état cohérent.
- [ ] Forme d'onde : silence PCM → petites barres, son → amplitude ; pause → barres immobiles.
- [ ] Import paroles pendant changement de morceau : paroles attachées au morceau initial sélectionné.

## 5. Playlists/recherche/UI

- [ ] Création nom vide désactivée ; nom Unicode/80 caractères ; doublons de noms tolérés.
- [ ] Ajouter une piste deux fois ne duplique pas l'association.
- [ ] Déplacer première/dernière piste, glisser après appui long puis recharger la playlist.
- [ ] Utiliser Monter/Descendre au lieu du geste ; retirer une piste.
- [ ] Suppression : annuler ne change rien ; confirmer supprime la sélection, jamais les fichiers.
- [ ] Favoris, historique et nouveautés réagissent aux actions réelles.
- [ ] Recherche multi-mots, accents/casse, nom d'artiste/album/genre et aucun résultat.
- [ ] Historique limité aux huit dernières recherches validées/lues.
- [ ] Thèmes/forme du lecteur/réglages persistants après relance.
- [ ] TalkBack : descriptions des boutons, lecture de la piste, alternatives au glissement.
- [ ] Police agrandie, petits écrans/paysage : pas de contrôles hors d'atteinte ; lecteur défilable.
- [ ] Animation désactivée : aucune transition/onde/parallaxe en mouvement non souhaitée.

## 6. Performance — protocole et critères proposés

1. Bibliothèques de 100, 1 000 puis 10 000 pistes (titres/genres/pochettes variés, taille totale documentée).
2. Mesurer séparément premier scan SHA-256, scan inchangé, import de 100 fichiers et défilement.
3. Android Studio Profiler/Perfetto et FrameTimeline : mesurer navigation/transition/glissement durant 60 s en build profilable ou release, pas seulement debug.
4. Mesurer mémoire Java/native et pics de décodage des pochettes ; vérifier la borne du cache et l'absence de fuite Activity/MediaController.
5. Mesurer batterie en lecture écran éteint pendant une heure, puis avec visualiseur ; documenter appareil, version Android, codec, sortie et volume.
6. Comparer animations activées/désactivées et premier affichage/cache chaud.

**Cibles, pas résultats :** navigation visant un budget de 16,7 ms/image sur écran 60 Hz ; aucun blocage long du thread principal ; recherche perçue rapide malgré bibliothèque importante ; pas de croissance mémoire durable à chaque navigation. Fixer des seuils quantitatifs après une baseline matérielle. Ne pas annoncer « 60 FPS garantis » ni une autonomie avant mesures.

Si 10 000 titres provoquent des ralentissements : introduire Paging 3 + FTS et requêtes agrégées, traitements par lots de scan, puis refaire les mesures. Si le scan est interrompu trop souvent par Android : déplacer l'orchestration vers un worker approprié avec progression et contraintes explicites.

## 7. Sécurité et publication

- [ ] Manifest sans INTERNET, RECORD_AUDIO, MANAGE_EXTERNAL_STORAGE ni accès aux contacts.
- [ ] Aucune télémétrie ni clé secrète dans le projet.
- [ ] Session ne donne pas les commandes à n'importe quel contrôleur externe.
- [ ] Droits persistants SAF vérifiés sur plusieurs fournisseurs.
- [ ] Règles de confidentialité et procédure de sauvegarde/export définies avant diffusion ; le prototype désactive la sauvegarde Android.
- [ ] Vérifier exigences SDK/store du moment, licences des dépendances et signature release.
