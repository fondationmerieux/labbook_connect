# 07 — Écrire un plugin HL7/MLLP

Ce chapitre s'appuie sur le plugin Roche
(`labbook_connect_plugin_roche/src/plugin/AnalyzerRoche.java`). C'est l'archétype opposé à
celui du chapitre précédent : l'automate parle déjà HL7 v2.5.1 et suit le profil IHE-LAW.

Les numéros de ligne cités correspondent au **plugin Roche 1.0.5** — un cycle de version
distinct de celui du moteur et du plugin GeneXpert (voir
[Versions décrites](README.md#versions-décrites)).

Le plugin fait environ mille lignes contre près de deux mille pour GeneXpert, et la
différence n'est pas une question de soin apporté au code : **tout le transport et
l'essentiel des conversions disparaissent**. Ce qui reste est du routage et du mapping.

C'est la démonstration concrète de l'intérêt d'un automate conforme à la norme : quand les
deux extrémités parlent le même langage, le plugin n'a presque rien à faire.

## Le transport : MLLP, et c'est tout

L'automate parle HL7 v2.5.1 sur TCP, encadré en **MLLP**. Connect fournit les deux
fonctions nécessaires, il n'y a rien à écrire :

```java
String receivedMessage = Connect_util.readMLLPMessage(this.inputStream);
...
String framed = Connect_util.encapsulateHL7Message(responseMessage);
this.outputStream.write(framed.getBytes(StandardCharsets.UTF_8));
this.outputStream.flush();
```

Comparez à la machine à états ASTM du chapitre [06](06-plugin-astm.md#la-machine-à-états) :
pas de caractères de contrôle à gérer, pas de trames à assembler, pas de checksum à
calculer, pas d'acquittement de bas niveau. MLLP se contente de délimiter les messages ; la
fiabilité est celle de TCP.

`listenForIncomingMessages()` (ligne 658) tient de ce fait en une soixantaine de lignes :

1. lire un message MLLP (bloquant) ;
2. si un envoi synchrone attend une réponse, la lui remettre et reprendre la boucle ;
3. sinon, router le message et, si une réponse est produite, la renvoyer encadrée en MLLP.

Un délai de lecture qui expire n'est pas une erreur : on continue d'attendre. Une erreur
d'entrée-sortie fait sortir de la boucle et déclenche la reconnexion.

### L'attente synchrone de LAB-28

Le point 2 mérite explication. Quand le LIS pousse une prescription (LAB-28), le plugin
envoie le message à l'automate puis **attend son accusé de réception** pour pouvoir répondre
au LIS. Or les messages entrants arrivent tous par la même boucle d'écoute.

Un petit protocole d'attente résout le problème :

```java
synchronized (responseLock) {
    if (waitingForResponse) {
        expectedResponse = receivedMessage.toString();
        waitingForResponse = false;
        responseLock.notifyAll();
        deliveredToSyncCaller = true;
    }
}
if (deliveredToSyncCaller) continue;
```

`sendHL7MessageToAnalyzer()` (ligne 301) pose le drapeau, envoie, puis attend sur le verrou
jusqu'au délai imparti. Ce mécanisme doit être libéré dans `stopListening()`, faute de quoi
un arrêt pendant une transaction LAB-28 laisserait un fil bloqué.

## Le routage : MSH-9

Là où le plugin ASTM devait deviner la transaction d'après le type d'enregistrement, ici
l'information est explicite : le champ **MSH-9** porte le type du message.

```java
PipeParser parser = new PipeParser();
parser.getParserConfiguration().setValidating(false);
Message message = parser.parse(hl7Message);

// MSH-9 : "OUL^R22" -> "OUL_R22"
String msh9 = msgCode + "_" + trig;

if      ("OUL_R22".equals(msh9)) responseMessage = lab29(hl7Message);
else if ("QBP_Q11".equals(msh9)) responseMessage = lab27(hl7Message);
else {
    logger.info("DEBUG: Received an unknown HL7 message type.");
    return null;
}
```

Les types non gérés sont tracés et ignorés — pas d'erreur, pas de réponse.

Deux précautions encadrent ce code.

**Le message est validé a minima avant d'être analysé** : s'il ne commence pas par `MSH`,
il est écarté immédiatement. Cela évite de faire travailler l'analyseur syntaxique sur du
bruit réseau.

**Les fins de ligne sont normalisées** :

```java
hl7Message = hl7Message.replace("\r\n", "\r").replace("\n", "\r");
```

HL7 v2 sépare ses segments par un `CR` seul. Un émetteur qui envoie des `CRLF` — ou un
outil intermédiaire qui « corrige » les fins de ligne — produit un message que l'analyseur
refuse, alors qu'il paraît parfaitement normal à la lecture.

> **Piège — la validation HAPI désactivée.** `setValidating(false)` est un choix délibéré :
> avec la validation active, HAPI rejette des messages que l'automate émet réellement et
> qui sont exploitables. Le revers est qu'aucun contrôle applicatif n'est fait sur le
> contenu. Un champ manquant ne provoquera pas d'erreur à l'analyse : il donnera une chaîne
> vide plus loin, souvent sans conséquence visible immédiate.

## Les trois transactions

Elles sont **beaucoup plus courtes** que leurs équivalents ASTM.

### LAB-27 et LAB-29 — transmettre au LIS

Comme il n'y a aucune conversion de format à faire, les deux se ramènent à une seule
méthode privée :

```java
@Override
public String lab27(final String msg) {
    return processLabTransaction(msg, "LAB-27", this.url_upstream_lab27);
}

@Override
public String lab29(final String msg) {
    String mapped = applyMappingOUL_R22(msg);
    return processLabTransaction(mapped, "LAB-29", this.url_upstream_lab29);
}

private String processLabTransaction(String msg, String labType, String url) {
    Connect_util.archiveMessage(this.getId_analyzer(), this.archive_msg, msg, labType, "Analyzer");
    String response = Connect_util.send_hl7_msg(this, url, msg);
    return response;
}
```

Archiver, envoyer, retourner la réponse. La seule différence entre les deux est que LAB-29
applique d'abord le mapping des résultats.

### LAB-28 — transmettre à l'automate

`lab28()` (ligne 215) analyse l'`OML^O33`, vérifie la présence d'un groupe `SPECIMEN`,
ré-encode le message pour normaliser sa syntaxe, applique le mapping des prescriptions,
puis l'envoie à l'automate et attend l'accusé de réception :

```java
OML_O33 omlMessage = (OML_O33) parser.parse(str_OML_O33);
if (omlMessage.getSPECIMENReps() == 0) return "Error: No SPECIMEN segment found.";

String formattedHL7 = parser.encode(omlMessage);
String mappedHL7    = applyMappingOML_O33(formattedHL7);
return sendHL7MessageToAnalyzer(mappedHL7);
```

Le message reste en **v2.5.1 de bout en bout** — c'est du passage direct. L'analyse puis le
ré-encodage ne servent qu'à normaliser la syntaxe et à détecter les défauts grossiers.

Tous les automates ne permettent pas cette simplicité. Certains n'acceptent que des versions
plus anciennes de HL7 et imposent une conversion de version (par exemple `OML^O33` v2.5.1
vers `ORM^O01` v2.3.1), ce qui rallonge sensiblement le plugin. La règle générale :
**convertissez le moins possible**, et documentez ce que vous convertissez.

## Le mapping : sur les segments, pas dans la conversion

Puisqu'il n'y a pas de convertisseur où glisser le mapping, celui-ci s'applique dans deux
méthodes dédiées, qui modifient le message HL7 en place.

**`applyMappingOUL_R22`** (ligne 798), dans le sens automate → LIS :

- relève l'identifiant d'analyse dans `OBR-4` (premier composant) ;
- parcourt les segments `OBX` et lit le code de résultat du constructeur en `OBX-3` ;
- cherche le couple (`test`, `vendor_result_code`) dans `[[ivd_mapping]]` ;
- remplace l'identifiant `OBX-3` par le `lis_result_code` trouvé.

**`applyMappingOML_O33`** (ligne 932), dans le sens LIS → automate :

```java
for (Structure s : message.getAll("OBR")) {
    OBR obr = (OBR) s;
    String lisCode = obr.getUniversalServiceIdentifier().encode().split("\\^", -1)[0].trim();
    // ... chercher lis_test_code dans [[ivd_test]] ...
    if (!vendorCode.isEmpty()) {
        obr.getUniversalServiceIdentifier().getIdentifier().setValue(vendorCode);
    }
}
return parser.encode(message);
```

Dans les deux cas, **si aucune correspondance n'est trouvée, la valeur d'origine est
conservée**. C'est un choix prudent — on ne perd rien — mais c'est aussi ce qui rend
l'absence de mapping silencieuse.

> **Piège — seul `OBR-4.1` est lu en LAB-28.** Il n'y a pas de repli sur les autres
> composants du champ. Si le LIS place le code ailleurs, aucune correspondance ne sera
> trouvée et la prescription partira avec le code du LIS, que l'automate ne connaît pas.

Le fichier de correspondances a exactement la même structure que pour un plugin ASTM ; il
est simplement exploité à un autre endroit du traitement. Voir chapitre
[05](05-configuration-setting-et-mapping.md#le-fichier-de-correspondances).

## Connexion et reconnexion

`listenDevice()` (ligne 415) suit la même logique que son homologue ASTM, avec deux
différences.

**Le garde-fou de réentrance** est explicite dès l'entrée :

```java
if (this.listening.get()) {
    logger.info("DEBUG: listenDevice() already running, ignoring call");
    return;
}
```

Sans lui, un rechargement de configuration mal séquencé lancerait deux fils d'écoute sur le
même port.

**La reconnexion à intervalle croissant vaut pour les deux modes**, pas seulement le mode
client : 5 s au départ, doublement à chaque échec, plafond à 60 s, remise à 5 s dès qu'une
connexion aboutit.

En mode client, la socket est ouverte avec un délai de lecture de 15 s et une gestion
soigneuse de l'échec :

```java
Socket s = new Socket(ip_analyzer, port_analyzer);
boolean ok = false;
try {
    s.setSoTimeout(15000);
    inputStream = s.getInputStream();
    outputStream = s.getOutputStream();
    socket = s;
    ok = true;
} finally {
    if (!ok) { try { s.close(); } catch (Exception ignore) {} }
}
```

Le drapeau `ok` évite de laisser une socket ouverte si l'initialisation échoue à mi-chemin.

En mode serveur, `startHL7Server()` (ligne 536) pose `setReuseAddress(true)` et un délai
d'acceptation de 2 s, ce qui permet à la boucle de retester régulièrement le drapeau d'arrêt
au lieu de rester bloquée indéfiniment sur `accept()`.

Le mode serveur reste recommandé en production ; le mode client est prévu pour les automates
incapables d'initier la connexion.

## Ce que ce plugin ne fait pas

Trois limites, assumées et documentées :

- **Aucune validation applicative HL7.** La validation de l'analyseur syntaxique est
  désactivée.
- **Aucune reprise sur acquittement négatif applicatif.** Si le LIS ou l'automate renvoie
  un accusé de réception négatif, il est tracé, pas rejoué.
- **Le mode client dépend du comportement réseau de l'automate.**

Ces limites sont raisonnables pour un composant de passage : rejouer automatiquement un
message de résultat pourrait produire des doublons dans le dossier du patient. La reprise
est une décision qui appartient au LIS, qui seul connaît l'état du dossier.
