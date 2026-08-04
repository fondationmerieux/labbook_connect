# Manuel de développement LabBook Connect

Ce manuel s'adresse à un développeur qui doit connecter un automate d'analyse (dispositif
de diagnostic in vitro) à un système de gestion de laboratoire, en passant par LabBook
Connect. Il couvre le moteur Connect lui-même et l'écriture des plugins d'automates.

Il est le pendant du [manuel de développement LabBook](../formation/README.md), qui traite
du LIS. Les deux se lisent indépendamment : on peut développer un plugin d'automate sans
jamais toucher au code du LIS, et inversement.

## Prérequis du lecteur

Java, la ligne de commande Linux, et les notions de base des réseaux TCP/IP (socket,
client, serveur, port). Aucune connaissance préalable de HL7 ni d'ASTM n'est supposée :
ce qu'il faut en savoir est expliqué au fil des chapitres.

Le manuel n'enseigne ni Java, ni Maven, ni Podman : il explique comment LabBook Connect
les utilise.

## Chapitres

| # | Chapitre | Contenu |
|---|---|---|
| [00](00-vue-d-ensemble.md) | Vue d'ensemble | Ce que fait Connect, le profil IHE-LAW, les trois transactions, pourquoi des plugins |
| [01](01-environnement-et-construction.md) | Environnement et construction | JDK, clone, compilation en ligne de commande, cycle de développement |
| [02](02-conteneur-stockage-logs.md) | Conteneur, stockage et logs | `Dockerfile`, `Makefile`, volume partagé, où lire les traces |
| [03](03-le-coeur-de-connect.md) | Le cœur de Connect | `App`, `AnalyzerLoader`, `MyResource`, `CORSFilter` |
| [04](04-le-contrat-de-plugin.md) | Le contrat de plugin | L'interface `Analyzer`, les utilitaires `Connect_util` |
| [05](05-configuration-setting-et-mapping.md) | Configuration | Le fichier de réglage, le fichier de correspondance |
| [06](06-plugin-astm.md) | Écrire un plugin ASTM | Le protocole E1381, la machine à états, les conversions |
| [07](07-plugin-hl7-mllp.md) | Écrire un plugin HL7/MLLP | MLLP, routage sur MSH-9, mapping sur les segments |
| [08](08-cote-lis.md) | Le côté LIS | Ce que le LIS doit exposer, l'interface de configuration |
| [09](09-tester-et-diagnostiquer.md) | Tester et diagnostiquer | Simulateurs, lecture des logs, pannes courantes |

## Convention

Les chemins de fichiers sont donnés depuis la racine du dépôt concerné, par exemple
`src/main/java/plugin/Analyzer.java` pour `labbook_connect`, ou
`src/plugin/AnalyzerGeneXpert.java` pour le plugin GeneXpert. Quand une ambiguïté est
possible, le nom du dépôt précède le chemin.

Les encadrés **Piège** signalent une erreur fréquente ou un comportement contre-intuitif.

## Les dépôts

| Dépôt | Contenu |
|---|---|
| `labbook_connect` | le moteur : serveur HTTP, chargeur de plugins, interface de plugin |
| `labbook_connect_plugin_genexpert` | plugin d'exemple **ASTM** (analyseur qui ne parle pas HL7) |
| `labbook_connect_plugin_roche` | plugin d'exemple **HL7/MLLP** (analyseur conforme à IHE-LAW) |
| `labbook_connect_plugin_mindray`, `labbook_connect_plugin_sysmex` | autres plugins, à des stades d'avancement variables |

Chaque plugin vit dans son propre dépôt et suit son propre cycle de version. C'est une
décision de conception, expliquée au chapitre [00](00-vue-d-ensemble.md).

## Versions décrites

Ce manuel a été écrit d'après les versions suivantes. **Les numéros de ligne cités dans les
chapitres [03](03-le-coeur-de-connect.md), [06](06-plugin-astm.md) et
[07](07-plugin-hl7-mllp.md) s'y rapportent** ; les noms de méthodes, eux, sont stables d'une
version à l'autre.

| Composant | Version |
|---|---|
| `labbook_connect` | **1.0.14** |
| `labbook_connect_plugin_genexpert` | **1.0.14** |
| `labbook_connect_plugin_roche` | **1.0.5** |

Les trois numéros ne progressent pas ensemble : chaque dépôt a son propre `CHANGELOG.md` et
son propre rythme. Ne déduisez pas d'un numéro de plugin la version du moteur, ni l'inverse.

Pour connaître les versions réellement en service sur une installation :

```bash
wget -qO- http://localhost:8080/connect/test              # version du moteur
wget -qO- http://localhost:8080/connect/info/GX_01        # versions du plugin et du réglage
```

L'environnement de référence est **Java 21** (image `eclipse-temurin:21`), avec les
bibliothèques figées listées au chapitre
[01](01-environnement-et-construction.md#les-bibliothèques-tierces).
