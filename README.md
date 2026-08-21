# Kiss-Shot Acerola-Orion Heart-Under-Blade

**Kiss-Shot Acerola-Orion Heart-Under-Blade** est un bot Discord qui a pour but de faciliter la gestion de serveurs Discord. Cet outil est développé par la branche **CILA** de l'association **Camélia Studio**.


Liens utiles :


- Site web Camélia Studio : https://camelia-studio.org
- Site web CILA : https://cila.camelia-studio.org
- Notre serveur Discord : https://discord.gg/nBuZ9vJ

## Base de données

Flyway applique automatiquement les migrations versionnées au démarrage, avant
qu'Hibernate valide le schéma. Hibernate ne crée ni ne modifie plus les tables.

Pour la première mise à jour d'une base historique, conserver temporairement
`GUILD_ID` dans `.env` : Flyway l'utilise une seule fois pour rattacher les
avertissements existants à leur serveur. La variable peut être supprimée après
la migration V2 et n'est pas nécessaire sur une base vide.

Les commandes slash sont enregistrées globalement et `GUILD_ID` n'intervient
plus dans le fonctionnement courant du bot.
