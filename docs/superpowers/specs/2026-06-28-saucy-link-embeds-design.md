# Design embeds de liens facon Saucy

## Objectif

Ajouter a Kiss-Shot un pipeline d'embeds de liens inspire de SaucyBot pour trois familles de sites :

- Twitter/X ;
- Pixiv ;
- Misskey.

Le bot doit traiter automatiquement les liens postes dans tous les salons du serveur, produire une reponse plus fiable que l'embed Discord natif, puis masquer l'embed natif uniquement quand le remplacement a reussi.

## Decisions validees

Le comportement cible est le suivant :

- activation automatique dans tous les salons de guilde ;
- reponse au message original avec le resultat genere ;
- message temporaire de traitement comme Saucy ;
- suppression de l'embed natif seulement apres un envoi reussi et seulement si le bot a la permission necessaire ;
- respect des liens volontairement masques par l'utilisateur : liens entre chevrons `<...>` et liens en spoiler `||...||` ignores ;
- limites Saucy par defaut, mais configurables par `.env` ;
- cache memoire simple avec TTL configurable ;
- contenu sensible ignore silencieusement en salon non-NSFW, avec log applicatif ;
- Pixiv complet, y compris ugoira via FFmpeg et adaptation Docker si necessaire ;
- Twitter/X aussi proche que Saucy, via `api.fxtwitter.com` et fallback lien FxTwitter ;
- Misskey sur une liste de domaines configurable, pre-remplie avec `misskey.io`, `misskey.design`, `oekakiskey.com`.

## Architecture

Ajouter un listener global auto-decouvert :

- `SaucyLinkEmbedListener` dans `listeners/global/`.

Le listener reste mince. Il filtre les evenements JDA puis delegue au pipeline :

- ignore les DM et les messages de bots ;
- lit le contenu brut du message ;
- ignore le message si le contenu contient un lien masque selon la regle Saucy ;
- demande au `SaucySiteManager` les liens pris en charge ;
- poste un message temporaire de traitement ;
- envoie les reponses produites ;
- masque les embeds natifs du message source uniquement apres succes.

Ajouter une couche de services dediee :

- `SaucySiteManager` : orchestre les modules de site, applique la limite de liens par message, et retourne les traitements a executer ;
- `SaucyMessageSender` : transforme une reponse normalisee en messages JDA, gere les fichiers, les embeds, le decoupage, le message temporaire et la suppression de l'embed natif ;
- `SaucyLinkCache` : cache memoire avec TTL, utilise par les clients HTTP ;
- `SaucyLinkEmbedConfig` : lecture centralisee des variables `.env` et valeurs par defaut.

Ajouter un contrat commun pour les sites :

- `SaucySite` : expose un identifiant, un matcher d'URL, et une methode de traitement ;
- `SaucyProcessResponse` : contient texte optionnel, embeds JDA, fichiers a uploader, indicateur sensible, et etat de succes ;
- `SaucyFileAttachment` : represente un flux/fichier temporaire, son nom, sa taille, et son type.

## Modules de site

### Twitter/X

`FxTwitterSite` matche les URLs de statut :

- `twitter.com/.../status/...` ;
- `x.com/.../status/...` ;
- `mobile.twitter.com/.../status/...` ;
- `nitter.*` compatible avec le pattern Saucy quand possible.

Le module appelle `FxTwitterClient`, qui consomme `https://api.fxtwitter.com/{user}/status/{id}`.

La reponse doit produire :

- un embed avec auteur, avatar, texte, date, footer Twitter, stats replies/retweets/likes/views ;
- une image par photo, en resolution originale quand l'URL le permet ;
- une video ou gif uploadee quand le fichier est sous la limite Discord configuree ;
- un lien `https://fxtwitter.com/{user}/status/{id}` quand la video depasse la limite ou ne peut pas etre recuperee.

Le champ `possibly_sensitive` de FxTwitter est considere comme contenu sensible pour le garde-fou NSFW.

### Pixiv

`PixivSite` matche les URLs `pixiv.net/.../artworks/{id}`.

Le module utilise `PixivClient` avec un cookie de session `SAUCY_PIXIV_SESSION_COOKIE`. Si le cookie est absent ou invalide, le module loggue l'erreur et ne poste rien.

Pour une illustration statique :

- recuperer les details via l'API web Pixiv ;
- recuperer les pages si l'oeuvre contient plusieurs images ;
- choisir la meilleure qualite sous la limite de fichier configuree ;
- poster jusqu'a `SAUCY_PIXIV_IMAGE_LIMIT`, valeur par defaut `5` ;
- indiquer sobrement quand une serie contient plus d'images que la limite.

Pour une ugoira :

- recuperer les metadonnees ugoira ;
- telecharger le zip de frames ;
- generer un fichier de concat avec les delais exacts ;
- utiliser FFmpeg pour produire une video dans le format configure ;
- poster la video si elle respecte la limite de fichier.

Le Dockerfile doit installer FFmpeg si l'implementation ugoira en a besoin au runtime.

Le contenu R-18 ou marque sensible par Pixiv est considere comme contenu sensible pour le garde-fou NSFW. L'implementation doit inspecter les champs de restriction exposes par l'API Pixiv, par exemple `xRestrict` si present, et privilegier le signal le plus restrictif quand plusieurs champs existent.

### Misskey

`MisskeySite` construit son pattern depuis `SAUCY_MISSKEY_DOMAINS`.

Valeur par defaut :

- `misskey.io,misskey.design,oekakiskey.com`

Le module appelle `{instance}/api/notes/show` avec `noteId`.

La reponse doit produire :

- un embed par fichier image ;
- auteur, avatar, texte, date, image et footer Misskey ;
- aucun embed pour les fichiers non image.

Comme Saucy, le module a pour but principal de corriger les cas ou l'embed natif est insuffisant, notamment notes multi-images et fichiers sensibles.

Un fichier Misskey avec `isSensitive=true` est considere comme contenu sensible pour le garde-fou NSFW.

## Gestion NSFW

Avant tout envoi public, le pipeline verifie l'indicateur sensible de la reponse.

Si le contenu est sensible et que le salon Discord n'est pas NSFW :

- ne pas poster de reponse ;
- ne pas masquer l'embed natif ;
- logguer la decision cote bot avec site, salon et message source ;
- supprimer le message temporaire si un message temporaire a ete poste.

Si le salon est NSFW ou si le contenu n'est pas sensible, le pipeline continue normalement.

## Configuration

Ajouter a `.env.example` :

- `SAUCY_LINK_EMBEDS_ENABLED=true`
- `SAUCY_LINK_CACHE_TTL_SECONDS=3600`
- `SAUCY_MAX_LINKS_PER_MESSAGE=8`
- `SAUCY_MAX_EMBEDS_PER_MESSAGE=4`
- `SAUCY_MAX_FILE_BYTES=10485760`
- `SAUCY_SEND_MATCHED_MESSAGE=true`
- `SAUCY_MATCHED_MESSAGE=Traitement du lien en cours...`
- `SAUCY_PIXIV_SESSION_COOKIE=`
- `SAUCY_PIXIV_IMAGE_LIMIT=5`
- `SAUCY_PIXIV_UGOIRA_FORMAT=mp4`
- `SAUCY_PIXIV_UGOIRA_BITRATE=2000`
- `SAUCY_MISSKEY_DOMAINS=misskey.io,misskey.design,oekakiskey.com`

Les valeurs invalides doivent retomber sur les valeurs par defaut avec un warning.

## Permissions Discord

Le bot doit pouvoir lire l'historique, envoyer des messages, envoyer des embeds et joindre des fichiers dans les salons cibles.

La suppression d'embed natif requiert `MANAGE_MESSAGES`. Si cette permission manque, le remplacement reste envoye mais l'embed natif n'est pas masque, avec un log de debug ou warning leger.

Le pipeline ne doit pas mentionner d'utilisateurs ou roles depuis le contenu externe. Toutes les reponses envoyees par le pipeline doivent desactiver les mentions autorisees via JDA.

## Erreurs et degradations

Une erreur sur un lien ne doit pas empecher le traitement des autres liens du meme message.

En cas d'echec API ou de parsing :

- logguer l'erreur avec le site et l'URL ;
- supprimer le message temporaire ;
- ne pas masquer l'embed natif ;
- ne pas poster de message d'erreur public.

En cas de fichier trop lourd :

- Twitter/X : fallback lien FxTwitter comme Saucy ;
- Pixiv : essayer une qualite inferieure quand elle existe, sinon ignorer le fichier concerne ;
- ugoira : si la video finale depasse la limite, logguer et ne rien poster pour cette ugoira.

## Tests

Ajouter des tests JUnit centres sur la logique testable sans Discord reel :

- matching des URLs Twitter/X, Pixiv et Misskey ;
- limites de liens par message ;
- ignore des liens `<...>` et `||...||` ;
- parsing des domaines Misskey configurables ;
- lecture de configuration avec fallback sur valeurs par defaut ;
- cache memoire avec TTL ;
- creation de reponses pour chaque site avec clients HTTP mocks ;
- detection de contenu sensible et blocage en salon non-NSFW via un service pur si possible ;
- decoupage de reponses multi-embeds/fichiers dans `SaucyMessageSender`.

Validation attendue :

- `./gradlew test`
- `./gradlew compileJava`
- `./gradlew shadowJar` si le Dockerfile ou le packaging FFmpeg est modifie dans l'implementation.

## Hors scope

Ne pas ajouter de configuration par guilde ou par salon en base de donnees pour ce premier jet.

Ne pas ajouter de commande slash dediee tant que l'automatisme global couvre le besoin.

Ne pas persister le cache dans PostgreSQL.

Ne pas supprimer ou remplacer `SuppressLinkEmbedListener` dans ce changement. Il reste responsable de `NO_EMBED_CHANNEL_IDS`.
