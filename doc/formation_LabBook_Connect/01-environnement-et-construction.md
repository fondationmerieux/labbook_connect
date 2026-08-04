# 01 — Environnement et construction

## Ce qu'il faut installer

| Outil | Pourquoi |
|---|---|
| **JDK 21** | Connect et les plugins sont écrits en Java 21 |
| **git** | récupérer les dépôts |
| **make** | pilote la construction et le lancement du conteneur |
| **podman** | exécute le conteneur |

Sur une Ubuntu, le JDK s'installe par le gestionnaire de paquets :

```bash
sudo apt install openjdk-21-jdk
```

C'est le seul téléchargement conséquent (de l'ordre de 150 à 200 Mo). Lancez-le avant
toute chose. `git`, `make` et `podman` sont probablement déjà présents si vous avez
travaillé sur le LIS.

Vérifiez que les trois commandes utilisées par la procédure de construction sont dans
votre `PATH` :

```bash
java -version && javac -version && jar --version
```

Un environnement de développement intégré n'est pas nécessaire. Ce chapitre décrit la
construction en ligne de commande, qui est reproductible partout et suffit à travailler.
La section [Et avec un IDE ?](#et-avec-un-ide-) explique ce que change Eclipse.

## Récupérer les sources

Placez-vous dans votre répertoire personnel — **pas** dans le dépôt du LIS, sinon le clone
atterrira dedans :

```bash
cd $HOME
git clone https://github.com/fondationmerieux/labbook_connect.git
cd labbook_connect
```

Le dépôt fait quelques dizaines de mégaoctets. Il contient déjà les bibliothèques tierces
dans `lib/` et une version précompilée dans `bin/labbook_connect.jar`.

## Comprendre ce qu'on construit

Le livrable est un **JAR exécutable autonome** : il contient les classes de Connect, ses
ressources, et **toutes ses dépendances**. On le lance par un simple `java -jar`.

Ce format n'est pas celui que produit `jar` par défaut. C'est celui d'Eclipse, qui
embarque un mécanisme maison — le *JAR-in-JAR loader* — capable de charger des `.jar`
imbriqués dans un `.jar`. Le manifeste porte pour cela une entrée `Rsrc-Class-Path` qui
liste les bibliothèques internes.

La procédure en ligne de commande, détaillée dans `doc/build_command_line.md`, reproduit
exactement ce que fait Eclipse. Elle part du JAR de référence livré dans `bin/` pour en
extraire le manifeste, le chargeur et les bibliothèques, puis recompose un JAR identique
avec vos sources fraîchement compilées.

### Les dix étapes

```bash
# 1. répertoires de travail
mkdir -p target/classes eclipse_lib eclipse_loader

# 2. sauver le manifeste d'origine (il porte le Rsrc-Class-Path)
jar xf bin/labbook_connect.jar META-INF/MANIFEST.MF
grep Rsrc-Class-Path META-INF/MANIFEST.MF

# 3. extraire les bibliothèques embarquées
cd eclipse_lib && jar xf ../bin/labbook_connect.jar \
  && rm -rf META-INF org labbook_connect plugin logback.xml && cd ..

# 4. extraire le chargeur JAR-in-JAR d'Eclipse
cd eclipse_loader && jar xf ../bin/labbook_connect.jar org && cd ..

# 5. compiler   <-- à refaire à chaque modification du code
javac -cp "eclipse_lib/*" -d target/classes $(find src/main/java -name "*.java")

# 6. copier les ressources
cp src/main/resource/logback.xml target/classes/

# 7. produire le JAR exécutable   <-- à refaire à chaque modification du code
jar cfm bin/labbook_connect.jar META-INF/MANIFEST.MF \
  -C eclipse_loader . -C target/classes . -C eclipse_lib .

# 8. vérifier le contenu
jar tf bin/labbook_connect.jar | head -20

# 9. vérifier le manifeste final
rm -rf META-INF && jar xf bin/labbook_connect.jar META-INF/MANIFEST.MF
grep Rsrc-Class-Path META-INF/MANIFEST.MF
```

La dixième étape ne concerne pas la compilation : elle configure le partage du volume de
stockage avec le LIS, et fait l'objet du chapitre
[02](02-conteneur-stockage-logs.md#le-volume-partagé-avec-le-lis).

### Le cycle de développement quotidien

Les étapes 1 à 4 et 9 sont de l'initialisation : on les fait **une fois**. Ensuite,
chaque modification du code se résume à :

```bash
javac -cp "eclipse_lib/*" -d target/classes $(find src/main/java -name "*.java")   # étape 5
jar cfm bin/labbook_connect.jar META-INF/MANIFEST.MF \
  -C eclipse_loader . -C target/classes . -C eclipse_lib .                          # étape 7
make devstop && make devbuild && make devrun
```

L'étape 6 n'est à refaire que si vous touchez à `logback.xml`, ce qui est rare.

> **Piège — l'étape 6 oubliée.** Sans `logback.xml` dans `target/classes`, Logback
> retombe sur sa configuration par défaut. Le programme fonctionne, mais les traces
> perdent leur horodatage et leur mise en forme :
>
> ```text
> AAAA-MM-JJ HH:MM:SS | App | Line:49 | ...
> ```
>
> C'est ce format-là qu'il faut voir. S'il a disparu, c'est l'étape 6 qui manque, pas une
> régression du code.

> **Piège — pas de rechargement à chaud.** Contrairement au LIS, où les sources sont
> montées dans le conteneur, ici le JAR est **copié dans l'image**. Modifier un fichier
> Java sans reconstruire le JAR *et* l'image ne produit strictement aucun effet. Si votre
> correction « ne change rien », vérifiez d'abord la date du JAR :
>
> ```bash
> ls -l bin/labbook_connect.jar
> ```

## Construire un plugin

La procédure est plus courte — cinq étapes — parce qu'un plugin n'est pas exécutable et
n'embarque pas de chargeur. Elle est décrite dans le `doc/build_command_line.md` de chaque
dépôt de plugin. Exemple avec GeneXpert :

```bash
cd $HOME
git clone https://github.com/fondationmerieux/labbook_connect_plugin_genexpert.git
cd labbook_connect_plugin_genexpert

mkdir -p target/classes bin/plugin

javac -cp "lib/*:../labbook_connect/target/classes" -d target/classes $(find src -name "*.java")

jar cfm bin/plugin/AnalyzerGeneXpert.jar MANIFEST.MF \
  -C ../labbook_connect/target/classes . -C target/classes . \
  src resources doc lib script README.md LICENSE.md CHANGELOG.md MANIFEST.MF .classpath .project

jar tf bin/plugin/AnalyzerGeneXpert.jar | head -20
```

Deux points méritent attention.

**Le plugin se compile contre les classes de Connect.** Le `-cp` pointe vers
`../labbook_connect/target/classes` : le plugin a besoin de `plugin.Analyzer` et de
`plugin.Connect_util`. Les deux dépôts doivent donc être côte à côte, et Connect doit
avoir été compilé au moins une fois (étape 5) avant de compiler un plugin.

**Le JAR du plugin réembarque ces classes de Connect.** C'est visible dans le `jar cfm` :
`-C ../labbook_connect/target/classes .`. Le chargeur de classes de Connect en tient
compte, et le mécanisme fonctionne — mais cela veut dire qu'un plugin compilé contre une
version ancienne de l'interface embarque cette version ancienne. Après une évolution de
`Analyzer.java`, **recompilez tous les plugins**.

> **Piège — le nom du fichier JAR n'est pas décoratif.** Connect déduit le nom de la
> classe à instancier du nom du fichier : `AnalyzerGeneXpert.jar` donne
> `plugin.AnalyzerGeneXpert`. Le nom doit correspondre exactement, **casse comprise**.
> Renommer le JAR, c'est le rendre incapable de se charger. Le détail du mécanisme est au
> chapitre [03](03-le-coeur-de-connect.md#analyzerloader--le-chargement-dynamique).

## Les bibliothèques tierces

Elles sont décrites dans `pom.xml` et **physiquement présentes** dans `lib/`. Ce n'est pas
une négligence : un laboratoire n'a pas toujours accès à Internet, et embarquer les
bibliothèques fige aussi leurs numéros de version.

| Bibliothèque | Version | Rôle |
|---|---|---|
| **Jetty** (`jetty-server`, `jetty-io`, `jetty-util`, `jetty-servlet`) | 11.0.18 | serveur HTTP embarqué |
| **Jersey** (`jersey-container-jetty-*`, `jersey-hk2`) | — | couche REST par-dessus Jetty |
| **Jakarta** (`jakarta.json` 2.0.1, `jakarta.activation-api` 2.1.2, `jaxb-runtime` 4.0.4) | — | dépendances tirées par Jersey |
| **Logback** (`logback-classic`, `logback-core`) | 1.4.11 | mise en forme des traces |
| **slf4j** (`slf4j-api`) | 2.0.9 | façade de journalisation |
| **toml4j** | 0.7.2 | lecture des fichiers de configuration TOML |
| **HAPI** (`hapi-base`, `hapi-structures-v251`) | 2.2 | manipulation des messages HL7 v2 |

Les versions exactes en vigueur sont celles des fichiers présents dans `lib/` et déclarées
dans `pom.xml` — ce tableau donne l'état correspondant aux versions décrites dans le
[README](README.md#versions-décrites).

`hapi-structures-v251` mérite un mot : le suffixe `v251` désigne la version **HL7 2.5.1**.
C'est cette bibliothèque qui donne des objets Java typés (`OML_O33`, `QBP_Q11`, `MSH`,
`OBX`…) au lieu d'obliger à écrire un analyseur syntaxique HL7 à la main.

> **Piège — HAPI ne couvre pas tout.** La bibliothèque connaît les structures **standard**.
> Quand un automate envoie une variante que HAPI ne modélise pas, on ne peut plus passer
> par les objets typés : il faut construire la portion de message à la main, par
> concaténation de chaînes. C'est plus verbeux et plus exposé aux fautes de frappe, mais
> c'est parfois la seule voie. Selon les cas, le problème se manifeste à la compilation
> (« type inconnu ») ou seulement à l'exécution.

Le numéro de version du projet dans `pom.xml` n'est pas maintenu. La version qui fait foi
est la constante `VERSION` de `src/main/java/labbook_connect/labbook_connect/App.java` :
c'est elle que le `Makefile` lit, que le service `test` renvoie, et qu'on voit en première
ligne des logs au démarrage.

## Et avec un IDE ?

Le projet a été développé sous Eclipse. Un IDE apporte deux choses : la construction en
un clic (*Build Project* puis *Export → Runnable JAR*, en demandant l'inclusion des
bibliothèques dans le JAR), et surtout la détection à la frappe des structures HL7 que
HAPI ne connaît pas.

Rien n'y oblige. La configuration du *Java Build Path* est documentée par des captures
d'écran dans les répertoires `doc/` des dépôts. Le format produit par la ligne de commande
et celui produit par Eclipse sont interchangeables — c'est tout l'objet de la procédure en
dix étapes.
