# 04 — Le contrat de plugin

Deux fichiers, dans `labbook_connect/src/main/java/plugin/`, définissent tout ce qui passe
entre le moteur et un plugin :

- **`Analyzer.java`** — l'interface que le plugin doit implémenter ; ce que Connect
  attend de lui.
- **`Connect_util.java`** — les fonctions que Connect met à disposition ; ce que le
  plugin peut appeler.

Ces deux fichiers sont compilés dans le moteur **et** réembarqués dans chaque `.jar` de
plugin (voir chapitre [01](01-environnement-et-construction.md#construire-un-plugin)).
Toute évolution de l'interface impose donc de recompiler les plugins.

## L'interface `Analyzer`

### Identité et configuration

Ces méthodes sont appelées par `AnalyzerLoader` juste après la création de l'instance,
pour lui transmettre son fichier de réglage. Un plugin se contente en général de les
implémenter comme de simples accesseurs.

| Méthode | Ce que Connect y met |
|---|---|
| `setId_analyzer` / `getId_analyzer` | l'identifiant unique de l'automate (`analyzer.id`) |
| `setVersion` | la version du fichier de réglage |
| `setUrl_upstream_lab27` / `getUrl_upstream_lab27` | URL LIS complète pour LAB-27, identifiant compris |
| `setUrl_upstream_lab29` / `getUrl_upstream_lab29` | idem pour LAB-29 |
| `setType_cnx` | `socket`, `MLLP` ou `socket_E1381` |
| `setType_msg` | `HL7` ou `ASTM` |
| `setArchive_msg` | `Y` ou `N` |
| `setOperationMode` | `batch` ou `query` |
| `setMode` | `client` ou `server` |
| `setIp_analyzer` / `setPort_analyzer` | adresse et port de l'automate |
| `setMappingPath` / `getMappingPath` | chemin du fichier de correspondance |

> **Piège — `setId_analyzer` et `copy()`.** Regardez bien l'ordre des appels dans
> `AnalyzerLoader.parse_setting()` : pour une **création**, `copy()` est appelée d'abord,
> puis `setId_analyzer()`. Si votre `copy()` recopie l'identifiant du prototype — qui est
> vide — ce n'est pas grave, il sera écrasé juste après. Mais si votre `copy()` **oublie**
> de recopier un champ, ce champ restera à sa valeur par défaut sur l'instance créée, et
> seule une relecture attentive des traces le révélera.

### Identification du plugin

```java
String test();   // le nom du plugin, à faire correspondre avec analyzer.plugin
String info();   // un résumé lisible de la configuration et de l'état
Analyzer copy(); // une nouvelle instance, configurée comme celle-ci
```

`test()` porte un nom malheureux : ce n'est pas un test, c'est **l'identité du plugin**.
C'est la valeur que `AnalyzerLoader` compare au champ `analyzer.plugin` du fichier de
réglage pour savoir quel `.jar` doit servir. L'implémentation habituelle est la plus sûre :

```java
@Override
public String test() {
    return this.getClass().getSimpleName();
}
```

`copy()` est le mécanisme qui permet à un seul `.jar` de servir plusieurs automates. Le
plugin chargé depuis le fichier sert de prototype ; chaque fichier de réglage en produit
une copie, qu'on configure ensuite indépendamment.

`info()` est renvoyée telle quelle par le service web `info/{id_analyzer}`. Composez-la
avec tout ce qui aide au diagnostic : version du `.jar`, version du réglage, identifiant,
URL, type de connexion, mode, adresse, port, chemin de mapping.

### Les trois transactions

```java
String lab27(final String msg);          // requête de l'automate  -> QBP^Q11 -> RSP^K11
String lab28(final String str_OML_O33);  // prescription du LIS    -> ORL^O34
String lab29(final String msg);          // résultats de l'automate -> OUL^R22 -> ACK
```

**`lab27` et `lab29` sont appelées par le plugin lui-même**, depuis sa boucle d'écoute,
quand un message arrive de l'automate. Connect ne les appelle jamais : il n'expose aucune
route HTTP pour elles.

**`lab28` est appelée par Connect**, depuis `MyResource.lab28()`, quand le LIS pousse une
prescription.

Le paramètre et le retour sont toujours des chaînes de caractères, et leur contenu dépend
du protocole de l'automate. Pour un plugin ASTM, `lab27` reçoit de l'ASTM et renvoie de
l'ASTM ; pour un plugin HL7 natif, elle reçoit et renvoie du HL7. C'est le plugin qui
décide, la seule contrainte étant ce qui circule **vers le LIS** : toujours du HL7 v2.5.1
conforme à IHE-LAW.

### Le cycle de vie

```java
void listenDevice();     // passer à l'état actif : ouvrir les sockets, lancer les threads
boolean isListening();   // l'instance est-elle active ?
void stopListening();    // tout libérer proprement
```

`listenDevice()` n'est **jamais** appelée par le développeur du plugin : c'est
`AnalyzerLoader` qui la déclenche, une fois toute la configuration transmise. Quand elle
démarre, l'instance connaît déjà son identifiant, son protocole, son port et son fichier de
mapping.

`stopListening()` doit **réellement fermer les sockets**. C'est le point le plus souvent
bâclé, et sa conséquence est spectaculaire : si la socket serveur reste ouverte, le port
demeure occupé et l'instance ne peut plus redémarrer sans redémarrer tout le conteneur.
Or `AnalyzerLoader` appelle `stopListening()` puis `listenDevice()` à **chaque**
rechargement de configuration.

## Les utilitaires `Connect_util`

Classe purement statique, non instanciable. Quatre services.

### `send_hl7_msg` — parler au LIS

```java
public static String send_hl7_msg(Analyzer analyzer, String url_upstream, String hl7_msg)
```

C'est **la seule** façon dont un plugin doit envoyer un message au LIS. N'écrivez pas votre
propre client HTTP : cette fonction porte des comportements qu'il faudrait reproduire.

Elle envoie un `POST` avec `Content-Type: application/hl7-v2`, un délai de connexion de
10 s et un délai de lecture de 15 s, puis renvoie le corps de la réponse.

En cas d'échec, elle renvoie une chaîne commençant par `ERROR` — **elle ne lève pas
d'exception**. Votre plugin doit tester le retour :

```java
String hl7Ack = Connect_util.send_hl7_msg(this, this.url_upstream_lab29, hl7Message);

if (hl7Ack == null || !hl7Ack.startsWith("MSH|")) {
    logger.error("Lab29 : upstream returned non-HL7 or null");
    return "L|1|N";   // acquittement négatif vers l'automate
}
```

> **Piège — la nouvelle tentative sans `/external`.** Sur un code HTTP 404, si l'URL
> contient `/services/external/`, la fonction retente **une fois** avec
> `/services/` à la place. C'est une compatibilité avec des versions de LIS qui n'ont pas
> le préfixe `external`. Conséquence à connaître au diagnostic : un 404 génère deux
> lignes de log et deux appels réseau. Ce n'est pas un bug, et ce n'est pas une boucle.

### `readMLLPMessage` et `encapsulateHL7Message` — le transport HL7

```java
public static final int START_MSG_MLLP  = 0x0B;
public static final int END_MSG_MLLP     = 0x1C;
public static final int CARRIAGE_RETURN  = 0x0D;
```

MLLP (*Minimal Lower Layer Protocol*) est la façon standard de faire passer du HL7 v2 sur
une socket TCP : le message est encadré par un octet de début et un octet de fin suivi d'un
retour chariot. Sans cet encadrement, le destinataire ne saurait pas où finit un message et
où commence le suivant.

`encapsulateHL7Message()` pose l'encadrement, `readMLLPMessage()` le retire en lisant le
flux d'entrée. Un plugin HL7 n'a donc **aucune couche transport à écrire** — c'est toute la
différence avec un plugin ASTM (chapitre [06](06-plugin-astm.md)).

### `archiveMessage` — la piste d'audit

```java
public static void archiveMessage(String id_analyzer, String archive_msg,
                                  String message, String labType, String srcType)
```

Écrit le message brut dans le répertoire d'archive de l'instance, sous un nom de la forme
`LAB-29_Analyzer_AAAAMMJJ_HHMMSS.txt`. `labType` vaut `LAB-27`, `LAB-28` ou `LAB-29` ;
`srcType` vaut `LIS` ou `Analyzer`.

L'appel est sans effet si `archive_msg` ne vaut pas `Y`.

**Archivez toujours le message brut, avant toute transformation.** C'est ce qui permet, en
cas d'incident, de distinguer un automate qui envoie quelque chose d'inattendu d'un plugin
qui le traite mal. C'est aussi une exigence réglementaire dans certains contextes : le
message brut reçu d'un automate doit pouvoir être conservé plusieurs années. Ce sont de
petits fichiers texte, le coût en espace disque est négligeable.

### `loadMappingToml` — charger les correspondances

```java
public static Toml loadMappingToml(String mappingPath)
```

Renvoie un objet `Toml` **toujours utilisable** : si le chemin est vide, si le fichier
n'existe pas ou s'il est illisible, la fonction renvoie un TOML vide et trace la raison.
Elle ajoute au besoin l'extension `.toml` si le chemin ne la porte pas.

Appelez-la **une fois**, dans `listenDevice()`, et conservez le résultat dans un champ :

```java
this.mappingToml = Connect_util.loadMappingToml(this.getMappingPath());
```

Le mapping est ainsi en mémoire pour toute la vie de l'instance, et un rechargement de
configuration le relit.

> **Piège — un mapping absent ne fait rien planter.** Le plugin fonctionne, l'échange va
> jusqu'au LIS, et rien n'a l'air anormal dans les logs de Connect. Simplement, aucune
> correspondance n'est trouvée : les codes du constructeur remontent tels quels et le LIS
> ne les reconnaît pas. Le symptôme apparaît beaucoup plus loin, dans l'écran de saisie
> des résultats. Voir chapitre
> [05](05-configuration-setting-et-mapping.md#quand-le-mapping-échoue).

## Par où commencer un nouveau plugin

Le plus simple est de partir du plugin le plus proche de votre cas :

- l'automate parle **HL7 sur MLLP** et suit IHE-LAW → partez du plugin Roche, vous n'aurez
  quasiment que du mapping à écrire ;
- l'automate parle **ASTM** ou un dialecte maison → partez du plugin GeneXpert, qui
  contient la couche de transport complète.

Il existe aussi un dépôt `labbook_connect_plugin_demo` présenté comme squelette de
référence. Attention : il a été écrit pour une version antérieure de l'interface
(ses méthodes `lab27()` et `lab29()` sont sans paramètre) et ne compile pas contre
`Analyzer.java` tel qu'il est aujourd'hui. Il reste lisible pour comprendre l'intention
générale, mais ne le prenez pas comme point de départ.

Dans tous les cas, la structure attendue d'un plugin est :

```text
src/plugin/AnalyzerXxx.java   un seul fichier source, une seule classe
lib/                          les bibliothèques embarquées
resources/logback.xml
doc/analyzer_xxx.toml         un fichier de réglage d'exemple
doc/mapping_xxx.toml          un fichier de correspondance d'exemple
doc/build_command_line.md
script/                       les simulateurs (chapitre 09)
MANIFEST.MF, README.md, CHANGELOG.md, LICENSE.md
```

Un plugin tient dans un seul fichier source. Celui de Roche fait un millier de lignes,
celui de GeneXpert un peu moins de deux mille. C'est assumé : le découpage se fait par
méthodes, pas par fichiers.
