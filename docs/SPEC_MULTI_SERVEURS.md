# Spécification — Bot public multi-serveurs

## 1. Objectif

Faire évoluer le bot actuel vers un fonctionnement multi-serveurs, puis vers une installation publique.

Chaque serveur Discord doit disposer de sa propre configuration, de ses propres données et de ses propres états d'exécution. Aucune donnée métier ou configuration ne doit traverser la frontière entre deux serveurs.

Le déploiement se fera en deux phases :

1. version privée multi-serveurs ;
2. version publique multi-serveurs.

## 2. Principes fondamentaux

- Le bot sera publiquement installable.
- Aucun module ne sera activé par défaut.
- Les fonctionnalités seront activées par module et par serveur.
- Les commandes seront enregistrées globalement.
- Les commandes d'un module désactivé resteront visibles, mais répondront de manière éphémère que la fonctionnalité doit être activée.
- Les administrateurs pourront masquer manuellement des commandes depuis les paramètres d'intégration Discord.
- Aucun backoffice web n'est prévu.
- PostgreSQL stockera les configurations propres aux serveurs.
- Les variables d'environnement seront réservées aux secrets et aux limites techniques globales.

## 3. États d'un module

Chaque module pourra être dans l'un des états suivants :

- **désactivé** : le serveur ne souhaite pas utiliser le module ;
- **actif** : la configuration et les permissions sont valides ;
- **suspendu** : le module était activé, mais une ressource ou une permission nécessaire a disparu.

La configuration d'un module suspendu est conservée.

L'activation est atomique : un module ne devient actif que lorsque tous ses réglages obligatoires, salons, rôles et permissions ont été validés.

Un mécanisme central devra contrôler ces états pour toutes les commandes et tous les listeners.

## 4. Installation sur un serveur

Lors de l'arrivée du bot :

1. le serveur est enregistré en base ;
2. tous les modules sont désactivés ;
3. le bot tente d'envoyer le message d'accueil dans le salon système ;
4. à défaut, il utilise le premier salon textuel accessible ;
5. à défaut, il tente un message privé au propriétaire ;
6. si tout échoue, `/onboarding` reste disponible.

Le bot demandera uniquement les permissions minimales lors de son installation. Il ne demandera jamais la permission `ADMINISTRATOR` pour lui-même.

Les permissions supplémentaires seront détectées selon les modules choisis pendant l'onboarding.

## 5. Administration

### Permissions

La configuration est réservée :

- au propriétaire du serveur ;
- aux membres possédant la permission native Discord `ADMINISTRATOR`.

Le contrôle porte sur la permission Discord, pas sur le nom ou l'identifiant d'un rôle.

### Commandes permanentes

Ces commandes restent toujours disponibles :

- `/ping` ;
- `/help` ;
- `/privacy` ;
- `/onboarding` ;
- `/config`.

### `/onboarding`

Cette commande assure la première configuration :

- choix de la langue ;
- sélection des modules ;
- saisie des paramètres indispensables ;
- vérification des permissions du bot ;
- vérification des rôles et salons ;
- activation uniquement après validation complète.

### `/config`

Cette commande permet ensuite :

- de consulter les modules ;
- d'activer ou désactiver un module ;
- de modifier ses réglages ;
- de consulter les modules suspendus ;
- de relancer leur validation ;
- de modifier la langue et le salon de logs ;
- de réinitialiser la configuration ;
- de demander la suppression des données du serveur.

### Audit

Chaque changement de configuration conserve :

- l'identifiant du serveur ;
- l'identifiant de l'administrateur ;
- le module concerné ;
- l'ancienne et la nouvelle valeur ;
- la date du changement.

## 6. Langues et dates

Le français et l'anglais seront disponibles dès la version publique.

La langue initiale sera déterminée depuis la langue préférée du serveur Discord. Le français sera utilisé en fallback. Elle restera modifiable dans `/config`.

Toutes les dates seront stockées en UTC et affichées avec les timestamps natifs de Discord afin de respecter automatiquement le fuseau horaire de chaque utilisateur.

## 7. Commandes et permissions

| Module | Permission utilisateur par défaut |
|---|---|
| Configuration | `ADMINISTRATOR` ou propriétaire |
| Avertissements | `MODERATE_MEMBERS` |
| Musique | Accessible à tous |
| Enregistrement vocal | `MANAGE_CHANNEL` |
| Messages envoyés au nom du bot | `MANAGE_MESSAGES` |
| Sanctions automatiques | Configuration par un administrateur |
| Rôle automatique | Configuration par un administrateur |

Les administrateurs pourront modifier la visibilité réelle des commandes depuis les paramètres **Intégrations** de Discord. Cette procédure devra être documentée.

## 8. Modules

### 8.1 Avertissements

- Chaque avertissement appartient obligatoirement à un serveur.
- Un avertissement créé sur le serveur A n'est jamais visible sur le serveur B.
- Les utilisateurs Discord restent des identités globales.
- La création et la consultation nécessitent `MODERATE_MEMBERS`.
- Le salon de logs est facultatif.
- Sans salon de logs, un avertissement textuel reste possible.
- Une preuve ne peut être conservée durablement que si elle est envoyée dans le salon de logs.

### 8.2 Rôle automatique

- Le rôle est configuré séparément pour chaque serveur.
- Il est attribué uniquement aux nouveaux membres humains.
- Les bots sont toujours ignorés.
- Aucun parcours global des membres n'est effectué au démarrage.
- Un administrateur peut lancer explicitement un rattrapage des membres existants.
- Si le rôle est supprimé, le module est suspendu.

### 8.3 Musique

- Accessible à tous les membres.
- Les lecteurs, files et connexions vocales sont isolés par serveur.
- Les états sont nettoyés lorsque le bot quitte le serveur.
- Des limites par serveur empêchent une file ou un traitement excessif.

### 8.4 Enregistrement vocal

- Permission `MANAGE_CHANNEL` par défaut.
- Les administrateurs peuvent assouplir cette permission depuis Discord.
- Un seul enregistrement est possible par serveur.
- L'utilisateur doit être présent dans le salon concerné.
- Le démarrage est annoncé publiquement avec :
  - le salon enregistré ;
  - l'utilisateur ayant lancé l'action ;
  - la manière de consulter ou arrêter l'enregistrement.
- Une durée maximale est définie globalement.
- Le nombre global d'enregistrements simultanés est limité.
- Ces plafonds sont configurables dans `.env`.
- Les fichiers temporaires sont supprimés après un envoi réussi.
- En cas d'échec, ils sont conservés pendant 24 heures maximum par défaut.
- Ce délai est configurable globalement.
- Aucun archivage vocal permanent n'est réalisé par le bot.

### 8.5 Messages envoyés au nom du bot

- `/msgsend` et `/msgedit` nécessitent `MANAGE_MESSAGES`.
- Le salon et le message doivent appartenir au serveur courant.
- Ces commandes appartiennent à un module activable.
- Le bot vérifie ses propres permissions avant l'envoi ou la modification.

### 8.6 Enrichissement des liens

- Lorsqu'il est activé, le module s'applique à tout le serveur.
- Une liste de salons exclus peut être définie.
- Les secrets externes, caches et limites techniques restent globaux.
- Le serveur ne configure que l'activation et les exclusions.

### 8.7 Suppression des intégrations

- Le module agit uniquement dans les salons explicitement sélectionnés.
- Aucun salon n'est surveillé par défaut.
- La configuration est propre à chaque serveur.

## 9. Sanctions automatiques

### Salon de logs

Un salon de logs unique peut être configuré par serveur.

Il est :

- facultatif pour utiliser le bot ;
- obligatoire pour activer l'anti-raid ou une sanction automatique.

### Actions disponibles

Une règle peut :

- journaliser uniquement ;
- exclure temporairement ;
- expulser ;
- bannir.

### Rôles protégés

Une liste commune de rôles protégés est définie par serveur.

Sont toujours protégés, même sans figurer dans cette liste :

- le propriétaire ;
- les membres possédant `ADMINISTRATOR` ;
- les bots.

Le bot doit également vérifier qu'il peut agir sur le membre selon la hiérarchie des rôles Discord.

### Anti-raid

Le module contient deux règles indépendantes.

#### Comptes récents

Valeurs initiales :

- compte âgé de moins de 7 jours ;
- action initiale : expulsion.

#### Spam de mentions

Valeurs initiales :

- 5 mentions ;
- fenêtre de 10 secondes ;
- exclusion temporaire de 10 minutes.

Chaque règle peut être activée séparément et recevoir une autre action.

### Sanction automatique par salon

- Une liste de salons déclencheurs est configurée.
- L'administrateur doit choisir explicitement l'action avant activation.
- Aucune sanction n'est présélectionnée.
- La suppression du message déclencheur est configurable.
- La durée d'historique supprimée lors d'un bannissement est configurable pour ce module.
- La valeur initiale de suppression de l'historique est nulle.

### Sanction automatique par rôle

- Une liste de rôles déclencheurs est configurée.
- L'administrateur doit choisir explicitement l'action avant activation.
- Aucune sanction n'est présélectionnée.
- La durée d'historique supprimée lors d'un bannissement est configurable indépendamment du module par salon.
- La valeur initiale est nulle.

## 10. Stockage des données

### Organisation générale

La base contiendra au minimum les concepts suivants :

- serveur Discord et cycle de vie ;
- modules du serveur ;
- configurations propres aux modules ;
- utilisateurs Discord globaux ;
- avertissements rattachés à un serveur ;
- historique des changements de configuration.

Les configurations ne seront pas stockées dans un unique JSON libre ni dans une table monolithique regroupant tous les modules.

### Données non conservées

Le bot ne dupliquera pas durablement :

- le nom du serveur ;
- son icône ;
- son nombre de membres ;
- le propriétaire du serveur.

Ces informations seront récupérées depuis Discord lorsque nécessaire.

### Migration historique

Tous les avertissements existants seront rattachés au serveur actuellement identifié par `GUILD_ID`.

Après la migration :

- `GUILD_ID` n'est plus nécessaire au fonctionnement ;
- les commandes ne sont plus enregistrées sur un serveur unique ;
- toutes les requêtes métier exigent un contexte de serveur.

### Flyway

- Flyway devient responsable du schéma.
- Les migrations sont versionnées.
- Hibernate utilise `validate` en production.
- Hibernate ne modifie pas automatiquement le schéma de production.
- La migration doit fonctionner sur une base existante et sur une base vide.

## 11. Preuves et salon de logs

Les preuves d'avertissement sont stockées uniquement sur Discord :

- le fichier est publié dans le salon de logs ;
- la base conserve seulement une référence vers le message ou la pièce jointe ;
- le fichier temporaire local est supprimé après l'envoi ;
- la durée de conservation réelle dépend ensuite du serveur Discord.

Une suppression des données internes du bot ne supprime jamais les messages, logs ou preuves déjà présents sur Discord.

Cette limite doit être clairement documentée.

## 12. Départ et réinstallation du bot

Lors du départ du bot :

- le serveur est marqué comme inactif ;
- les connexions vocales et états temporaires sont nettoyés ;
- les modules cessent immédiatement de fonctionner ;
- les données entrent en période de rétention.

La rétention est de 30 jours par défaut et peut être modifiée globalement dans `.env`.

Si le bot revient pendant cette période :

- aucune ancienne fonctionnalité n'est réactivée automatiquement ;
- un administrateur peut restaurer l'ancienne configuration ;
- ou repartir avec une configuration vide.

Après expiration du délai, les données internes du serveur sont supprimées automatiquement.

Un administrateur peut également demander une suppression immédiate depuis `/config`, avec double confirmation.

## 13. Confidentialité

`/privacy` doit expliquer :

- les données stockées ;
- leur finalité ;
- la durée de rétention ;
- les limites de la suppression interne ;
- le maintien éventuel de messages sur Discord ;
- la procédure permettant de contacter l'exploitant du bot.

Pour la première version, les demandes individuelles seront traitées manuellement.

Une procédure interne permettra de :

- rechercher les données liées à un identifiant Discord ;
- les exporter ;
- les supprimer lorsque la demande est validée.

Il n'existe pas de suppression individuelle automatique en libre-service, notamment pour éviter qu'un utilisateur puisse supprimer directement ses propres avertissements de modération.

## 14. Fiabilité et sécurité

### Perte d'une ressource Discord

Lorsqu'un rôle ou un salon configuré est supprimé :

- sa référence est invalidée ;
- le module est suspendu s'il ne peut plus fonctionner ;
- sa configuration est conservée ;
- les administrateurs sont informés lorsque cela est possible.

La suppression du salon de logs suspend notamment les modules de sanction automatique.

### Perte d'une permission

Lorsqu'une permission du bot est retirée :

- le module concerné est suspendu ;
- sa configuration n'est pas supprimée ;
- il peut être réactivé après rétablissement des permissions.

### Indisponibilité de PostgreSQL

Les configurations sont mises en cache par serveur.

En cas de panne :

- les sanctions automatiques sont suspendues ;
- les avertissements et autres écritures métier sont suspendus ;
- `/config` et les actions nécessitant la base deviennent indisponibles ;
- les modules sans sanction ni écriture peuvent continuer avec la dernière configuration connue.

### Logs techniques

Les logs techniques peuvent contenir :

- l'identifiant du serveur ;
- l'identifiant de l'utilisateur concerné lorsque nécessaire ;
- le module ;
- l'action ;
- son résultat.

Ils ne doivent pas contenir :

- le contenu des messages ;
- les preuves ;
- les fichiers ;
- les enregistrements vocaux ;
- les secrets.

## 15. Ressources et montée en charge

Le bot applique :

- des plafonds techniques globaux configurés dans `.env` ;
- des limites par serveur ;
- un nombre maximal global d'enregistrements ;
- une durée maximale d'enregistrement ;
- des limites sur les traitements de médias ;
- des limites sur les files musicales.

Les intents Discord doivent être réduits aux besoins réels. `ALL_INTENTS` et le cache complet des membres doivent disparaître.

Le chargement global de tous les membres au démarrage est interdit. Les opérations de rattrapage doivent être explicites.

Le sharding n'est pas nécessaire pour la première version publique. Il sera introduit uniquement lorsque le volume réel de serveurs l'exigera.

## 16. Tests attendus

Les tests d'intégration utiliseront PostgreSQL avec Testcontainers.

Ils devront notamment couvrir :

- l'application des migrations Flyway sur une base vide ;
- la migration de la base historique ;
- deux serveurs ayant des configurations différentes ;
- l'impossibilité de lire les avertissements d'un autre serveur ;
- l'isolation de l'anti-raid ;
- l'isolation des rôles et salons ;
- l'isolation de la musique et de l'enregistrement ;
- un serveur nouvellement ajouté avec tous les modules désactivés ;
- la suspension après suppression d'un rôle ou d'une permission ;
- la rétention de 30 jours ;
- la restauration après réinstallation ;
- la suppression complète des données internes ;
- le comportement sécurisé pendant une panne PostgreSQL.

La CI doit exécuter :

- la compilation ;
- les tests unitaires ;
- les tests Testcontainers ;
- la construction du JAR.

## 17. Déploiement

### Phase 1 — Version privée multi-serveurs

Cette phase comprend :

- Flyway et le nouveau modèle de données ;
- la migration du serveur historique ;
- la configuration modulaire ;
- le contrôle central des modules ;
- l'isolation de toutes les fonctionnalités existantes ;
- une première version de `/config` ;
- les tests PostgreSQL ;
- la validation sur un deuxième serveur privé.

### Phase 2 — Version publique multi-serveurs

Cette phase comprend :

- les commandes globales ;
- `/onboarding` ;
- le français et l'anglais ;
- les permissions minimales à l'installation ;
- la rétention et la restauration ;
- `/privacy` ;
- les quotas et protections de ressources ;
- la réduction des intents ;
- la documentation publique ;
- la bêta fermée ;
- l'ouverture publique.

### Stratégie d'ouverture

1. migrer et valider le serveur historique ;
2. tester un second serveur privé très différent ;
3. ouvrir une bêta fermée ;
4. corriger les problèmes d'onboarding, de permissions et de ressources ;
5. publier le lien d'installation.

## 18. Hors périmètre initial

- Backoffice web.
- Activation automatique de modules.
- Configuration par commande individuelle.
- Stockage permanent des preuves ou enregistrements.
- Suppression automatique des messages Discord lors d'une purge interne.
- Sharding dès la première version.
- Suppression individuelle automatique en libre-service.
