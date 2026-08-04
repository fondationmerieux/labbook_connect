# 00 — Vue d'ensemble

## Le problème à résoudre

Un laboratoire de biologie médicale possède des automates d'analyse. Chacun sait recevoir
des demandes d'examen et renvoyer des résultats — mais chacun le fait à sa manière, avec
son protocole, son format de message et son vocabulaire de codes.

Le système de gestion de laboratoire (LIS), lui, veut une seule façon de dialoguer.

LabBook Connect se place entre les deux. Il expose au LIS une interface unique et
normalisée, et il traduit vers chaque automate. C'est un **middleware** ; dans sa version
actuelle, il ne fait qu'une chose : la passerelle vers les automates.

```
LIS                     LabBook Connect                    Automates
 |                            |                                |
 |  HL7 v2.5.1 sur HTTP       |    protocole du constructeur    |
 |<-------------------------->|<------------------------------->|
 |    toujours identique      |    différent pour chacun        |
```

## Ce qui est normalisé, et pourquoi

Côté LIS, Connect implémente le profil **IHE-LAW** (*Laboratory Analytical Workflow*),
défini par IHE et promu par l'IVD Industry Connectivity Consortium. Ce profil décrit trois
transactions entre un automate et son gestionnaire d'automates (le rôle que tient ici le
LIS) :

| Transaction | Sens | Message | Rôle |
|---|---|---|---|
| **LAB-27** | automate → LIS | `QBP^Q11` → `RSP^K11` | l'automate demande ce qu'il doit faire d'un échantillon |
| **LAB-28** | LIS → automate | `OML^O33` → `ORL^O34` | le LIS diffuse une prescription aux automates |
| **LAB-29** | automate → LIS | `OUL^R22` → `ACK` | l'automate remonte des résultats ou un changement d'état |

Ces messages sont des messages HL7 v2.5.1, transportés en HTTP selon la spécification
*HL7 over HTTP* du projet HAPI. Concrètement : une requête `POST` dont le corps est le
message HL7 brut.

Le choix d'IHE-LAW plutôt qu'un format maison tient en une phrase : c'est une norme
publique, sans licence ni redevance, conçue pour remplacer les anciens standards de
connectivité de laboratoire (CLSI LIS1-A / LIS2, autrement dit ASTM E1381 et E1394).
Un LIS tiers qui sait traiter ces trois transactions peut se brancher sur Connect sans
qu'une ligne de code change. Rien dans Connect ne suppose que le LIS est LabBook.

> **Piège — les trois transactions ne sont pas symétriques.** LAB-27 et LAB-29 sont à
> l'initiative de l'automate ; elles entrent dans Connect par le plugin, qui appelle
> ensuite le LIS. LAB-28 est à l'initiative du LIS ; elle entre par le serveur HTTP de
> Connect. C'est pourquoi vous ne trouverez un point d'entrée HTTP `lab28` que d'un seul
> côté, et pourquoi les deux autres n'apparaissent nulle part dans les routes de Connect.

## Deux modes de fonctionnement

Le vocabulaire prête à confusion, car deux notions différentes s'appellent toutes deux
« mode ».

**Le mode opératoire** (`operation_mode`) décrit qui prend l'initiative du travail :

- **query** : l'automate interroge le LIS (« qu'as-tu pour cet échantillon ? »).
  C'est LAB-27, complété par LAB-28 qui porte la réponse.
- **batch** (ou *broadcast*) : le LIS pousse les prescriptions vers les automates
  sans qu'on les lui demande. C'est LAB-28 seule.

**Le mode de connexion** (`mode`) décrit qui ouvre la socket TCP :

- **server** : Connect écoute sur un port, l'automate vient s'y connecter.
- **client** : Connect va se connecter à l'adresse IP de l'automate.

Les deux notions sont indépendantes, et le constructeur impose souvent l'une comme
l'autre. En pratique, le mode *query* avec Connect en *server* est la combinaison la mieux
éprouvée : c'est l'automate qui établit la connexion et qui vient demander du travail.
Cela paraît contre-intuitif — on imagine volontiers le logiciel pilotant la machine — mais
c'est ce que font la plupart des automates.

## Pourquoi une architecture à plugins

Connect ne connaît aucun constructeur. Il ne sait pas ce qu'est un GeneXpert, un Roche ou
un Sysmex. Tout le savoir spécifique à un automate est déporté dans un **plugin** : une
archive `.jar` déposée dans un répertoire du volume de stockage, chargée dynamiquement au
démarrage ou sur demande.

La raison est opérationnelle. Un laboratoire qui n'a qu'un automate ne doit pas subir une
mise à jour parce qu'un autre modèle, qu'il ne possède pas, a changé de format de message.
Si tout le code de conversion vivait dans Connect, chaque correction sur un constructeur
imposerait de redistribuer une nouvelle image de conteneur à tout le monde.

Avec les plugins :

- le cœur de Connect évolue rarement, et sa mise à jour est un événement ;
- un plugin se met à jour en déposant un fichier et en demandant un rechargement, sans
  arrêter le conteneur ni perturber les autres automates ;
- ajouter un automate ne demande **aucune recompilation** du moteur.

Le prix à payer est un contrat strict : `src/main/java/plugin/Analyzer.java`, l'interface
que tout plugin doit implémenter. Elle est décrite au chapitre
[04](04-le-contrat-de-plugin.md).

## Ce que Connect fait au démarrage

1. Il crée, si elles n'existent pas, les répertoires dont il a besoin sous `/storage`.
2. Il parcourt le répertoire des plugins et charge chaque `.jar` trouvé.
3. Il parcourt le répertoire des réglages et, pour chaque fichier de configuration valide,
   crée une **instance** d'automate à partir du plugin correspondant.
4. Il démarre l'écoute de chaque instance.
5. Il démarre un serveur HTTP sur le port 8080.

Une conséquence importante : le plugin et l'instance sont deux choses distinctes. Un seul
`.jar` GeneXpert peut servir trois automates GeneXpert du même laboratoire, à condition de
fournir trois fichiers de réglage, chacun avec un identifiant et un port différents.

## Ce que Connect ne fait pas

- **Pas d'interface graphique.** Connect est un service. Tout se pilote par des appels
  HTTP ou depuis l'interface du LIS ; tout se diagnostique dans les logs.
- **Pas de liaison série.** Seules les connexions TCP/IP sont gérées. Les automates
  raccordés en RS-232 sortent du périmètre, et rien n'est prévu pour les y faire entrer.
- **Pas de base de données.** Connect ne conserve aucun état métier. Il archive les
  messages bruts sur disque, c'est tout ; la mémoire du laboratoire est dans le LIS.
- **Pas de correspondance de codes en dur.** Les correspondances entre codes du
  constructeur et codes du LIS vivent dans des fichiers de configuration, pas dans le code.

## Organisation du dépôt `labbook_connect`

| Chemin | Contenu |
|---|---|
| `src/main/java/labbook_connect/labbook_connect/` | le moteur : `App`, `AnalyzerLoader`, `MyResource`, `CORSFilter` |
| `src/main/java/plugin/` | les deux fichiers partagés avec les plugins : `Analyzer.java`, `Connect_util.java` |
| `src/main/resource/logback.xml` | format des traces |
| `lib/` | bibliothèques tierces embarquées, versions figées |
| `bin/labbook_connect.jar` | une version précompilée, livrée avec le dépôt |
| `doc/` | diagrammes des transactions, procédure de construction, documentation d'API générée |
| `Dockerfile`, `Makefile` | construction et lancement du conteneur |

Le code du moteur tient en un peu plus de mille lignes réparties sur cinq fichiers. C'est
volontairement peu : la complexité est dans les plugins.

## Licence

Le projet est publié sous **GPL v2**, comme LabBook. Toute redistribution — y compris
d'une version dérivée — impose la publication des sources modifiées. L'usage interne à une
organisation, lui, n'impose rien.

Cette contrainte vaut aussi pour les plugins que vous écrirez si vous les distribuez.
