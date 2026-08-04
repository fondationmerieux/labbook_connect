# 06 — Écrire un plugin ASTM

Ce chapitre s'appuie sur le plugin GeneXpert
(`labbook_connect_plugin_genexpert/src/plugin/AnalyzerGeneXpert.java`). C'est l'archétype
du plugin pour un automate qui **ne parle pas HL7** : tout est à convertir, et la couche de
transport est à écrire entièrement.

Les numéros de ligne cités correspondent au **plugin GeneXpert 1.0.14** (voir
[Versions décrites](README.md#versions-décrites)) ; les **noms de méthodes**, eux, se
retrouvent d'un plugin à l'autre et survivent aux changements de version. Si un numéro ne
tombe pas juste, cherchez la méthode par son nom.

## ASTM n'est pas un format, c'est un protocole

C'est le point de départ, et il change tout.

HL7 v2 est un **format de message**. Le transport est assuré par autre chose (MLLP, HTTP),
et une bibliothèque comme HAPI fait le reste. On reçoit un message, on le traite, on répond.

ASTM E1381 est un **protocole de communication complet**. Il décrit comment deux
équipements établissent le dialogue, s'échangent le droit de parole, découpent les données
en trames, vérifient leur intégrité et réagissent aux erreurs. Le contenu des messages est,
lui, décrit par une autre norme (E1394).

Conséquence : un plugin ASTM est nettement plus gros qu'un plugin HL7, et l'essentiel du
surcroît est du transport, pas du métier.

### Le dialogue

ASTM fonctionne en **semi-duplex** : à un instant donné, un seul équipement a le droit de
parler, l'autre écoute, puis les rôles s'inversent. Le contrôle passe par des caractères
spéciaux :

```java
private static final byte ENQ = 0x05;  // demande à parler
private static final byte ACK = 0x06;  // d'accord / bien reçu
private static final byte NAK = 0x15;  // refusé / mal reçu
private static final byte EOT = 0x04;  // fin de transmission
private static final byte STX = 0x02;  // début de trame
private static final byte ETX = 0x03;  // fin de trame (dernière)
private static final byte ETB = 0x17;  // fin de trame (d'autres suivent)
private static final byte CR  = 0x0D;
private static final byte LF  = 0x0A;
```

Un échange complet ressemble à ceci :

```text
Automate                          Connect
   |  ENQ  ------------------------>  |   « puis-je parler ? »
   |  <----------------------- ACK    |   « vas-y »
   |  STX 1 H|...  ETX CS CR LF --->  |   trame 1
   |  <----------------------- ACK    |   checksum correct
   |  STX 2 P|...  ETX CS CR LF --->  |   trame 2
   |  <----------------------- ACK    |
   |  ...                             |
   |  EOT  ------------------------>  |   « j'ai fini »
```

Un `NAK` peut survenir à deux moments et n'a pas le même sens :

- en réponse à un `ENQ`, il signifie « je suis occupé, pas maintenant » ;
- en réponse à une trame, il signifie « checksum incorrect, renvoie-la ».

### La structure d'une trame

```text
STX | n° de trame | données | ETX ou ETB | checksum (2 caractères) | CR | LF
```

Le numéro de trame cycle de 0 à 7. Le checksum est la somme des octets — numéro de trame
et terminateur compris — tronquée à un octet et écrite en hexadécimal sur deux caractères
majuscules.

À l'émission (`sendASTMMessage`, ligne 1085) :

```java
String body = ((i + 1) % 8) + lines[i];
byte[] bodyBytes = body.getBytes(StandardCharsets.US_ASCII);

int checksum = 0;
for (byte b : bodyBytes) checksum += (b & 0xFF);
checksum += ETX;
checksum &= 0xFF;
String checksumStr = String.format("%02X", checksum);
```

À la réception, le même calcul est refait et comparé (lignes 1434-1452). En cas d'écart, on
répond `NAK` et **on n'ajoute pas la trame au message en cours d'assemblage** — l'émetteur
va la renvoyer à l'identique.

Ce calcul n'a rien de propre à un constructeur : c'est la norme. La documentation du
constructeur le rappelle, et il est identique chez la plupart des automates ASTM.

> **Piège — un enregistrement peut être coupé entre deux trames.** Le terminateur `ETB`
> signifie exactement cela. Quand vous assemblez les trames, **n'insérez aucun séparateur**
> entre elles : les données portent déjà leurs `CR` entre enregistrements, et en ajouter un
> coupe en deux un enregistrement qui continuait dans la trame suivante.
>
> ```java
> assembledMessage.write(payloadBytes);   // et rien d'autre
> ```

## La machine à états

`listenForIncomingMessages()` (ligne 1362) implémente le dialogue sous forme de machine à
états : à chaque instant, le plugin sait ce qu'il attend, et un même octet reçu ne provoque
pas la même action selon l'état courant.

Les étapes, dans l'ordre du code :

1. **Attendre `ENQ`**, avec un délai de lecture de 15 s. Tout autre octet est tracé et
   ignoré. Un flux fermé par le pair met fin à l'écoute.
2. **Répondre `ACK`.**
3. **Boucler sur les trames** jusqu'à `EOT` : lire `STX`, le numéro de trame, les données
   jusqu'à `ETX` ou `ETB`, puis le checksum, `CR` et `LF`. Vérifier, acquitter, assembler.
4. **Reconstituer le message** en normalisant les `CRLF` en `CR`.
5. **Router** vers LAB-27 ou LAB-29, et si une réponse est produite, la renvoyer à
   l'automate par un échange ASTM complet (`sendASTMMessage`).

Le délai de lecture qui expire n'est **pas** une erreur : on continue simplement d'attendre.

```java
} catch (SocketTimeoutException timeoutEx) {
    logger.warn("No data received within 15000 ms — continuing to wait...");
    continue;
}
```

Une erreur d'entrée-sortie, en revanche, est fatale pour cette socket : l'écoute s'arrête et
la logique de reconnexion prend le relais.

### Le routage

`processAnalyzerMsg()` (ligne 1513) décide de la transaction d'après le **type des
enregistrements** présents :

```java
boolean hasH = Arrays.stream(lines).anyMatch(l -> l.matches("^\\d*H\\|.*"));
boolean hasQ = Arrays.stream(lines).anyMatch(l -> l.matches("^\\d*Q\\|.*"));

if (hasQ)      return lab27(receivedMessage);   // Q| = requête
else if (hasH) return lab29(receivedMessage);   // H| = message de données
else           return null;                     // ignoré
```

Le `\\d*` en tête des expressions régulières absorbe le numéro de trame, qui peut rester
collé au type d'enregistrement selon la façon dont le message a été assemblé.

L'ordre compte : un message de requête porte **aussi** un enregistrement `H|`, puisque tout
message ASTM commence par un en-tête. Tester `Q|` d'abord est donc indispensable.

## Serveur ou client

`listenDevice()` (ligne 1192) commence par charger le mapping, vérifie le type de connexion,
puis lance un fil d'exécution dédié.

**En mode serveur** — le cas éprouvé — `startASTMServer()` ouvre une `ServerSocket` sur le
port configuré et accepte les connexions entrantes.

**En mode client** — expérimental — le plugin ouvre lui-même la connexion vers l'automate,
avec une reconnexion à intervalle croissant :

```java
int backoffDelayMs = 5000;        // première attente : 5 s
final int backoffMaxMs = 60000;   // plafond : 60 s
...
backoffDelayMs = Math.min(backoffDelayMs * 2, backoffMaxMs);
```

Le délai est remis à 5 s dès qu'une connexion réussit. L'intérêt de cette progression est
simple : inutile de solliciter le réseau toutes les cinq secondes si l'automate est éteint
pour la semaine ; mais inutile non plus d'attendre des heures s'il redémarre.

Le fil d'exécution est marqué *daemon* et nommé, ce qui aide au diagnostic.

**Pourquoi un fil par instance ?** Parce que l'écoute d'un automate est permanente : elle
doit durer des jours sans bloquer le reste. Tant qu'aucune donnée n'arrive, le fil reste
bloqué en lecture sur la socket et ne consomme pratiquement rien.

### Arrêter proprement

`stopListening()` (ligne 1593) fait exactement trois choses : lever le drapeau d'arrêt,
fermer la socket cliente, fermer la socket serveur — chacune dans son `try` avec remise à
`null` dans le `finally`.

```java
this.listening.set(false);
// fermer socket, inputStream, outputStream
// fermer serverSocket
```

C'est court, et c'est critique. `AnalyzerLoader` appelle `stopListening()` puis
`listenDevice()` à chaque rechargement de configuration. Une socket serveur oubliée laisse
le port occupé, et le redémarrage échoue jusqu'au prochain redémarrage du conteneur.

## Les trois transactions

Toutes suivent le même squelette, ce qui rend le code lisible malgré sa taille :

```text
archiver le message brut
  → découper / nettoyer pour les traces
  → convertir vers le format cible
  → (si LAB-27 ou LAB-29) envoyer au LIS et attendre la réponse
  → convertir la réponse vers le format de l'automate
  → rendre la réponse à la couche de communication
```

Aucune de ces méthodes ne touche au réseau vers l'automate. Elles **produisent** une
réponse ; c'est la boucle d'écoute qui l'encapsule en trames, calcule les checksums, gère
les acquittements et l'envoie.

### LAB-27 — l'automate demande du travail

`lab27()` (ligne 223) :

```java
Connect_util.archiveMessage(this.getId_analyzer(), this.archive_msg, msg, "LAB-27", "Analyzer");
String[] astmLines = logAndSplitAstm(msg);
String qbpMsg = convertASTMQueryToQBP_Q11(astmLines);            // ASTM  -> HL7
String rspMsg = Connect_util.send_hl7_msg(this, this.url_upstream_lab27, qbpMsg);
String[] astmResponse = convertRSP_K11toASTM(rspMsg);            // HL7   -> ASTM
return String.join("\r", astmResponse);
```

Le plugin ignore totalement ce que fait le LIS de sa requête. Il envoie un `QBP^Q11`,
attend un `RSP^K11`, le retraduit.

La construction du message HL7 s'appuie sur les objets typés de HAPI
(`QBP_Q11`, `MSH`, `QPD`, `RCP`…) plutôt que sur de la concaténation de chaînes.

### LAB-28 — le LIS pousse une prescription

`lab28()` (ligne 271) est la seule appelée par Connect. Elle analyse l'`OML^O33` avec HAPI,
vérifie la présence des groupes `SPECIMEN` et `ORDER`, convertit en ASTM, envoie à
l'automate et construit un accusé de réception HL7 selon le résultat :

```java
String result = sendASTMMessage(astmLines);
String ackCode = "AA";                      // accepté
if (!"ACK".equals(result)) ackCode = "AE";  // erreur applicative
String hl7Ack = generateAckR22(str_OML_O33, ackCode);
```

> **Note sur la maturité de cette transaction.** Le mode diffusion n'a pas pu être validé
> sur cet automate : la documentation l'annonce, mais aucun essai n'a abouti. Le code est
> conservé parce qu'il est conforme à la norme et qu'il peut servir de base à d'autres
> plugins. En exploitation, cet automate fonctionne en mode `query`, où c'est lui qui prend
> l'initiative.

### LAB-29 — l'automate remonte des résultats

`lab29()` (ligne 344) est la transaction la plus utilisée :

```java
Connect_util.archiveMessage(..., "LAB-29", "Analyzer");
String[] astmLines = logAndSplitAstm(msg);
String hl7Message = convertASTMtoOUL_R22(astmLines);
if (hl7Message == null || hl7Message.isEmpty()) return "L|1|N";   // NAK ASTM

String hl7Ack = Connect_util.send_hl7_msg(this, this.url_upstream_lab29, hl7Message);
if (hl7Ack == null || !hl7Ack.startsWith("MSH|")) return "L|1|N";

return convertACKtoASTM(hl7Ack);
```

Notez le contrôle `startsWith("MSH|")` : `send_hl7_msg` ne lève pas d'exception, elle renvoie
une chaîne d'erreur. Sans ce contrôle, on tenterait de convertir un message d'erreur en
acquittement ASTM.

Une fois le message remis au LIS, le rôle du plugin s'arrête. C'est le LIS qui enregistre
les résultats, met à jour le dossier et les rend visibles.

## Les conversions et le mapping

Les conversions sont déléguées à des méthodes spécialisées, séparées des transactions. Si
un constructeur modifie légèrement son format, il n'y a qu'un convertisseur à reprendre.

| Méthode | Sens |
|---|---|
| `convertASTMQueryToQBP_Q11` | requête ASTM → HL7 |
| `convertRSP_K11toASTM` | réponse HL7 → ASTM |
| `convertOML_O33ToASTM` | prescription HL7 → ASTM |
| `convertASTMtoOUL_R22` | résultats ASTM → HL7 |
| `convertACKtoASTM` | acquittement HL7 → ASTM |

C'est **à l'intérieur des convertisseurs** que le mapping s'applique. Dans
`convertASTMtoOUL_R22`, deux moments :

**Sur l'enregistrement `O|`** — on résout l'analyse. Le code du constructeur est cherché
dans `[[ivd_test]]`, ce qui donne le nom interne (`currentTestName`, qui servira de clé pour
les résultats) et le code LIS (`currentLisTestCode`, qui part dans `OBR-4`) :

```java
for (Toml t : mappingToml.getTables("ivd_test")) {
    if (vendorTestCode.equals(t.getString("vendor_test_code"))) {
        currentTestName    = t.getString("name");
        currentLisTestCode = t.getString("lis_test_code");
        break;
    }
}
```

**Sur chaque enregistrement `R|`** — on résout le résultat. La recherche dans
`[[ivd_mapping]]` se fait sur **le couple** (`test`, `vendor_result_code`), puis on applique
l'unité et la conversion configurées :

```java
if (!lisUnit.isEmpty()) units = lisUnit;

if (!vtrim.isEmpty() && !"none".equalsIgnoreCase(convert)) {
    double num = Double.parseDouble(vtrim.replace(",", "."));
    if      ("multiply".equalsIgnoreCase(convert)) num = num * factor;
    else if ("divide".equalsIgnoreCase(convert))   { if (factor != 0.0) num = num / factor; }
    else if ("add".equalsIgnoreCase(convert))      num = num + factor;
    else if ("subtract".equalsIgnoreCase(convert)) num = num - factor;
    else if ("log10".equalsIgnoreCase(convert))    { if (num > 0.0) num = Math.log10(num); }
}
```

Quand aucune correspondance n'est trouvée, le code brut du constructeur est transmis tel
quel — c'est la panne silencieuse décrite au chapitre
[05](05-configuration-setting-et-mapping.md#quand-le-mapping-échoue).

L'identifiant d'échantillon relevé dans l'enregistrement `O|` est reporté dans `SPM-2`,
`ORC-2` et `OBR-2` : c'est par lui que le LIS retrouvera le dossier.

## Deux utilitaires à ne pas négliger

**`logAndSplitAstm`** (ligne 1733) nettoie le message avant de le tracer.

> **Piège — les traces tronquées.** Un message ASTM contient des `\r` seuls. Selon le
> terminal, un log qui les contient s'affiche sur moins de lignes qu'il n'en compte
> réellement : on croit que l'automate a envoyé un message incomplet alors qu'il est
> entier. Nettoyez systématiquement avant de tracer — dans tout le code, les traces
> utilisent `.replace("\r", "\n")`.

**`buildReplyHeader`** (ligne 1785) construit l'en-tête `H|` des réponses en **reprenant
celui du message reçu** : il en retire le numéro de trame, conserve les champs d'origine et
n'y remplace que les horodatages. La raison est que les séparateurs déclarés dans l'en-tête
varient d'un automate à l'autre (`H|\^&` chez l'un, `H|@^\` chez un autre). Réutiliser
l'en-tête reçu évite d'inscrire ces choix en dur. Un en-tête par défaut n'est fabriqué que
si aucun `H|` exploitable n'a été trouvé.

## Le vrai coût d'un plugin ASTM

Le code, on l'a vu, est presque mécanique. La difficulté est ailleurs.

Un automate ASTM est rarement bavard. Il acquitte volontiers un message **syntaxiquement**
correct — vous voyez passer les `ACK`, le dialogue se déroule parfaitement — et ne fait
rien du contenu, sans jamais dire pourquoi. Aucune erreur, aucun message, rien à l'écran de
l'automate. Un séparateur en trop ou en moins suffit.

Ce qui aide, dans l'ordre :

1. **Les exemples de la documentation constructeur.** Rejouez-les avec un script, cela
   valide votre lecture indépendamment de l'automate.
2. **Les messages bruts archivés.** Ce que l'automate envoie vraiment prime toujours sur ce
   que la documentation annonce.
3. **Quelqu'un qui a déjà écrit un pilote pour ce matériel**, même dans un autre langage :
   il connaît la structure exacte attendue.
4. **La vérification des versions.** Une documentation à jour face à un microcode ancien
   — ou l'inverse — explique bien des écarts inexplicables.

Prévoyez du temps. Le développement d'un plugin d'automate est rarement limité par la
difficulté du code ; il l'est par les allers-retours avec la machine.
