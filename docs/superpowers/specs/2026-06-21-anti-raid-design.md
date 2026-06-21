# Design anti-raid et anti-spam

## Objectif

Ajouter une protection automatique contre deux comportements a risque :

- comptes Discord trop recents qui rejoignent le serveur ;
- spam de mentions utilisateur ou role par un membre deja present.

Le bot doit bannir automatiquement dans ces deux cas et logger chaque action.

## Regles fonctionnelles

### Compte trop recent

Au moment ou un membre rejoint le serveur, le bot compare la date de creation du compte Discord a l'heure courante.

Si le compte a moins de 7 jours, le bot tente de bannir le membre avec une raison explicite.

Le seuil doit etre configurable par variable d'environnement, avec une valeur par defaut de 7 jours.

### Spam de mentions

Le bot ecoute les messages de guilde.

Pour chaque auteur, il additionne les mentions utilisateur et role presentes dans ses messages sur une fenetre glissante de 10 secondes.

Si le total atteint 5 mentions ou plus, le bot tente de bannir l'auteur avec une raison explicite.

La limite de mentions et la duree de fenetre doivent etre configurables par variables d'environnement, avec des valeurs par defaut de 5 mentions et 10 secondes.

## Exemptions et garde-fous

Le bot ne doit pas bannir :

- les bots ;
- le proprietaire du serveur ;
- un membre que le bot ne peut pas gerer selon la hierarchie Discord ;
- un membre possedant un role present dans `AUTO_BAN_EXEMPT_ROLE_IDS`.

Ces garde-fous s'appliquent aux deux regles.

## Architecture

Ajouter un listener global auto-decouvert par le mecanisme existant :

- `AntiRaidListener` dans `listeners/global/`.

Extraire la logique metier dans un service testable :

- `AntiRaidService` dans `services/`.

Le listener recupere les evenements JDA, applique les garde-fous Discord, puis delegue la decision de spam de mentions au service.

Le service conserve en memoire les timestamps et poids de mentions par couple guilde/utilisateur. Cette memoire est volontairement volatile : une fenetre de 10 secondes ne justifie pas une persistance en BDD.

## Configuration

Ajouter a `.env.example` :

- `ANTI_RAID_MIN_ACCOUNT_AGE_DAYS=7`
- `ANTI_RAID_MENTION_LIMIT=5`
- `ANTI_RAID_MENTION_WINDOW_SECONDS=10`

Le listener continue de reutiliser :

- `LOG_CHANNEL_ID`
- `AUTO_BAN_EXEMPT_ROLE_IDS`

## Logs

Chaque tentative de ban doit produire :

- un log applicatif `info` en cas de succes ;
- un log applicatif `error` en cas d'echec ;
- un embed dans `LOG_CHANNEL_ID` quand le salon existe.

L'embed doit inclure :

- l'utilisateur ;
- la regle declenchee ;
- les details utiles, par exemple age du compte ou total de mentions ;
- l'erreur en cas d'echec.

## Validation

Le repo n'a pas de suite de tests existante. Pour rendre la logique verifiable, ajouter une dependance de test minimale et couvrir `AntiRaidService`.

Validation attendue :

- test rouge sur la detection de 5 mentions en moins de 10 secondes ;
- test rouge sur l'absence de detection quand la fenetre expire ;
- implementation minimale ;
- `./gradlew test` ;
- `./gradlew compileJava`.
