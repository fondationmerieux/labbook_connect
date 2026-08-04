# 03 — Le cœur de Connect

Le moteur tient en quatre classes, dans
`src/main/java/labbook_connect/labbook_connect/`. Aucune ne contient de logique métier :
leur rôle est d'orchestrer le démarrage, de charger les plugins et d'exposer une API.

| Fichier | Rôle |
|---|---|
| `App.java` | point d'entrée, initialisation, démarrage du serveur HTTP |
| `AnalyzerLoader.java` | chargement dynamique des plugins et création des instances |
| `MyResource.java` | les services web |
| `CORSFilter.java` | en-têtes CORS sur toutes les réponses |

## `App` : l'orchestrateur du démarrage

`App.main()` (`App.java:47`) fait cinq choses, dans cet ordre.

**1. Il trace sa version.** La première ligne du log donne la date et la version de
l'application. C'est la vérification la plus rapide qu'on soit bien sur la version qu'on
croit.

```java
public static final String VERSION  = "1.0.14";
public static final int NUM_VERSION = 10014;
```

`VERSION` est aussi ce que lit le `Makefile` et ce que renvoie le service `test`. La valeur
citée ici est celle de la version décrite par ce manuel ; les numéros de ligne de ce
chapitre s'y rapportent (voir [Versions décrites](README.md#versions-décrites)).

**2. Il crée son arborescence** (`createRequiredDirectories()`, `App.java:103`). Les
répertoires `plugin`, `setting` et `mapping` sous `/storage/resource/connect/analyzer` sont
créés s'ils manquent. Connect est donc autonome à la première installation : rien à
préparer à la main.

**3. Il charge les plugins**, en déléguant à `AnalyzerLoader` (section suivante).

**4. Il configure le serveur HTTP.** Connect embarque **Jetty** : le serveur n'est pas un
processus externe comme Apache ou nginx, c'est une bibliothèque instanciée dans
l'application. Il écoute sur `0.0.0.0:8080`, donc sur toutes les interfaces réseau du
conteneur.

Par-dessus Jetty, **Jersey** fournit la couche REST. La classe `MyResource` lui est
déclarée : toutes les requêtes reçues seront routées vers ses méthodes.

```java
final ResourceConfig config = new ResourceConfig(MyResource.class);
config.register(CORSFilter.class);
```

**5. Il démarre et bloque.**

```java
server.start();
server.join();
```

`join()` est indispensable : sans lui, `main()` se terminerait immédiatement après le
démarrage réussi, et le processus s'arrêterait. Le bloc `finally` arrête et détruit le
serveur si une exception survient.

Deux listes statiques portent l'état global de l'application :

```java
public static List<Analyzer> analyzers_classes = new ArrayList<Analyzer>();  // les plugins chargés
public static List<Analyzer> analyzers_loaded  = new ArrayList<Analyzer>();  // les instances configurées
```

La distinction est structurante : `analyzers_classes` contient **un élément par `.jar`**,
`analyzers_loaded` **un élément par automate configuré**. Trois automates du même modèle
donnent une entrée dans la première liste et trois dans la seconde.

## `AnalyzerLoader` : le chargement dynamique

C'est la classe la plus délicate du moteur. Son objectif : rendre Connect capable
d'instancier une classe qu'il ne connaît pas, dont il ignore jusqu'au nom à la compilation.

### Étape 1 — trouver et charger les classes

`loadAnalyzerClasses()` (`AnalyzerLoader.java:46`) parcourt
`/storage/resource/connect/analyzer/plugin` et appelle `loadPlugin()` sur chaque fichier
qui ne commence pas par un point.

`loadPlugin()` (`AnalyzerLoader.java:72`) fait le travail :

```java
String pluginClassName = plugin_name.toString().replace(".jar", "");
String className = "plugin." + pluginClassName;

Class<?> loadedClass = pluginClassLoader.loadClass(className);
Object instance = loadedClass.getDeclaredConstructor().newInstance();

if (Analyzer.class.isAssignableFrom(loadedClass)) {
    analyzers.add((Analyzer) instance);
}
```

Trois règles en découlent, et ce sont les trois causes de plugin muet :

1. **Le nom du JAR détermine le nom de la classe.** `AnalyzerGeneXpert.jar` cherche
   `plugin.AnalyzerGeneXpert`. Il n'existe pas de fichier descripteur permettant de
   dissocier les deux.
2. **La casse compte.** `analyzergenexpert.jar` ne chargera rien.
3. **Le paquetage est imposé.** La classe doit être dans le paquetage `plugin`.

La vérification `isAssignableFrom` garantit ensuite que la classe implémente bien
`Analyzer` : sans elle, on instancierait n'importe quoi et l'erreur n'apparaîtrait qu'au
premier appel de transaction.

> **Piège — le chargement qui échoue en silence.** Il existe des façons d'écrire ce
> chargement qui compilent, s'exécutent sans lever d'exception, et ne chargent rien : la
> liste reste vide et aucun message d'erreur n'apparaît. Si vous touchez à cette partie,
> tracez systématiquement le contenu de la liste après le chargement. La formulation
> actuelle fonctionne ; elle est plus fragile qu'elle n'en a l'air.

### Étape 2 — lire les réglages et créer les instances

`loadAnalyzers()` (`AnalyzerLoader.java:120`) enchaîne le chargement des classes, puis
parcourt `/storage/resource/connect/analyzer/setting` et passe chaque fichier à
`parse_setting()`.

`parse_setting()` (`AnalyzerLoader.java:173`) lit le TOML, contrôle les champs
obligatoires, puis cherche dans `analyzers_classes` le plugin dont `test()` renvoie la
valeur du champ `analyzer.plugin`. Deux cas :

**L'identifiant existe déjà dans `analyzers_loaded`** — c'est une mise à jour. L'instance
est arrêtée si elle écoutait, sa configuration est remplacée, puis l'écoute est relancée :

```java
if (existingAnalyzer.isListening()) {
    existingAnalyzer.stopListening();
}
// ... setVersion, setUrl_upstream_lab27, setType_cnx, setPort_analyzer, etc.
if (!existingAnalyzer.isListening()) {
    existingAnalyzer.listenDevice();
}
```

**L'identifiant est nouveau** — l'instance est créée par `analyzer.copy()`, configurée,
ses répertoires de travail sont créés, elle est ajoutée à `analyzers_loaded`, puis
`listenDevice()` démarre son écoute.

C'est ici que se construisent les deux URL du LIS, à partir du champ `url_lis` :

```java
private static final String END_POINT_LAB27 = "/services/external/device/analyzer/lab27";
private static final String END_POINT_LAB29 = "/services/external/device/analyzer/lab29";
...
newAnalyzer.setUrl_upstream_lab27(url_lis + END_POINT_LAB27 + "/" + id_analyzer);
newAnalyzer.setUrl_upstream_lab29(url_lis + END_POINT_LAB29 + "/" + id_analyzer);
```

> **Piège — les URL amont ne se configurent pas.** Certains fichiers `README` de plugins
> mentionnent des clés `url_upstream_lab27` et `url_upstream_lab29` dans le fichier de
> réglage. **Elles ne sont pas lues.** Seul `analyzer.url_lis` l'est ; les deux points
> d'entrée et le suffixe d'identifiant sont ajoutés par le code ci-dessus. Si vous devez
> viser un chemin différent, c'est le code du moteur qu'il faut changer, pas la
> configuration.

### Étape 3 — les répertoires de l'instance

`createAnalyzerDirectories()` (`AnalyzerLoader.java:302`) crée les six sous-répertoires
décrits au chapitre [02](02-conteneur-stockage-logs.md#larborescence), avec des
permissions POSIX ouvertes en lecture, écriture et exécution pour tous. Chaque automate
dispose ainsi d'un espace de travail entièrement séparé de ses voisins.

## `MyResource` : les services web

Toutes les routes sont préfixées par `/connect` (`@Path("/connect")`).

| Méthode | Chemin | Retour | Usage |
|---|---|---|---|
| `GET` | `test` | version de Connect | vérifier que le service répond |
| `GET` | `info/{id_analyzer}` | résumé lisible de l'instance | diagnostic |
| `GET` | `is_analyzer_loaded/{id_analyzer}` | JSON `status` / `message` | utilisé par l'interface du LIS |
| `GET` | `load_analyzers` | nombre d'instances chargées | **rechargement à chaud** |
| `GET` | `list_analyzers_classes` | un plugin par ligne | quels `.jar` sont chargés |
| `GET` | `list_analyzers_loaded` | une instance par ligne | quels automates sont configurés |
| `POST` | `lab28/{id_analyzer}` | message `ORL^O34` | transaction LAB-28 depuis le LIS |
| `POST` | `test_lab27` | écho | mise au point sans LIS |
| `POST` | `test_lab29` | écho | mise au point sans LIS |

`load_analyzers` est le service qui permet d'ajouter ou de modifier un automate **sans
arrêter le conteneur** : il relance tout le cycle de chargement. C'est le bouton
« recharger les plugins » de l'interface du LIS.

Une vérification rapide depuis le poste de développement, sans `curl` :

```bash
wget -qO- http://localhost:8080/connect/test
```

### La lecture du corps de la requête LAB-28

Un point technique mérite l'attention (`MyResource.java:163`) : le corps de la requête est
lu **octet par octet** dans un tampon, jamais ligne par ligne.

```java
ByteArrayOutputStream buffer = new ByteArrayOutputStream();
byte[] tmp = new byte[4096];
int read;
while ((read = bodyStream.read(tmp)) != -1) {
    buffer.write(tmp, 0, read);
}
oml_o33 = buffer.toString(StandardCharsets.UTF_8);  // conserve les \r
```

Un message HL7 v2 sépare ses segments par un **retour chariot seul** (`\r`). Toute lecture
« intelligente » qui normaliserait les fins de ligne détruirait le message. C'est une
erreur classique et son symptôme est déroutant : le message paraît correct dans les logs
mais l'analyseur syntaxique HL7 le rejette.

### La désinfection des traces

`sanitizeForLog()` (`MyResource.java:245`) remplace les caractères `\r`, `\n` et `\t` par
des soulignés et tronque à cent caractères avant d'écrire une valeur reçue de l'extérieur
dans le log. Sans cela, un identifiant contenant un retour à la ligne permettrait de
fabriquer de fausses lignes de journal.

Appliquez la même précaution dans vos plugins pour toute valeur venant de l'automate.

## `CORSFilter`

Une dizaine de lignes qui ajoutent les en-têtes CORS à **toutes** les réponses :

```java
responseContext.getHeaders().add("Access-Control-Allow-Origin", "*");
responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");
responseContext.getHeaders().add("Access-Control-Allow-Headers", "origin, content-type, accept, authorization");
responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
```

Sans ces en-têtes, un navigateur refuserait les appels venant de l'interface du LIS. La
politique actuelle est permissive (`*`) ; c'est un point que l'exploitant réseau peut
vouloir resserrer selon le déploiement. Une fois posé, ce fichier ne bouge plus.
