# 05 — Configuration : réglage et correspondances

Chaque automate se configure avec **deux fichiers TOML**, déposés dans deux répertoires
distincts du volume de stockage :

| Fichier | Emplacement | Rôle |
|---|---|---|
| réglage (*setting*) | `/storage/resource/connect/analyzer/setting/` | connexion et routage : où est l'automate, où est le LIS |
| correspondances (*mapping*) | `/storage/resource/connect/analyzer/mapping/` | vocabulaire : quel code du constructeur vaut quel code du LIS |

Les dépôts de plugins livrent un exemplaire de chacun dans leur répertoire `doc/`. **Ce
sont des exemples, pas des valeurs par défaut fonctionnelles** : le fichier de réglage doit
impérativement être édité avant usage.

## Le fichier de réglage

```toml
version = "1.0.0"                             # version du fichier de configuration

[analyzer]
brand = "GENEXPERT"                           # marque, informatif
name = "GeneXpert"                            # modèle, informatif
id = "GX_01"                                  # identifiant unique de cet automate
plugin = "AnalyzerGeneXpert"                  # nom de la classe Java du plugin
url_lis = "http://localhost/sigl"             # racine du LIS
operation_mode = "query"                      # batch ou query
archive_msg = "Y"                             # Y ou N
type_cnx = "socket_E1381"                     # socket, MLLP ou socket_E1381
type_msg = "ASTM"                             # HL7 ou ASTM
mapping = "/storage/resource/connect/analyzer/mapping/mapping_genexpert"

[analyzer.socket]
mode = "server"                               # server ou client
ip = "IP_ADDRESS_TO_ENTER"                    # utilisé uniquement en mode client
port = 12345
```

### Les champs, un par un

| Champ | Obligatoire | Remarques |
|---|---|---|
| `version` | non | version **du fichier**, pas du plugin ; sert aux traces |
| `analyzer.brand`, `analyzer.name` | non | purement documentaires, non lus par le moteur |
| `analyzer.id` | **oui** | doit être unique sur toute l'installation ; c'est la clé de tout |
| `analyzer.plugin` | **oui** | doit correspondre exactement à ce que renvoie `test()` du plugin |
| `analyzer.url_lis` | oui en pratique | racine du LIS ; les points d'entrée LAB-27/LAB-29 sont ajoutés par le code |
| `analyzer.operation_mode` | non | `batch` ou `query` ; défaut `batch` |
| `analyzer.archive_msg` | oui en pratique | `Y` active l'archivage des messages bruts |
| `analyzer.type_cnx` | **oui** | `socket`, `MLLP` ou `socket_E1381` |
| `analyzer.type_msg` | **oui** | `HL7` ou `ASTM` |
| `analyzer.mapping` | non | chemin du fichier de correspondances, **sans l'extension** `.toml` |
| `analyzer.socket.mode` | **oui** | `server` ou `client` |
| `analyzer.socket.ip` | oui si `client` | ignoré en mode `server` |
| `analyzer.socket.port` | **oui** | doit être > 0 |

La validation, dans `AnalyzerLoader.parse_setting()`, exige : un identifiant non vide, un
nom de plugin non vide, un `type_cnx` et un `type_msg` non vides, et — en mode `server` —
un port strictement positif, ou — en mode `client` — une adresse IP non vide **et** un port
strictement positif.

Si un seul de ces éléments manque, le fichier est écarté avec un simple
`ERROR lack of settings` dans le log. Aucune instance n'est créée, aucune autre explication
n'est donnée.

> **Piège — `type_cnx` accepté par le moteur mais pas par le plugin.** `AnalyzerLoader`
> accepte `socket`, `MLLP` et `socket_E1381`, mais chaque plugin ne gère qu'un
> sous-ensemble : le plugin GeneXpert refuse tout ce qui n'est pas `socket_E1381` ou
> `socket`, le plugin Roche tout ce qui n'est pas `socket`. Le symptôme est particulier :
> l'instance est bien créée et apparaît dans `list_analyzers_loaded`, mais elle n'écoute
> rien. Cherchez dans le log la ligne `Unsupported connection type:`.

> **Piège — le port et la plage publiée par le conteneur.** Un port valide dans le fichier
> de réglage n'est pas forcément joignable de l'extérieur du conteneur. En développement,
> seule la plage 12300-12399 est publiée. Voir chapitre
> [02](02-conteneur-stockage-logs.md#les-ports).

### Plusieurs automates du même modèle

C'est le cas courant : un laboratoire possède trois automates identiques.

- **Un seul `.jar`** de plugin est déposé.
- **Un seul fichier de correspondances** suffit si les trois font les mêmes analyses.
- **Trois fichiers de réglage** sont nécessaires, avec chacun :
  - un `analyzer.id` différent (`GX_01`, `GX_02`, `GX_03`) ;
  - un `analyzer.socket.port` différent — deux instances ne peuvent pas écouter sur le
    même port ;
  - un **nom de fichier** différent (`analyzer_genexpert_GX_01.toml`, etc.).

Ce dernier point compte quand on dépose les fichiers par l'interface du LIS : téléverser
trois fois `analyzer_genexpert.toml` écrase à chaque fois le précédent, et il ne reste
qu'un automate configuré.

Le nom du fichier n'a par ailleurs aucune signification pour Connect : il lit **tous** les
fichiers du répertoire `setting` et ne se fie qu'à leur contenu.

Au démarrage, le log doit montrer trois créations d'instance et trois écoutes ouvertes.
Un fichier de correspondances par automate reste souvent plus commode à maintenir qu'un
fichier commun, même quand les analyses sont identiques.

## Le fichier de correspondances

Sa forme s'inspire du format LIVD, qui vise à normaliser la publication des
correspondances entre codes constructeurs et codes standards. La normalisation n'étant pas
aboutie, le projet en a repris le vocabulaire sans en adopter toute la complexité.

```toml
[livd_publication]
publisher = "..."
document_identifier = "cepheid_genexpert"
publication_version_id = "1.0"
publication_date = "..."

[equipment]
manufacturer = "Cepheid"
model = "GeneXpert 6.2"
```

Ces deux sections sont **documentaires** : rien ne les lit aujourd'hui. Elles anticipent le
jour où il faudra distinguer plusieurs correspondances pour un même constructeur.

### Les analyses : `[[ivd_test]]`

```toml
[[ivd_test]]
name = "carba_v2"              # nom interne, sert de clé de jointure
vendor_test_code = "carba_v2"  # code envoyé/attendu par l'automate
lis_test_code = "GX01"         # code de l'analyse dans le LIS
```

Un bloc par analyse. `name` est la clé qui relie une analyse à ses résultats dans la
section suivante ; `vendor_test_code` et `lis_test_code` sont les deux vocabulaires à
réconcilier.

La correspondance fonctionne **dans les deux sens** :

- résultats qui remontent (LAB-29) : le plugin cherche le `vendor_test_code` reçu et en
  déduit le `lis_test_code` ;
- prescriptions qui descendent (LAB-28) : il cherche le `lis_test_code` et en déduit le
  `vendor_test_code`.

> **Piège — la limite de longueur du code envoyé à l'automate.** Certains automates
> tronquent ou ignorent silencieusement un code d'analyse trop long. Le GeneXpert, par
> exemple, n'accepte pas plus de **15 caractères** dans le champ correspondant. Un code
> plus long est accepté par le LIS, transmis sans erreur, et les résultats reviennent sans
> pouvoir être rapprochés de la prescription. Aucun message d'erreur nulle part.

### Les résultats : `[[ivd_mapping]]`

```toml
[[ivd_mapping]]
test = "carba_v2"                                       # renvoie à ivd_test.name
vendor_result_code = "^carba_v2^^imp1^Xpert Carba-R^2^IMP1^"
lis_result_code = "821"                                 # code de la variable dans le LIS
vendor_unit = ""
lis_unit = ""
convert = "none"
factor = 0
```

Une analyse produit en général plusieurs résultats — plusieurs variables. Chacune demande
son bloc.

Le `vendor_result_code` est **exactement** la chaîne que l'automate place dans le champ
d'identification du résultat, séparateurs compris. On ne l'invente pas : on le relève dans
les messages bruts archivés lors des premiers échanges.

| Champ | Rôle |
|---|---|
| `test` | rattache le résultat à une analyse déclarée en `[[ivd_test]]` |
| `vendor_result_code` | identifiant du résultat côté automate |
| `lis_result_code` | identifiant de la variable côté LIS |
| `vendor_unit` | unité renvoyée par l'automate (documentaire) |
| `lis_unit` | unité à écrire dans le message vers le LIS ; si renseignée, **remplace** celle de l'automate |
| `convert` | opération à appliquer à la valeur |
| `factor` | opérande de l'opération |

Les valeurs reconnues de `convert` sont :

| `convert` | Effet |
|---|---|
| `none` | aucune transformation (défaut) |
| `multiply` | `valeur × factor` |
| `divide` | `valeur ÷ factor` (sans effet si `factor` vaut 0) |
| `add` | `valeur + factor` |
| `subtract` | `valeur − factor` |
| `log10` | logarithme décimal (sans effet si la valeur est ≤ 0) |

La conversion n'est tentée que si la valeur est numérique ; sinon elle est laissée telle
quelle et une ligne d'information est tracée.

`lis_unit` est utile même sans conversion, pour une simple question d'écriture : si
l'automate renvoie `IU/mL` et que le LIS attend `UI/ml`, il suffit de renseigner le champ.
Laissé vide, le plugin transmet l'unité brute de l'automate.

> **Piège — le champ `test` vide.** Certains automates ne réalisent qu'une seule analyse
> et ne renvoient donc aucun nom d'analyse dans leurs messages. Dans ce cas il n'y a rien à
> faire correspondre du côté `[[ivd_test]]`, et le champ `test` des blocs
> `[[ivd_mapping]]` peut rester vide. Dès qu'un automate fait plusieurs analyses, en
> revanche, le champ devient indispensable : sans lui, impossible de savoir quelle
> variable appartient à quelle analyse.

### Où trouver le contenu

Ce n'est ni dans la documentation du constructeur, ni dans le code. La documentation
technique décrit le protocole et le format des messages ; elle ne dit presque jamais quelles
analyses tel exemplaire d'automate sait réaliser.

La méthode qui marche est empirique :

1. brancher l'automate et laisser les premiers échanges se produire ;
2. relire les messages bruts archivés pour repérer, à l'aide de la documentation, les champs
   qui portent le nom de l'analyse et les identifiants de résultats ;
3. faire passer, une fois, chaque analyse que l'automate sait réaliser — une dizaine en
   général — pour collecter la structure exacte de chacune ;
4. créer côté LIS les analyses et les variables correspondantes, avec leurs codes ;
5. écrire les correspondances.

Cette étape se fait à deux : quelqu'un qui connaît le référentiel d'analyses du laboratoire,
et quelqu'un qui sait lire les messages bruts.

## Quand le mapping échoue

C'est la panne la plus fréquente et la plus déroutante, parce qu'elle **ne ressemble pas à
une panne** :

- l'échange réseau se déroule normalement ;
- les logs de Connect ne montrent aucune erreur ;
- le LIS reçoit bien un message et le trace dans son écran des transactions ;
- mais les valeurs n'apparaissent pas dans les cases de résultats.

Ce qu'il s'est passé : le plugin n'a trouvé aucune correspondance, il a transmis les codes
bruts du constructeur, et le LIS ne les reconnaît pas dans son référentiel.

Le diagnostic est décrit au chapitre
[08](08-cote-lis.md#quand-les-résultats-narrivent-pas-dans-les-cases) : le LIS conserve la
donnée reçue et permet de la consulter, ce qui donne le code exact attendu.

## Corriger sans recompiler

C'est tout l'intérêt d'avoir sorti les correspondances du code Java. Changer un code,
ajouter une analyse, corriger une unité : on modifie le fichier TOML, on le redépose, et on
demande un rechargement — bouton « recharger les plugins » du LIS, ou appel direct :

```bash
wget -qO- http://localhost:8080/connect/load_analyzers
```

Aucune recompilation, aucun redémarrage de conteneur, et les autres automates ne sont pas
perturbés.

Le fichier de correspondances est relu à chaque rechargement, puisque `listenDevice()`
appelle `loadMappingToml()`.
