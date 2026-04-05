# Case 3 – Client-Applikation (Group 6)

## Übersicht

Diese Applikation ist ein **JMS-basierter Client** für ein Auftrags-Dispositionssystem. Der Client verbindet sich über **ActiveMQ** mit einem zentralen Message-Broker und kommuniziert mit der Dispo-Server-Applikation, um Aufträge (Jobs) zu empfangen, anzuzeigen und anzufordern.

Die Anwendung ist mit **Spring Boot 2.3.4** gebaut und verwendet eine **Swing-GUI**, um dem Benutzer offene und zugewiesene Aufträge in Echtzeit anzuzeigen.

---

## Architektur

```
┌─────────────────────────────────────────────────────────────┐
│                     ActiveMQ Broker                         │
│                  (tcp://192.168.111.6:61616)                │
│                                                             │
│  ┌───────────────────────────┐                              │
│  │  Topic                    │   Dispo-Server publiziert    │
│  │  group6.dispo.jobs.new    │──────neue Aufträge──────┐    │
│  └───────────────────────────┘                         │    │
│                                                        │    │
│  ┌────────────────────────────────────┐                │    │
│  │  Topic                             │   Dispo-Server │    │
│  │  group6.dispo.jobs.assignments     │──publiziert ───┤    │
│  └────────────────────────────────────┘  Zuweisungen   │    │
│                                                        │    │
│  ┌────────────────────────────────────────┐            │    │
│  │  Queue                                 │            │    │
│  │  group6.dispo.jobs.requestAssignment   │◄───────┐   │    │
│  └────────────────────────────────────────┘        │   │    │
└────────────────────────────────────────────────────┼───┼────┘
                                                     │   │
                                                     │   │
┌────────────────────────────────────────────────────┼───┼────┐
│                Client-Applikation (diese App)      │   │    │
│                                                    │   │    │
│  ┌──────────────────┐    empfängt Jobs & ──────────┘   │    │
│  │  MessageReceiver │    Zuweisungen von Topics   ◄────┘    │
│  └────────┬─────────┘                                       │
│           │ zeigt an                                        │
│           ▼                                                 │
│  ┌──────────────────┐                                       │
│  │       UI         │    Swing-GUI mit zwei Listen          │
│  │  (Swing JFrame)  │    • offene Jobs (links)              │
│  └────────┬─────────┘    • zugewiesene Jobs (rechts)        │
│           │ Button-Klick                                    │
│           ▼                                                 │
│  ┌──────────────────┐    sendet Anfrage ───────────────┐    │
│  │  MessageSender   │    an Queue                      │    │
│  └──────────────────┘                                  │    │
│                                                        │    │
└────────────────────────────────────────────────────────┼────┘
                                                         │
                                            geht an Dispo-Server
```

---

## Nachrichtenkanäle (Channels)

Die Kommunikation zwischen Client und Dispo-Server läuft über drei JMS-Destinations auf dem ActiveMQ-Broker. Dabei wird bewusst zwischen **Topics** und **Queues** unterschieden, da sie in JMS grundlegend unterschiedliche Zustellsemantiken haben.

### Topics (Publish/Subscribe – 1-zu-viele)

Ein Topic wurde gewählt, weil **alle** verbundenen Clients gleichzeitig über neue Aufträge und Zuweisungen informiert werden müssen. Jeder Subscriber erhält eine eigene Kopie jeder Nachricht. So ist sichergestellt, dass kein Client eine Information verpasst, sobald er verbunden ist.

| Destination | Richtung | Beschreibung |
|---|---|---|
| `group6.dispo.jobs.new` | Dispo-Server → Client(s) | Der Dispo-Server publiziert hier neue Aufträge. Jeder verbundene Client erhält die Nachricht und kann den Job in seiner GUI anzeigen. Dieses Topic unterstützt zusätzlich **Content-Based-Router-Sub-Topics** wie `group6.dispo.jobs.new.basel`, `group6.dispo.jobs.new.zuerich`, `group6.dispo.jobs.new.bern`, `group6.dispo.jobs.new.repair` oder `group6.dispo.jobs.new.maintenance`, über die der Broker bereits eine Vorfilterung vornehmen kann (siehe Abschnitt [Filterung](#filterung)). |
| `group6.dispo.jobs.assignments` | Dispo-Server → Client(s) | Der Dispo-Server publiziert hier Zuweisungsentscheidungen. Wenn ein Job vergeben oder eine Anfrage abgelehnt wurde, erfahren es alle Clients, damit sie den Job aus der offenen Liste entfernen und in der Zuweisungsliste anzeigen (oder eine Ablehnung notieren) können. |

### Queue (Point-to-Point – 1-zu-1)

Für die Job-Anfrage wird eine Queue verwendet, weil jede Anfrage **genau einmal** vom Dispo-Server verarbeitet werden soll. Eine Queue garantiert, dass jede Nachricht nur von einem einzigen Consumer gelesen wird – selbst bei mehreren laufenden Dispo-Server-Instanzen würde die Anfrage nicht doppelt bearbeitet.

| Destination | Richtung | Beschreibung |
|---|---|---|
| `group6.dispo.jobs.requestAssignment` | Client → Dispo-Server | Der Client sendet hier eine Anfrage, wenn ein Benutzer einen bestimmten Job anfordern möchte. Der Dispo-Server liest die Anfrage aus der Queue und entscheidet, ob der Job dem anfragenden Client zugewiesen wird. |

### Warum Topic für Empfang und Queue für Senden?

- **Topic für Empfang:** Mehrere Clients können gleichzeitig laufen. Jeder muss alle neuen Jobs und alle Zuweisungsentscheidungen erhalten. Ein Topic stellt genau das sicher – jede Nachricht wird an alle Subscriber ausgeliefert.
- **Queue für Senden:** Eine Job-Anfrage ist eine gerichtete Aktion eines einzelnen Clients an den Server. Sie darf nur einmal verarbeitet werden. Eine Queue stellt sicher, dass exakt ein Consumer die Nachricht empfängt und bearbeitet.

---

## Nachrichtentypen

Die Applikation verwendet drei Nachrichtentypen, die als **JSON** über den Broker übertragen werden. Die Serialisierung und Deserialisierung erfolgt automatisch durch den `MappingJackson2MessageConverter` von Spring.

### `JobMessage` – Ein neuer Auftrag

Wird vom Dispo-Server auf das Topic `group6.dispo.jobs.new` (oder ein Sub-Topic) publiziert.

| Feld | Typ | Beschreibung |
|---|---|---|
| `jobId` | String | Eindeutige ID des Auftrags |
| `description` | String | Beschreibung des Auftrags (z. B. „Heizung defekt") |
| `region` | String | Region des Auftrags (z. B. `"basel"`, `"zuerich"`, `"bern"`) |
| `jobType` | String | Art des Auftrags: `"repair"` (Reparatur) oder `"maintenance"` (Wartung) |

### `JobRequestMessage` – Anfrage auf einen Auftrag

Wird vom Client an die Queue `group6.dispo.jobs.requestAssignment` gesendet, wenn ein Benutzer auf „Selektierten Job anfordern" klickt.

| Feld | Typ | Beschreibung |
|---|---|---|
| `jobId` | String | ID des gewünschten Auftrags |
| `clientId` | String | ID des anfragenden Clients (z. B. `"group6_ClientAppStub"`) |

### `JobAssignmentMessage` – Zuweisungsentscheidung

Wird vom Dispo-Server auf das Topic `group6.dispo.jobs.assignments` publiziert, nachdem er eine Anfrage verarbeitet hat.

| Feld | Typ | Beschreibung |
|---|---|---|
| `jobId` | String | ID des betroffenen Auftrags |
| `clientId` | String | ID des Clients, dem der Job zugewiesen wurde (oder der abgelehnt wurde) |
| `assigned` | boolean | `true` = Job wurde erfolgreich zugewiesen, `false` = Anfrage wurde abgelehnt |

---

## Nachrichtenfluss (End-to-End)

Der typische Ablauf einer Auftragsvergabe funktioniert in folgenden Schritten:

1. **Dispo-Server publiziert einen neuen Job** auf das Topic `group6.dispo.jobs.new` (oder ein regionsspezifisches/typspezifisches Sub-Topic).
2. **`MessageReceiver` empfängt den Job** über den `@JmsListener` und prüft optional die konfigurierten Filter (Region und/oder JobType). Passt der Job zu den Filterkriterien (oder sind keine Filter gesetzt), wird er an die GUI weitergereicht.
3. **`UI` zeigt den Job** in der linken Liste („offene Jobs") an.
4. **Benutzer wählt einen Job aus** und klickt den Button „Selektierten Job anfordern".
5. **`MessageSender` erstellt eine `JobRequestMessage`** mit der Job-ID und der eigenen Client-ID und sendet sie über `JmsTemplate` an die Queue `group6.dispo.jobs.requestAssignment`. Dabei wird `setPubSubDomain(false)` gesetzt, damit die Nachricht als Queue-Nachricht (nicht als Topic-Nachricht) gesendet wird.
6. **Dispo-Server empfängt die Anfrage** aus der Queue, entscheidet über die Zuweisung und publiziert eine `JobAssignmentMessage` auf das Topic `group6.dispo.jobs.assignments`.
7. **`MessageReceiver` empfängt die Zuweisung** und leitet sie an die GUI weiter.
8. **`UI` aktualisiert die Anzeige:**
   - Bei `assigned = true`: Der Job wird aus der offenen Liste entfernt und in der rechten Liste („zugewiesene Jobs") als zugewiesen angezeigt.
   - Bei `assigned = false`: Der Job bleibt in der offenen Liste. In der rechten Liste wird ein Ablehnungshinweis angezeigt.

---

## Serialisierung & Type-ID-Mapping

### Das Problem

Die Nachrichten werden als **JSON-Text** über JMS übertragen. Der `MappingJackson2MessageConverter` von Spring serialisiert Java-Objekte zu JSON und setzt dabei einen `_type`-Header in die JMS-Nachricht. Der Empfänger liest diesen Header, um zu wissen, in welche Java-Klasse das JSON zurück deserialisiert werden soll.

Die **Dispo-Server-Applikation** verwendet allerdings ein anderes Package als der Client:

| Dispo-Server (Sender) | Client-App (Empfänger) |
|---|---|
| `ch.fhnw.digi.demo.JobMessage` | `ch.fhnw.digi.mockups.case3.JobMessage` |
| `ch.fhnw.digi.demo.JobAssignmentMessage` | `ch.fhnw.digi.mockups.case3.JobAssignmentMessage` |
| `ch.fhnw.digi.demo.JobRequestMessage` | `ch.fhnw.digi.mockups.case3.JobRequestMessage` |

Wenn der Client eine Nachricht vom Server empfängt, steht im `_type`-Header z. B. `ch.fhnw.digi.demo.JobMessage`. Der Standard-Converter versucht dann, genau diese Klasse per Reflection zu laden – sie existiert aber nicht im Client-Package. Die Folge ist eine `ClassNotFoundException`.

### Die Lösung: Type-ID-Mappings

Im `jacksonJmsMessageConverter()` wird ein explizites **Mapping** konfiguriert, das den Typ-String des Senders auf die lokalen Klassen abbildet:

```java
Map<String, Class<?>> typeIdMappings = new HashMap<>();
typeIdMappings.put("ch.fhnw.digi.demo.JobMessage", JobMessage.class);
typeIdMappings.put("ch.fhnw.digi.demo.JobAssignmentMessage", JobAssignmentMessage.class);
typeIdMappings.put("ch.fhnw.digi.demo.JobRequestMessage", JobRequestMessage.class);
converter.setTypeIdMappings(typeIdMappings);
```

Dieses Mapping funktioniert **bidirektional**:

- **Beim Empfangen:** Der Converter liest `_type = "ch.fhnw.digi.demo.JobMessage"` aus dem Header und weiss durch das Mapping, dass er die lokale Klasse `ch.fhnw.digi.mockups.case3.JobMessage` verwenden soll. So wird die `ClassNotFoundException` verhindert.
- **Beim Senden:** Der Converter schaut, welcher Mapping-Key zu `JobRequestMessage.class` gehört, und setzt `_type = "ch.fhnw.digi.demo.JobRequestMessage"` in den Header – genau das, was der Dispo-Server erwartet.

Ohne dieses Mapping würde der Client beim Empfang jeder Nachricht vom Server mit einer Exception abstürzen, da die Server-Packages nicht im Client-Classpath existieren.

---

## Filterung

Der Client unterstützt zwei Filtervarianten, um nur relevante Aufträge anzuzeigen. Die Wahl der Variante hängt davon ab, ob die Filterung bereits auf Broker-Ebene (Content-Based Router) oder erst im Client geschehen soll.

### Variante 1: Content-Based Router (Broker-seitig)

Der Dispo-Server publiziert Aufträge nicht nur auf dem allgemeinen Topic `group6.dispo.jobs.new`, sondern zusätzlich auf **spezialisierte Sub-Topics**, die nach Region oder JobType aufgeteilt sind:

| Sub-Topic | Inhalt |
|---|---|
| `group6.dispo.jobs.new.basel` | Nur Aufträge aus der Region Basel |
| `group6.dispo.jobs.new.zuerich` | Nur Aufträge aus der Region Zuerich |
| `group6.dispo.jobs.new.bern` | Nur Aufträge aus der Region Bern |
| `group6.dispo.jobs.new.repair` | Nur Reparatur-Aufträge |
| `group6.dispo.jobs.new.maintenance` | Nur Wartungs-Aufträge |

Durch die Wahl des Sub-Topics in der Property `channel.topic.newJobs` wird die Filterung bereits vom Broker übernommen – der Client empfängt nur Nachrichten, die auf dieses Topic publiziert wurden. Das reduziert den Netzwerk-Traffic und die Last im Client.

```properties
# Nur Aufträge aus Basel empfangen (Broker-seitig gefiltert):
channel.topic.newJobs=group6.dispo.jobs.new.basel
```

> **Wichtig:** Wenn ein Sub-Topic gewählt wird, darf der entsprechende client-seitige Filter (`client.region` bzw. `client.jobType`) nicht widersprüchlich gesetzt werden. Beispiel: `channel.topic.newJobs=group6.dispo.jobs.new.basel` und `client.region=zuerich` würde dazu führen, dass **keine** Aufträge angezeigt werden, da der Broker nur Basel-Aufträge liefert, der Client-Filter aber nur Zuerich durchlässt.

### Variante 2: Client-seitiger Filter

Bei dieser Variante wird das allgemeine Topic `group6.dispo.jobs.new` abonniert (der Client empfängt also alle Aufträge), und die Filterung wird im `MessageReceiver` in Software durchgeführt:

#### Region-Filter (`client.region`)

Zeigt nur Aufträge aus bestimmten Regionen an. Mehrere Regionen können kommagetrennt angegeben werden. Wenn der Wert leer gelassen wird, werden Aufträge aus allen Regionen angezeigt.

```properties
# Nur Aufträge aus Basel:
client.region=basel

# Aufträge aus Basel und Bern:
client.region=basel,bern

# Alle Regionen (kein Filter):
client.region=
```

Die Filterlogik im `MessageReceiver` splittet den Wert am Komma und vergleicht jede Region **case-insensitive** mit der Region des eingehenden Jobs. Sobald eine Region übereinstimmt, wird der Job angezeigt.

#### JobType-Filter (`client.jobType`)

Zeigt nur Aufträge eines bestimmten Typs an (`repair` oder `maintenance`). Wenn leer, werden alle Typen angezeigt.

```properties
# Nur Reparatur-Aufträge:
client.jobType=repair

# Alle Typen (kein Filter):
client.jobType=
```

Auch dieser Vergleich erfolgt case-insensitive.

### Kombination beider Varianten

Beide Varianten können **kombiniert** werden. Beispiel: Das Sub-Topic `group6.dispo.jobs.new.basel` abonnieren (Broker liefert nur Basel-Aufträge), und zusätzlich `client.jobType=repair` setzen (Client filtert nur Reparaturen heraus). So erhält man nur Basel-Reparaturen.

---

## Durable Subscriber (auskommentiert)

Im Code der `MessageReceiver`-Klasse ist ein Block auskommentiert, der einen **Durable Subscriber** konfigurieren würde:

```java
// factory.setSubscriptionDurable(true);
// factory.setClientId(clientId);
// factory.setSubscriptionShared(true);
```

Ein Durable Subscriber bewirkt, dass der Broker Nachrichten **auch dann speichert**, wenn der Client gerade offline ist. Sobald der Client sich wieder verbindet, werden die verpassten Nachrichten nachgeliefert.

Dieser Mechanismus wurde bewusst **nicht aktiviert**, da er im aktuellen Setup zu Problemen führt: Bei aktivem Durable Subscriber werden Nachrichten im Client nicht korrekt angezeigt. Das liegt daran, dass die ActiveMQ-Broker-Konfiguration und die Client-ID-Vergabe dafür speziell aufeinander abgestimmt sein müssen. Da im Rahmen dieses Projekts keine Kontrolle über die Broker-Konfiguration besteht und der Use-Case (Echtzeitanzeige offener Jobs) keine Offline-Nachlieferung erfordert, wurde auf Durable Subscriptions verzichtet.

---

## Konfiguration (`application.properties`)

```properties
# ActiveMQ-Broker-Verbindung
spring.activemq.broker-url=tcp://192.168.111.6:61616
spring.activemq.user=group6
spring.activemq.password=oi374bU0njDlI6m

# Eindeutige Client-ID (wird beim Anfordern von Jobs mitgeschickt)
client.id=group6_ClientAppStub

# Channel-Konfiguration
channel.queue.requestAssignment=group6.dispo.jobs.requestAssignment
channel.topic.assignments=group6.dispo.jobs.assignments

# Topic für neue Jobs (Sub-Topics möglich, z.B. group6.dispo.jobs.new.basel)
channel.topic.newJobs=group6.dispo.jobs.new

# Optionale client-seitige Filter (leer = kein Filter)
client.region=
client.jobType=
```

| Property | Beschreibung | Default |
|---|---|---|
| `spring.activemq.broker-url` | URL des ActiveMQ-Brokers | – |
| `spring.activemq.user` | Benutzername für die Broker-Authentifizierung | – |
| `spring.activemq.password` | Passwort für die Broker-Authentifizierung | – |
| `client.id` | Eindeutige Kennung dieses Clients (wird in Job-Anfragen mitgesendet) | `group6` |
| `channel.topic.newJobs` | Topic, das für neue Jobs abonniert wird. Kann ein Sub-Topic sein, um Broker-seitig zu filtern. | `group6.dispo.jobs.new` |
| `channel.topic.assignments` | Topic für Zuweisungsentscheidungen | `group6.dispo.jobs.assignments` |
| `channel.queue.requestAssignment` | Queue, an die Job-Anfragen gesendet werden | `group6.dispo.jobs.requestAssignment` |
| `client.region` | Client-seitiger Region-Filter (kommagetrennt, leer = alle) | leer |
| `client.jobType` | Client-seitiger JobType-Filter (leer = alle) | leer |

---

## Projektstruktur

```
src/main/java/ch/fhnw/digi/mockups/case3/
│
├── JobMessage.java              Datenmodell: Neuer Auftrag
├── JobRequestMessage.java       Datenmodell: Auftragsanfrage
├── JobAssignmentMessage.java    Datenmodell: Zuweisungsentscheidung
│
├── client/
│   ├── Case3ClientAppApplication.java   Spring Boot Einstiegspunkt
│   ├── MessageReceiver.java             JMS-Listener, Converter & Factory
│   ├── MessageSender.java               JMS-Nachrichtenversand
│   └── UI.java                          Swing-GUI (JFrame)
│
└── dispo/                               (leer – Platzhalter fuer Server-Stub)
    ├── DispoApplication.java
    ├── DispoMessageReceiver.java
    ├── DispoMessageSender.java
    └── DispoUI.java
```

### Komponentenübersicht

| Klasse | Verantwortung |
|---|---|
| **`Case3ClientAppApplication`** | Startet die Spring Boot Applikation mit `headless(false)`, damit die Swing-GUI angezeigt werden kann. Ohne `headless(false)` würde Spring Boot im Server-Modus starten und keine GUI rendern. |
| **`MessageReceiver`** | Empfängt Nachrichten von den beiden Topics (`newJobs` und `assignments`), wendet die konfigurierten Filter an und leitet relevante Nachrichten an die `UI`-Komponente weiter. Definiert ausserdem die Beans `myFactory` (JMS-`ListenerContainerFactory` mit Topic-Modus) und `jacksonJmsMessageConverter` (Jackson-basierter Message-Converter mit Type-ID-Mapping). |
| **`MessageSender`** | Wird von der `UI` aufgerufen, wenn der Benutzer einen Job anfordert. Erstellt eine `JobRequestMessage` mit Job-ID und Client-ID und sendet sie über `JmsTemplate` an die Request-Queue des Dispo-Servers. Setzt dabei `setPubSubDomain(false)`, um im Queue-Modus zu senden. |
| **`UI`** | Swing-basierte Oberfläche mit zwei Listen: offene Jobs (links) und zugewiesene Jobs (rechts). Ein Button ermöglicht es, den ausgewählten Job anzufordern. GUI-Updates erfolgen thread-sicher über `SwingUtilities.invokeLater()`, da JMS-Nachrichten auf Hintergrund-Threads ankommen, Swing aber nur vom Event Dispatch Thread (EDT) verändert werden darf. |

---

## Technische Details

### JMS-Konfiguration (ListenerContainerFactory)

Die `myFactory`-Bean in `MessageReceiver` erstellt eine `DefaultJmsListenerContainerFactory` mit folgenden Einstellungen:

- **`setPubSubDomain(true)`** – Die Listener arbeiten im Topic-Modus (Publish/Subscribe), da beide Empfangs-Destinations Topics sind und jeder Client seine eigene Kopie jeder Nachricht erhalten soll.
- **`setMessageConverter(...)`** – Der Jackson-basierte Converter wird gesetzt, damit JSON-Nachrichten automatisch in Java-Objekte deserialisiert werden. Das Type-ID-Mapping sorgt dafür, dass die Server-seitigen Klassennamen auf die lokalen Klassen abgebildet werden.

### Senden vs. Empfangen: Topic vs. Queue

| | Empfangen (Receiver) | Senden (Sender) |
|---|---|---|
| **Modus** | Topic (Pub/Sub) | Queue (Point-to-Point) |
| **Einstellung** | `factory.setPubSubDomain(true)` | `jmsTemplate.setPubSubDomain(false)` |
| **Grund** | Alle Clients sollen neue Jobs und Zuweisungen gleichzeitig erhalten | Jede Job-Anfrage soll genau einmal vom Server verarbeitet werden |

### Thread-Sicherheit in der GUI

JMS-Nachrichten werden auf Hintergrund-Threads des JMS-Listener-Containers empfangen. Die Swing-GUI darf aber nur vom **Event Dispatch Thread (EDT)** verändert werden. Deshalb verwenden die Methoden `addJobToList()` und `assignJob()` in der `UI`-Klasse `SwingUtilities.invokeLater()`, um die GUI-Updates auf den EDT zu verlagern. Zusätzlich werden die `DefaultListModel`-Instanzen mit `synchronized`-Blöcken geschützt, um Race Conditions bei gleichzeitigen Nachrichten zu vermeiden.

### Channel-Namen als Properties

Die Destination-Namen (Topics und Queue) sind nicht hart im Code verdrahtet, sondern werden über die `application.properties` konfiguriert und per `@Value`-Annotation injiziert. Das erlaubt es, die Channel-Konfiguration zu ändern (z. B. ein Sub-Topic für Content-Based Routing zu wählen), ohne den Code anpassen zu müssen.

---

## Voraussetzungen

- **Java 11** oder höher
- **Maven** zum Bauen
- **ActiveMQ-Broker** erreichbar unter der konfigurierten URL (`tcp://192.168.111.6:61616`)
- **Dispo-Server-Applikation** muss laufen und Nachrichten publizieren

## Starten

```bash
mvn spring-boot:run
```

Oder in der IDE: `Case3ClientAppApplication.main()` ausführen.

Nach dem Start öffnet sich automatisch ein Swing-Fenster. Sobald der Dispo-Server Aufträge publiziert, erscheinen diese (je nach konfigurierten Filtern) in der linken Liste. Durch Auswählen eines Jobs und Klick auf „Selektierten Job anfordern" wird eine Anfrage an den Server gesendet. Die Zuweisungsentscheidung erscheint anschliessend in der rechten Liste.
