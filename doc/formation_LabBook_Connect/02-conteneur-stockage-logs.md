# 02 — Conteneur, stockage et logs

## Un conteneur volontairement minuscule

Le `Dockerfile` de Connect tient en cinq instructions :

```dockerfile
FROM eclipse-temurin:21
WORKDIR /app
RUN mkdir -p /app/logs
COPY /bin/labbook_connect.jar /app/labbook_connect.jar
CMD ["sh", "-c", "java -jar labbook_connect.jar > logs/labbook_connect.log 2>&1"]
```

Rien de plus. Là où l'image du LIS part d'une distribution Linux complète et y ajoute
LibreOffice, `wkhtmltopdf` et un client MySQL, celle de Connect part d'une image Java et
y dépose un fichier. Sa construction dure quelques secondes.

Cette frugalité a des conséquences pratiques :

- **Il n'y a presque aucun utilitaire à l'intérieur.** Pas d'éditeur, pas d'outil réseau.
  Entrer dans le conteneur pour diagnostiquer un problème est peu utile.
- **Si Java s'arrête, le conteneur s'arrête.** Une exception non rattrapée au démarrage
  fait disparaître le conteneur — et avec lui la possibilité d'aller lire les logs à
  l'intérieur. D'où la règle de la section [Lire les logs](#lire-les-logs).

> **Piège — l'image de base peut disparaître.** Le projet utilisait à l'origine une image
> `openjdk:21` ; elle a cessé d'être publiée quand l'éditeur est passé aux versions
> suivantes. Il a fallu basculer vers `eclipse-temurin:21`, un équivalent libre. Le
> problème peut se reproduire : tant que l'image est déjà présente sur votre machine,
> `make devbuild` réussit et vous ne voyez rien ; le jour où vous construisez sur une
> machine vierge, la construction échoue à la première ligne. Cette dépendance à un
> service extérieur est connue et non résolue.

> **Piège — podman sans registre configuré.** Sur une installation neuve, `podman` ne sait
> pas où chercher les images. La première ligne de `make devbuild` échoue alors avec une
> erreur de résolution, ou vous demande de choisir entre plusieurs registres. Éditez
> `/etc/containers/registries.conf` et déclarez-y les registres publics :
>
> ```text
> unqualified-search-registries = ["docker.io", "quay.io"]
> ```
>
> La ligne existe souvent, commentée. Il suffit de la décommenter et de la compléter.
> Que l'image du LIS ait pu être téléchargée auparavant ne prouve rien : elle venait
> peut-être d'un registre déjà qualifié dans son `Dockerfile`.

## Les cibles du `Makefile`

Ce sont les mêmes conventions que pour le LIS.

| Cible | Effet |
|---|---|
| `make devbuild` | construit l'image `localhost/labbook-connect:latest` depuis le répertoire courant |
| `make devrun` | lance le conteneur `labbook_connect` |
| `make devstop` | arrête le conteneur |
| `make devclean` | supprime l'image `latest` |
| `make devreload` | enchaîne `devstop`, `devclean`, `devbuild`, `devrun` |
| `make build VERSION=x.y.z` | construit une image de livraison depuis le tag `vx.y.z` |
| `make save VERSION=x.y.z` | exporte l'image en `.tar`, la compresse en `.tar.xz`, calcule les empreintes MD5 |
| `make clean VERSION=x.y.z` | supprime l'image de version |

`make build` fait un `git checkout` sur le tag correspondant avant de construire, puis
revient sur `master`. Votre copie de travail doit donc être propre.

Le numéro de version par défaut n'est pas écrit dans le `Makefile` : il est extrait de la
constante `VERSION` de `App.java` au moment où `make` s'exécute.

Vérifiez toujours que le conteneur tourne réellement :

```bash
podman ps --all
```

Vous devez y voir `labbook_connect`, et — si vous travaillez avec le LIS — le conteneur du
LIS à côté. **Les deux coexistent sans problème** : il n'y a aucune raison d'arrêter l'un
pour lancer l'autre, c'est même tout l'intérêt.

## Les ports

Deux familles de ports interviennent, et on les confond facilement.

**Le port 8080** est celui de l'API HTTP de Connect. Il est publié par `make devrun` et
c'est par lui que le LIS parle à Connect.

**Les ports des automates** sont ceux sur lesquels chaque instance écoute (ou se connecte).
Ils sont déclarés dans les fichiers de réglage, un par automate. Comme un conteneur est
une boîte étanche, ces ports doivent être publiés au lancement, faute de quoi l'automate
ne pourra jamais joindre Connect.

En mode développement, le `Makefile` ne publie qu'une seule plage :

```make
DEVRUN_GENERAL_OPTIONS=--rm --detach --name=$(CONTAINER_NAME) \
  --publish=$(DEVRUN_HTTP):$(DEVRUN_HTTP) --publish=12300-12399:12300-12399
```

> **Piège — la plage de ports du mode développement.** En développement, **seuls les ports
> 12300 à 12399** sont accessibles depuis l'extérieur du conteneur. Un fichier de réglage
> qui demande le port 7500 — valeur pourtant présente dans les fichiers d'exemple — donnera
> une instance qui démarre, écrit `Server started on port 7500` dans les logs, et que
> personne ne peut joindre. Le symptôme est un `Connection refused` côté automate ou côté
> simulateur, sans la moindre erreur côté Connect.
>
> En production, la configuration de lancement publie des plages plus larges
> (3100-3199, 7500-7599, 12300-12399 selon les déploiements). En développement, prenez
> un port dans 12300-12399, point.

## Le volume partagé avec le LIS

Connect attend un volume permanent monté sur `/storage`. C'est là que vivent les plugins,
leur configuration et les archives de messages.

Ce volume doit être **le même que celui du LIS** : c'est ainsi que l'interface du LIS peut
déposer un plugin que Connect ira lire. Le partage se déclare dans `labbook.conf`, le
fichier de configuration commun, par la variable `DEVRUN_STORAGE` :

```text
DEVRUN_STORAGE=/home/sigl/labbook_python/devrun_storage
```

Renseignez-y le répertoire de stockage **du LIS**, pas un répertoire propre à Connect.
`make devrun` le monte alors sur `/storage` dans le conteneur.

### L'arborescence

Connect crée au démarrage ce dont il a besoin :

```text
/storage/resource/connect/analyzer/
├── plugin/       <- les .jar des plugins
├── setting/      <- un fichier de réglage TOML par automate
├── mapping/      <- les fichiers de correspondance TOML
└── GX_01/        <- un répertoire par instance, nommé d'après son identifiant
    ├── mapping/
    ├── lab27/
    ├── lab29/
    ├── archive_lab27/
    ├── archive_lab28/
    └── archive_lab29/
```

Les trois premiers répertoires sont **communs** : c'est là qu'on dépose les fichiers. Les
suivants sont créés automatiquement, un jeu complet par instance d'automate, à la première
prise en compte de son fichier de réglage.

Les répertoires `archive_lab27`, `archive_lab28` et `archive_lab29` reçoivent les messages
bruts, un fichier texte horodaté par message. C'est la piste d'audit du système ; son rôle
est détaillé au chapitre [09](09-tester-et-diagnostiquer.md#les-archives).

## Lire les logs

Connect écrit dans `/app/logs/labbook_connect.log`, à l'intérieur du conteneur.

**La bonne pratique** est d'ouvrir la trace **dans un second terminal, avant** de déclencher
quoi que ce soit :

```bash
podman exec -it labbook_connect tail -f /app/logs/labbook_connect.log
```

On voit alors défiler en direct chaque étape d'un échange : réception, archivage,
conversion, envoi au LIS, réponse. C'est l'outil de diagnostic principal, et souvent le
seul.

Pour relire le début du fichier — la phase de démarrage, notamment — il faut entrer dans
le conteneur :

```bash
podman exec -it labbook_connect sh
```

… en gardant à l'esprit qu'il n'y a pas d'éditeur à l'intérieur.

> **Piège — le `tail -f` qui s'arrête tout seul.** `make devstop` détruit le conteneur ;
> le `tail` qui tournait dedans meurt avec lui. Ce n'est pas une panne : relancez-le après
> `make devrun`.

> **Piège — le log n'est pas recopié à l'extérieur.** Le LIS remonte ses journaux sur le
> système hôte ; Connect ne le fait pas encore de façon fiable. Ne cherchez pas les traces
> d'un incident récent dans un fichier hors conteneur, elles n'y seront pas.

Les premières lignes après un démarrage réussi ressemblent à ceci :

```text
*** <date> BEGIN App main version: 1.0.14 ***
Directory already exists: /storage/resource/connect/analyzer/plugin
Found plugin JAR: AnalyzerGeneXpert.jar
DEBUG className: plugin.AnalyzerGeneXpert
DEBUG: plugin.AnalyzerGeneXpert implements Analyzer.
Processing settings file: /storage/resource/connect/analyzer/setting/analyzer_genexpert.toml
ASTM Server started on port 12345
START server on 0.0.0.0:8080
```

Ces sept lignes disent presque tout : la version, l'arborescence, le plugin trouvé et
chargé, le réglage lu, l'écoute de l'automate ouverte, et le serveur HTTP démarré. Quand
quelque chose ne marche pas, c'est là qu'il faut regarder d'abord — la ligne manquante
désigne l'étape fautive.
