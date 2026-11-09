# Statut d’implémentation

## Implémenté côté application

- politique d’éligibilité gérée par MDM ;
- dossier IMAP d’astreinte configurable ;
- premier curseur IMAP silencieux ;
- séparation `(mailbox, UID)` et prise en compte de `UIDVALIDITY` ;
- service IMAP IDLE pendant l’astreinte, avec reconnexion et worker de rattrapage ;
- état local de santé de synchronisation ;
- base SQLCipher avec clé conservée via Android Keystore ;
- migration de la DB Room legacy vers la DB chiffrée ;
- suppression des champs SMTP inutilisés ;
- durcissement du WebView HTML, receiver de boot non exporté et crash logs non persistés ;
- Gradle wrapper et CI GitHub Actions.

## Validation obligatoire avant release

L’espace de travail de livraison ne contient ni JDK ni Android SDK. La commande `./gradlew testDebugUnitTest --stacktrace` échoue avant compilation car Java est absent. Aucun résultat de build, lint ou test Android ne peut donc être revendiqué ici.

Exécuter dans un environnement avec JDK 17 et Android SDK :

```bash
./gradlew testDebugUnitTest lintDebug
```

Puis réaliser la recette sur terminaux MDM : démarrage après boot, Doze, réseau perdu/rétabli, IMAP IDLE du fournisseur retenu, migration de DB et délai P95 inférieur à trois minutes.

## Non implémenté volontairement

L’escalade externe n’est pas intégrée : le choix validé est un état dégradé local. Le système de supervision reste responsable du canal secondaire.
