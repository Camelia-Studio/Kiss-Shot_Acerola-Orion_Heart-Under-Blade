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
- Hibernate, HikariCP et PostgreSQL pour les données ;
- Flyway pour les migrations de base de données ;
- Gradle et Shadow pour produire un JAR autonome.

## Installation locale

### Prérequis

- JDK 25 ;
- Docker avec Docker Compose, ou une instance PostgreSQL accessible ;
- FFmpeg pour l'enregistrement MP3 et le rendu des animations Pixiv.

### Configuration

Copier le fichier d'exemple, puis renseigner au minimum le jeton Discord et les accès PostgreSQL :

```bash
cp .env.example .env
```

Les principales variables sont :

| Variable | Utilisation |
| --- | --- |
| `BOT_TOKEN` | Jeton de l'application Discord |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Connexion à PostgreSQL |
| `ANTI_RAID_*` | Valeurs techniques temporaires de l'anti-raid, avant leur migration dans le ticket dédié |
| `SAUCY_*` | Configuration des aperçus Twitter/X, Pixiv et Misskey |
| `RECORDING_*` | Configuration des enregistrements vocaux |

Les salons, rôles, états de modules et autres réglages propres à un serveur sont configurés avec `/config` et stockés dans PostgreSQL. La liste complète des variables globales et leurs valeurs par défaut se trouve dans [`.env.example`](.env.example).

### Base de données

La configuration Docker expose PostgreSQL sur le port local `5434` :

```bash
docker compose up -d
```

Flyway applique automatiquement les migrations versionnées au démarrage, avant qu'Hibernate valide le schéma. Hibernate ne crée ni ne modifie les tables.

Pour la première mise à jour d'une base historique, conserver temporairement `GUILD_ID` dans `.env` : Flyway l'utilise pour rattacher les avertissements existants et les anciens réglages de modules à leur serveur. La migration V3 reconnaît également `DEFAULT_ROLE_ID`, `LOG_CHANNEL_ID`, `AUTO_BAN_CHANNEL_IDS`, `AUTO_BAN_ROLE_IDS`, `AUTO_BAN_EXEMPT_ROLE_IDS` et `NO_EMBED_CHANNEL_IDS`. Tous les modules migrés restent désactivés. Ces variables peuvent être supprimées après la migration et ne sont pas nécessaires sur une base vide.

### Compilation et lancement

```bash
./gradlew compileJava
./gradlew shadowJar
java -jar build/libs/kiss-shot-acerola.jar
```

### Tests

```bash
# Suite unitaire
./gradlew test

# Migrations et isolation multi-serveurs sur PostgreSQL réel (Docker requis)
./gradlew integrationTest
```

La CI exécute les deux suites avant de construire le fat JAR.

## Architecture et contribution

Le projet découvre automatiquement ses composants par réflexion au démarrage :

- les implémentations de `ISlashCommand` placées dans `commands/**` ;
- les sous-classes de `ListenerAdapter` placées dans `listeners/global/**` ;
- les entités implémentant `IEntity` placées dans `models/**`.

Une nouvelle commande, un nouveau listener ou une nouvelle entité n'a donc pas besoin d'être enregistré manuellement s'il respecte cette organisation.

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
