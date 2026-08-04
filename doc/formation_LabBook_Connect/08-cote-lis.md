# 08 — Le côté LIS

Connect ne sert à rien seul. Ce chapitre décrit ce que le système de gestion de laboratoire
doit fournir en face, et comment on configure un automate depuis son interface.

> **Portée de ce chapitre.** Les extraits de configuration et les chemins de service sont
> ceux que Connect construit et appelle : ils sont vérifiables dans le code du moteur. En
> revanche, les noms de fichiers et l'agencement des écrans décrits ici relèvent du dépôt du
> LIS, qui n'est pas celui de Connect. Considérez-les comme un repérage, à confirmer sur la
> version de LIS que vous avez sous la main.

## Le contrat minimal

Un LIS compatible doit fournir **deux points d'entrée HTTP** et savoir **appeler un
troisième** service côté Connect.

### Ce que le LIS doit exposer

Connect construit les URL à partir du champ `analyzer.url_lis` du fichier de réglage
(voir `AnalyzerLoader.java:30`) :

```text
POST {url_lis}/services/external/device/analyzer/lab27/{id_analyzer}
POST {url_lis}/services/external/device/analyzer/lab29/{id_analyzer}
```

| | LAB-27 | LAB-29 |
|---|---|---|
| Reçoit | `QBP^Q11` | `OUL^R22` |
| Doit répondre | `RSP^K11` | un accusé de réception HL7 |
| `Content-Type` | `application/hl7-v2` | `application/hl7-v2` |

L'identifiant de l'automate est passé **dans l'URL**, pas dans le message : c'est ainsi que
le LIS sait de quel appareil provient la demande.

> **Le point qui surprend le plus.** Un `200 OK` ne suffit pas. Ces transactions sont des
> dialogues : le LIS doit renvoyer un **message HL7 complet** dans le corps de la réponse.
> Un plugin qui reçoit un corps vide ou du texte non HL7 le traite comme un échec — voyez
> le contrôle `startsWith("MSH|")` du chapitre [06](06-plugin-astm.md#lab-29--lautomate-remonte-des-résultats).

Le segment `/external` du chemin admet une tolérance : sur un code 404, `send_hl7_msg`
retente une fois sans lui (voir chapitre
[04](04-le-contrat-de-plugin.md#send_hl7_msg--parler-au-lis)). Un LIS plus ancien exposant
`/services/device/analyzer/lab27/...` fonctionne donc, au prix d'un aller-retour perdu à
chaque message.

### Ce que le LIS doit appeler

Deux services, sur le port 8080 de Connect :

```text
POST http://{hôte_connect}:8080/connect/lab28/{id_analyzer}    diffuser une prescription
GET  http://{hôte_connect}:8080/connect/load_analyzers          recharger la configuration
```

Il n'existe pas de point d'entrée `lab27` ni `lab29` côté Connect, et c'est normal : ces
deux transactions sont à l'initiative de l'automate et entrent par le plugin.

Le LIS s'appuie aussi sur `GET /connect/test` (l'URL répond-elle ?) et
`GET /connect/is_analyzer_loaded/{id}` (cet automate est-il chargé ?) pour les boutons de
test de son interface.

### Un LIS tiers

Rien dans Connect ne dépend de LabBook. Un autre LIS qui expose ces deux points d'entrée et
sait produire un `RSP^K11` conforme fonctionnera. C'est une intention de conception, pas
seulement une possibilité théorique — même si, en pratique, LabBook reste le seul LIS avec
lequel l'ensemble a été éprouvé.

Le transport en HL7 v2 n'est pas figé non plus : une évolution vers FHIR est envisagée, en
complément et non en remplacement.

## L'implémentation côté LabBook

Deux fichiers portent l'essentiel :

| Fichier | Rôle |
|---|---|
| `DeviceRest.py` (services REST) | les ressources exposées, dont les deux transactions |
| `Analyzer.py` (modèle) | la construction et la lecture des messages HL7 |

Les routes sont déclarées comme toutes les autres, dans le fichier d'initialisation de
l'application back end. Vous n'y trouverez que **deux** routes d'automate, pour la raison
déjà dite.

Le nommage mérite un mot : la ressource s'appelle `Device` et non `Analyzer`, en prévision
d'autres types d'appareils à raccorder un jour ; le modèle, lui, est bien `Analyzer`.

Côté Python, la construction des messages passe par une bibliothèque HL7 dédiée, comme HAPI
côté Java. Les mêmes réserves s'appliquent : quand la bibliothèque ne connaît pas une
structure, on retombe sur de l'assemblage manuel, plus verbeux et plus fragile.

## Configurer un automate depuis l'interface

Le parcours se fait dans **Intégration → Configuration des analyseurs**, sous un profil
d'administration.

### 1. Déclarer l'URL de Connect

Sous l'onglet **Connect**, renseignez l'URL du service. La valeur par défaut,
`http://localhost:8080`, ne convient que si le LIS et Connect tournent sur la même machine
et dans le même espace réseau — ce qui n'est pas le cas avec deux conteneurs distincts.
Mettez l'adresse de la machine, en conservant le port 8080.

Le bouton **Tester** appelle `GET /connect/test`. Une confirmation en vert signifie que le
LIS voit Connect. Enregistrez ensuite.

### 2. Déposer les trois fichiers

Toujours sous l'onglet Connect, trois zones d'import déposent les fichiers dans le volume
partagé :

| Fichier | Destination |
|---|---|
| `AnalyzerXxx.jar` | `/storage/resource/connect/analyzer/plugin/` |
| `analyzer_xxx.toml` | `/storage/resource/connect/analyzer/setting/` |
| `mapping_xxx.toml` | `/storage/resource/connect/analyzer/mapping/` |

Le fichier de réglage doit avoir été **édité avant** : version, `url_lis` pointant sur le
LIS, identifiant, adresse et port. Voir chapitre
[05](05-configuration-setting-et-mapping.md#le-fichier-de-réglage).

Ces trois fichiers peuvent tout aussi bien être copiés à la main sur le serveur. L'interface
n'est qu'une commodité.

> **Rappel — un fichier par automate, un nom par fichier.** Pour trois automates, déposez
> une fois le `.jar`, une fois le mapping s'il est commun, et **trois** fichiers de réglage
> aux noms distincts. Trois envois du même nom de fichier n'en laissent qu'un.

### 3. Recharger

Le bouton **recharger les plugins** appelle `GET /connect/load_analyzers`. Ouvrez la trace
de Connect **avant** de cliquer :

```bash
podman exec -it labbook_connect tail -f /app/logs/labbook_connect.log
```

Vous devez voir défiler la découverte du `.jar`, la lecture du fichier de réglage, la
création ou la mise à jour de l'instance, puis l'ouverture de l'écoute :

```text
Found plugin JAR: AnalyzerGeneXpert.jar
Processing settings file: .../analyzer_genexpert.toml
DEBUG version=1.0.14
DEBUG Creating new analyzer AnalyzerGeneXpert
ASTM Server started on port 12345
```

Si l'instance existait déjà, le message est `Updating existing analyzer` — l'écoute est
alors arrêtée puis relancée.

### 4. Déclarer l'analyseur dans le LIS

Le dépôt des fichiers configure **Connect**. Il reste à déclarer l'appareil dans le
référentiel du LIS : un nom d'affichage libre, l'analyseur choisi dans la liste — alimentée
par les fichiers de réglage présents —, le mode opératoire, une position d'affichage, et
surtout **l'identifiant**.

> **Piège — l'identifiant doit être rigoureusement identique.** Celui saisi dans le LIS et
> celui du champ `analyzer.id` du fichier de réglage doivent correspondre au caractère
> près, souligné et casse compris. C'est cette valeur qui voyage dans l'URL des transactions
> et qui permet à chacun de retrouver l'autre. Un écart, et les messages partent vers un
> identifiant que personne ne reconnaît.

Le bouton **Tester** appelle `is_analyzer_loaded` : il confirme que l'identifiant saisi
correspond bien à une instance active dans Connect. Enregistrez.

## Suivre les échanges

L'écran **Transactions** liste les messages échangés, sans avoir à fouiller les logs :

| Colonne | Contenu |
|---|---|
| analyseur | quel appareil |
| date | horodatage |
| sens | analyseur → LIS, ou LIS → analyseur |
| message reçu | le message HL7 tel qu'il est arrivé |
| message renvoyé | la réponse produite |
| état | accepté, ou en rouge en cas d'échec |

C'est l'outil de diagnostic destiné à l'administrateur. La colonne qui compte est **le
message reçu** : c'est là qu'on lit les codes réellement envoyés par l'automate, donc ceux
qu'il faut faire figurer dans le fichier de correspondances.

## Le trajet d'un résultat

Une fois la chaîne en place, voici ce qui se passe pour un échantillon.

1. Un dossier est créé dans le LIS avec un **code échantillon**. C'est ce code qui fait le
   lien : il doit être celui que l'automate renverra. Le LIS peut imposer un format de code
   (une expression régulière) et le signale à la saisie.
2. L'automate réalise l'analyse et remonte ses résultats (LAB-29).
3. Connect les convertit et les transmet au LIS.
4. Dans l'écran de saisie des résultats, les valeurs apparaissent **préremplies**.
5. Le technicien **enregistre**, puis **valide** techniquement.
6. Le biologiste procède à la validation biologique.
7. Le compte rendu est généré.

> **Rien n'est validé automatiquement.** Le préremplissage des cases n'est ni un
> enregistrement ni une validation. La chaîne de validation humaine reste entière — c'est
> voulu : la responsabilité du résultat rendu n'appartient pas à l'automate.

## Quand les résultats n'arrivent pas dans les cases

C'est la panne la plus fréquente, et elle a une signature reconnaissable : **l'échange
fonctionne, mais les valeurs ne s'affichent pas**.

Deux situations à distinguer.

**Cas 1 — l'échantillon n'est pas connu.** Le log de Connect montre l'aller-retour complet,
et la réponse du LIS indique que l'identifiant d'échantillon est introuvable. C'est le
comportement normal quand aucun dossier ne porte ce code — c'est d'ailleurs ce qu'on obtient
lors d'un premier essai avec un simulateur, avant d'avoir créé quoi que ce soit.

**Cas 2 — l'échantillon est connu, mais le mapping a échoué.** Le message est bien reçu et
apparaît dans l'écran des transactions. Dans l'écran de saisie, une petite icône
d'information signale qu'une donnée est arrivée : elle ouvre une fenêtre listant, ligne par
ligne, l'analyse, le résultat, la valeur, l'unité et **le code de la variable tel qu'il a
été reçu**.

C'est exactement l'information qu'il faut : comparez ce code à celui de votre référentiel,
corrigez le fichier de correspondances, redéposez-le, rechargez. En attendant, les valeurs
restent lisibles dans cette fenêtre et peuvent être ressaisies à la main — rien n'est perdu.

## Résultats annulés

Un automate peut signaler qu'un résultat déjà transmis est annulé, au moyen d'un indicateur
dans la ligne de résultat.

Connect transmet l'information ; **c'est au LIS de décider quoi en faire**, et la décision
dépend de l'avancement du dossier. Si la validation biologique est déjà passée et que le
compte rendu a été édité, voire remis, on n'efface pas un résultat rétroactivement : le
message est archivé et tracé, mais le dossier n'est pas modifié en silence.

Cette logique est propre à chaque LIS. Elle n'a pas sa place dans un plugin.
