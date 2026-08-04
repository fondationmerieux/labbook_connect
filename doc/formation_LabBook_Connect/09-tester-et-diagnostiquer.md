# 09 — Tester et diagnostiquer

Développer un plugin d'automate sans avoir l'automate sous la main est la situation
normale, pas l'exception. Ce chapitre décrit comment travailler quand même, et comment lire
ce qui se passe quand ça ne marche pas.

## Les simulateurs

Chaque dépôt de plugin contient un répertoire `script/` avec des programmes Python écrits
pendant le développement. Ils ne sont pas de la documentation : ce sont les outils qui ont
réellement servi.

Pour le plugin GeneXpert :

| Script | Rôle |
|---|---|
| `simulate_genexpert_lab27.py` | joue un automate qui envoie une requête (LAB-27) |
| `simulate_genexpert_lab29.py` | joue un automate qui remonte des résultats (LAB-29) |
| `simulate_client_genexpert_send_astm_result_msg.py` | même chose, avec des options en ligne de commande |
| `simulate_server_connect_astm.py` | joue **Connect** : écoute et journalise ce qu'un vrai automate envoie |

Les deux premiers portent leur hôte et leur port en tête de fichier, à éditer :

```python
HOST = '0.0.0.0'
PORT = 12345
```

Le troisième prend ses paramètres en arguments, ce qui est plus commode pour enchaîner
plusieurs essais :

```bash
python3 simulate_client_genexpert_send_astm_result_msg.py \
    --host 192.0.2.10 --port 12345 --specimen TEST123
```

Le dernier renverse les rôles : c'est un serveur ASTM qui accepte une connexion, journalise
chaque octet et peut écrire le message brut dans un fichier.

```bash
python3 simulate_server_connect_astm.py --port 12345 --capture capture.txt
```

Il est précieux dans deux situations : quand vous avez accès à un vrai automate mais pas
encore de plugin, et quand vous voulez savoir ce que l'automate envoie **vraiment**, sans
que Connect s'interpose.

> **Écrivez votre simulateur avant votre plugin.** Les documentations constructeur
> contiennent presque toujours des exemples de messages. Recopiez-en un dans un script, et
> vous disposez d'un banc d'essai reproductible avant même d'avoir écrit une ligne de Java.
> C'est aussi la seule façon de vérifier que vous avez lu la documentation correctement.

Le plugin Roche a l'équivalent en HL7/MLLP dans `simulate_server_connect_hl7.py` et
`test_send_result.py`.

## La procédure d'essai de bout en bout

Elle suppose le LIS et Connect démarrés, et un plugin compilé.

**1. Vérifier que Connect répond.**

```bash
podman ps --all                                  # les deux conteneurs tournent
wget -qO- http://localhost:8080/connect/test     # renvoie le numéro de version
```

**2. Ouvrir la trace, dans un terminal séparé.** À faire *avant* la suite, pas après.

```bash
podman exec -it labbook_connect tail -f /app/logs/labbook_connect.log
```

**3. Déposer les trois fichiers et recharger** — depuis l'interface du LIS ou à la main.
Voir chapitre [08](08-cote-lis.md#configurer-un-automate-depuis-linterface).

Dans la trace, vous devez voir le `.jar` découvert, le réglage lu, l'instance créée et
l'écoute démarrée sur le port attendu.

**4. Déclencher un message** avec un simulateur.

**5. Lire la trace.** Un échange complet donne, dans l'ordre :

```text
Accepted connection from /...
<<< DEBUG BYTE 0x05 (ENQ)
>>> Sent ACK [0x06] in response to ENQ
<<< DEBUG BYTE 0x02 (STX)
...
<<< Received EOT — message transmission complete
DEBUG: Complete ASTM message: ...
Detected ASTM result message with H| segment, routing to lab29...
Lab29 GeneXpert : Received ASTM message ...
Archived message at /storage/resource/connect/analyzer/GX_01/archive_lab29/...
Lab29 GeneXpert : Converted HL7 OUL^R22: ...
Sending HL7 payload to http://.../lab29/GX_01 ...
Upstream HTTP 200 from ... ; first80='MSH|...'
```

Chaque ligne correspond à une étape identifiable : connexion, dialogue ASTM, assemblage,
routage, archivage, conversion, envoi, réponse. La **première ligne manquante** désigne
l'étape en défaut.

**6. Vérifier côté LIS**, dans l'écran des transactions.

Sur un premier essai, il est normal et attendu que le LIS réponde que l'échantillon est
inconnu : aucun dossier ne porte encore ce code. Cela ne veut pas dire que rien ne marche —
au contraire, cela prouve que la chaîne complète a fonctionné. Créez ensuite un dossier avec
le code utilisé par le simulateur pour aller jusqu'au bout.

## Les archives

Quand `archive_msg = "Y"`, chaque message brut est écrit dans le répertoire de l'instance :

```text
/storage/resource/connect/analyzer/GX_01/archive_lab29/LAB-29_Analyzer_AAAAMMJJ_HHMMSS.txt
```

Le nom de fichier porte la transaction, la source (`Analyzer` ou `LIS`) et l'horodatage.

Ces fichiers servent à trois choses :

1. **Trancher les responsabilités.** Le message brut permet de savoir si l'automate a envoyé
   quelque chose d'inattendu ou si le plugin l'a mal traité. Sans lui, on argumente.
2. **Rejouer un échange.** Un message archivé se réinjecte dans un simulateur, ce qui permet
   de reproduire un incident sans l'automate.
3. **Conserver la trace.** Dans certains contextes, le message brut reçu d'un automate doit
   être conservé plusieurs années. Ce sont de petits fichiers texte ; laissez l'archivage
   actif.

Seul le message émis par la source est archivé, pas l'échange complet.

## Où chercher selon le symptôme

| Symptôme | Où regarder |
|---|---|
| le conteneur ne démarre pas | rien à lire dedans : reconstruisez avec la trace de `make devbuild` sous les yeux |
| `Unsupported connection type:` | le `type_cnx` du réglage ne correspond pas à ce que gère le plugin |
| `ERROR lack of settings` | un champ obligatoire manque dans le fichier de réglage |
| aucune ligne `Found plugin JAR` | le `.jar` n'est pas au bon endroit, ou son nom commence par un point |
| `ClassNotFound` au chargement | le nom du `.jar` ne correspond pas à la classe, ou la casse diffère |
| l'instance existe mais n'écoute pas | plugin chargé, réglage lu, mais `listenDevice()` a refusé — cherchez la cause dans les lignes suivantes |
| `Connection refused` côté simulateur | port hors de la plage publiée par le conteneur, ou instance non démarrée |
| `Analyzer not found for id` sur LAB-28 | l'identifiant de l'URL ne correspond à aucune instance chargée |
| échantillon inconnu côté LIS | normal si aucun dossier ne porte ce code |
| dialogue parfait, valeurs absentes | mapping en défaut — voir [08](08-cote-lis.md#quand-les-résultats-narrivent-pas-dans-les-cases) |
| erreur d'insertion côté LIS | ce n'est plus Connect : passez aux logs du LIS |

## La panne silencieuse

C'est la difficulté propre à ce domaine, et elle mérite d'être nommée.

Un automate acquitte volontiers un message **syntaxiquement** correct sans rien en faire.
Le dialogue se déroule parfaitement, les acquittements arrivent, les traces sont propres —
et rien ne se produit sur la machine. Aucune erreur, aucun message, rien à l'écran de
l'automate.

La cause est presque toujours un détail de contenu : un séparateur en trop, un champ à la
mauvaise position, un code tronqué parce qu'il dépassait la longueur admise.

Ce qui aide, dans l'ordre d'efficacité :

1. **Comparer au message brut d'un échange réussi**, s'il en existe un dans les archives.
2. **Rejouer l'exemple de la documentation constructeur** : s'il passe et que le vôtre non,
   la différence est dans votre message.
3. **Vérifier les versions.** Une documentation en version 4 face à un microcode en
   version 3 explique beaucoup d'écarts inexplicables. Réclamez la version récente du
   document, et relevez celle du microcode.
4. **Demander à quelqu'un qui a déjà écrit un pilote pour ce matériel**, même dans un autre
   langage. La structure exacte attendue lui est connue.

Ne prévoyez pas ce type d'intégration au jour près. Le facteur limitant n'est pas la
difficulté du code, c'est le rythme des allers-retours avec la machine — a fortiori quand
elle est distante et qu'il faut quelqu'un sur place pour agir dessus.

## Ce qu'il faut obtenir du constructeur

Un seul document compte vraiment : la **spécification du protocole d'interface** (souvent
intitulée *LIS interface protocol specification* ou approchant). C'est celle qui décrit :

- la norme suivie et sa version (ASTM E1381/E1394, HL7 v2.x) ;
- la couche de transport et le format des trames ;
- la structure des enregistrements ou segments, champ par champ ;
- **des exemples de messages réels** — c'est la partie la plus utile.

Une documentation datée de moins de deux ou trois ans est un bon signe. Une très ancienne
invite à la prudence : beaucoup de choses ont pu être corrigées entre-temps.

Ce que ce document ne contient **presque jamais** : la liste des analyses que l'appareil
sait réaliser, et les codes correspondants. Ces informations se relèvent sur l'appareil et
dans ses messages (voir chapitre
[05](05-configuration-setting-et-mapping.md#où-trouver-le-contenu)).

Les autres documents — installation, câblage, configuration réseau — relèvent de la mise en
service, pas du développement. Ils contiennent tout de même des détails qui bloquent une
intégration entière : la présence de deux prises réseau à l'arrière de l'appareil dont une
seule est utilisable, ou la contrainte de placer l'automate sur un sous-réseau distinct.
Lisez-les au moins en diagonale.

## Versions et mise en production

Trois numéros de version cohabitent, indépendants :

| Version | Où | Comment la lire |
|---|---|---|
| Connect | constante `VERSION` de `App.java` | `GET /connect/test`, ou la première ligne du log |
| plugin | champ interne du plugin | `GET /connect/info/{id}` |
| réglage et mapping | champ `version` des fichiers TOML | `GET /connect/info/{id}`, ou les traces de chargement |

Le champ `version` des fichiers TOML n'est pas exploité par le code : il ne sert
aujourd'hui qu'aux traces et à la lisibilité. Renseignez-le quand même — c'est ce qui permet
de savoir, six mois plus tard, quelle configuration tourne réellement.

Pour livrer une image de Connect :

```bash
make build VERSION=1.0.14     # construit depuis le tag v1.0.14
make save  VERSION=1.0.14     # exporte en .tar, compresse en .tar.xz, calcule les MD5
```

Pour livrer un plugin, il n'y a rien à empaqueter : c'est le `.jar`, accompagné de ses deux
fichiers TOML d'exemple. Ce n'est **pas** un répertoire prêt à l'emploi à recopier tel quel :
chaque fichier va à un endroit différent, et le réglage doit être édité.

Une dernière remarque d'exploitation : rien n'indique combien de temps une instance peut
rester à l'écoute sans être relancée. Aucun retour d'expérience ne couvre plusieurs mois de
fonctionnement continu. Comme pour tout service de longue durée, prévoyez un redémarrage
périodique plutôt que de découvrir la limite en production.
