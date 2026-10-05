# Kiss-Shot Acerola-Orion Heart-Under-Blade

**Kiss-Shot Acerola-Orion Heart-Under-Blade** est un bot Discord communautaire qui réunit des outils de modération, de musique, d'enregistrement vocal et d'enrichissement de liens. Il est développé par [CILA](https://cila.camelia-studio.org), la branche technique de l'association [Camélia Studio](https://camelia-studio.org).

> Le projet est actuellement en développement. Son ouverture à d'autres serveurs Discord est en cours de préparation.

- [Découvrir le bot et ses fonctionnalités](https://cila.camelia-studio.org/kiss-shot/)
- [Consulter le code source](https://git.crystalyx.net/camelia-studio/Kiss-Shot_Acerola-Orion_Heart-Under-Blade)
- [Rejoindre le serveur Discord de Camélia Studio](https://discord.gg/nBuZ9vJ)

## Fonctionnalités

### Modération

- avertissements horodatés et historique par membre ;
- détection anti-raid ;
- audit des membres : journalisation des changements de pseudo et d'avatar pour repérer les usurpations d'identité ;
- bannissement automatique selon un rôle ou un salon surveillé ;
- attribution d'un rôle à l'arrivée d'un nouveau membre.

### Musique et salons vocaux

- lecture de pistes et gestion d'une file d'attente ;
- pause, reprise, répétition, lecture aléatoire et réglage du volume ;
- enregistrement d'un salon vocal et envoi du résultat au format MP3.

### Liens enrichis

- aperçus automatiques pour Twitter/X, Pixiv et plusieurs instances Misskey ;
- prise en charge des médias, albums Pixiv et ugoira ;
- protection des contenus NSFW et suppression configurable des aperçus natifs de Discord.

### Utilitaires

- envoi et modification de messages au nom du bot ;
- commande de vérification de disponibilité ;
- accueil automatique des nouveaux membres.

## Commandes slash

| Domaine | Commandes |
| --- | --- |
| Modération | `/averto`, `/avertolist` |
| Musique | `/play`, `/pause`, `/stop`, `/skip`, `/queue`, `/nowplaying`, `/repeat`, `/shuffle`, `/volume` |
| Vocal | `/record` |
| Utilitaires | `/msgsend`, `/msgedit`, `/ping` |

Les commandes sont enregistrées globalement et ne sont utilisables que dans un serveur Discord.

## Technologies

- Java 25 et [JDA 6](https://github.com/discord-jda/JDA) ;
- [Lavaplayer](https://github.com/lavalink-devs/lavaplayer) pour la lecture audio ;
- l'API Kiss-Shot (HTTP/JSON) pour les données : serveurs, configuration des modules, historique et avertissements ;
- Gradle et Shadow pour produire un JAR autonome.

## Installation locale

### Prérequis

- JDK 25 ;
- une instance de l'API Kiss-Shot accessible ;
- FFmpeg pour l'enregistrement MP3 et le rendu des animations Pixiv.

### Configuration

Copier le fichier d'exemple, puis renseigner au minimum le jeton Discord et l'accès à l'API :

```bash
cp .env.example .env
```

Les principales variables sont :

| Variable | Utilisation |
| --- | --- |
| `BOT_TOKEN` | Jeton de l'application Discord |
| `API_BASE_URL` | URL de l'API Kiss-Shot, préfixe `/api` compris |
| `API_BOT_TOKEN` | Token statique du bot, envoyé en `Authorization: Bearer` (même valeur que côté API) |
| `SAUCY_*` | Configuration des aperçus Twitter/X, Pixiv et Misskey |
| `RECORDING_*` | Configuration des enregistrements vocaux |

Les salons, rôles, états de modules et autres réglages propres à un serveur sont configurés avec `/config` et stockés par l'API Kiss-Shot. La liste complète des variables globales et leurs valeurs par défaut se trouve dans [`.env.example`](.env.example).

### Données et API

Le bot ne se connecte plus à une base de données : toute la persistance passe par l'API Kiss-Shot, qui gère le schéma et ses migrations. Le bot vérifie au démarrage que `API_BASE_URL` et `API_BOT_TOKEN` sont renseignés.

L'API ne connaît pas l'état de Discord : la validation qui en dépend (existence des salons et rôles, permissions du bot) reste dans le bot, qui n'envoie `ACTIVE` qu'une fois celle-ci réussie et suspend lui-même un module dont la validation échoue. La configuration lue est gardée 30 secondes en mémoire, ce qui laisse une modification faite depuis le backoffice devenir visible rapidement.

### Compilation et lancement

```bash
./gradlew compileJava
./gradlew shadowJar
java -jar build/libs/kiss-shot-acerola.jar
```

### Tests

```bash
./gradlew test
```

Les appels à l'API sont testés contre un faux serveur HTTP local : aucune API ni base de données n'est nécessaire. La CI exécute cette suite avant de construire le fat JAR.

## Architecture et contribution

Le projet découvre automatiquement ses composants par réflexion au démarrage :

- les implémentations de `ISlashCommand` placées dans `commands/**` ;
- les sous-classes de `ListenerAdapter` placées dans `listeners/global/**`.

Une nouvelle commande ou un nouveau listener n'a donc pas besoin d'être enregistré manuellement s'il respecte cette organisation.

Les échanges avec l'API sont regroupés dans `api/` : `ApiClient` (HTTP, authentification, erreurs) et un client par ressource (`ServerApi`, `ModuleApi`, `AvertoApi`). Les services (`services/`) s'appuient dessus.

## Crédits

- **Bot et développement** — [CILA](https://cila.camelia-studio.org), branche de [Camélia Studio](https://camelia-studio.org).
- **Direction visuelle du site** — inspirée de [Gachamélia](https://git.crystalyx.net/camelia-studio/Gachamelia), autre bot communautaire de Camélia Studio.
- **Univers et personnage** — Kiss-Shot Acerola-Orion Heart-Under-Blade appartient à l'univers de [*Kizumonogatari*](https://www.kizumonogatari-movie.com/), œuvre de [NisiOisiN et VOFAN](https://www.kizumonogatari-movie.com/original/) publiée par Kodansha. L'adaptation animée est produite par [Aniplex, Kodansha et Shaft](https://www.aniplex.co.jp/lineup/kizumonogatari/).
- **Illustrations du site et avatar du bot** — œuvre de [wukloo publiée sur Pixiv](https://www.pixiv.net/en/artworks/60752234).
- **Système d'aperçus de liens** — inspiré de [Saucy](https://github.com/Sn0wCrack/saucybot-discord), créé par Sn0wCrack.
- **Typographies du site** — [DM Sans](https://fonts.google.com/specimen/DM+Sans) et [Fraunces](https://fonts.google.com/specimen/Fraunces), distribuées via Google Fonts.

La page [Crédits](https://cila.camelia-studio.org/kiss-shot/credits.html) du site rassemble également ces attributions.

Ce projet de fans n'est ni affilié, ni sponsorisé, ni approuvé par les ayants droit de *Kizumonogatari* ou de la série *Monogatari*.

## Licence

Le code source est distribué sous [licence MIT](licence.txt), © 2026 Camélia Studio.
