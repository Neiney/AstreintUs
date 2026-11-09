# Configuration MDM Asteintus

Asteintus n’active pas l’astreinte tant que la politique d’alerte gérée est incomplète.

## Restrictions d’application Android Enterprise

| Clé | Type | Exemple | Requis |
|---|---|---|---|
| `oncall_imap_mailbox` | chaîne | `INBOX/ONCALL` | Oui |
| `oncall_allowed_senders` | chaîne CSV | `alerts@monitoring.example,pager@monitoring.example` | Oui |
| `oncall_subject_prefix` | chaîne | `[P1]` | Oui |

Les règles sont distribuées par le MDM et doivent être considérées en lecture seule par l’utilisateur.

## Posture exigée

- terminal chiffré et conforme à la politique MDM ;
- notifications et alertes plein écran autorisées ;
- application autorisée à exécuter son service d’astreinte ;
- exclusion batterie/Doze validée selon le constructeur ;
- effacement distant activé ;
- boîte IMAP et dossier d’astreinte dédiés.

## Validation opérationnelle

1. Déposer un e-mail admissible dans le dossier configuré.
2. Mesurer le délai jusqu’à l’alarme locale.
3. Répéter après redémarrage, perte/rétablissement réseau et mise en veille.
4. Rejeter un e-mail avec expéditeur ou sujet non conforme.
5. Vérifier que le tableau de bord affiche un état sain après une synchronisation.
