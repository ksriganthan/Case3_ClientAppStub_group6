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

Die Kommunikation zwischen Client und Dispo-Server läuft über drei JMS-Destinations auf dem ActiveMQ-Broker. Dabei wird bewusst zwischen **Topics** und **Queues** unterschieden, weil sie in JMS grundlegend unterschiedlich funktionieren:

### Topics (Publish/Subscribe – 1-zu-viele)

Bei einem Topic empfangen **alle** verbundenen Subscriber eine Kopie jeder Nachricht. Das ist sinnvoll, weil mehrere Clients gleichzeitig über neue Aufträge und Zuweisungen informiert werden sollen.

| Destination | Richtung | Beschreibung |
|---|---|---|
| `group6.dispo.jobs.new` | Broker → Client | Der Dispo-Server publiziert hier neue Aufträge. Jeder verbundene Client erhält die Nachricht und kann den Job in seiner GUI anzeigen. |
| `group6.dispo.jobs.assignments` | Broker → Client | Der Dispo-Server publiziert hier Zuweisungsentscheidungen. Wenn ein Job vergeben wurde, erfahren es alle Clients, damit sie den Job aus der offenen Liste entfernen und in der Zuweisungsliste anzeigen können. |

### Queue (Point-to-Point – 1-zu-1)

Bei einer Queue wird jede Nachricht nur von **einem einzigen** Consumer verarbeitet. Das ist wichtig für Job-Anfragen, weil jede Anfrage genau einmal vom Dispo-Server bearbeitet werden soll – nicht mehrfach.

| Destination | Richtung | Beschreibung |
|---|---|---|
| `group6.dispo.jobs.requestAssignment` | Client → Broker | Der Client sendet hier eine Anfrage, wenn ein Benutzer einen bestimmten Job anfordern möchte. Der Dispo-Server liest die Anfrage aus der Queue und entscheidet, ob der Job zugewiesen wird. |

---

## Nachrichtentypen

Die Applikation verwendet drei Nachrichtentypen, die als JSON über den Broker übertragen werden:

### `JobMessage` – Ein neuer Auftrag

Wird vom Dispo-Server auf das Topic `group6.dispo.jobs.new` publiziert.

| Feld | Typ | Beschreibung |
|---|---|---|
| `jobId` | String | Eindeutige ID des Auftrags |
| `description` | String | Beschreibung des Auftrags |
| `region` | String | Region des Auftrags (z.B. `"basel"`, `"zürich"`, `"bern"`) |
| `jobType` | String | Art des Auftrags: `"repair"` (Reparatur) oder `"maintenance"` (Wartung) |

### `JobRequestMessage` – Anfrage auf einen Auftrag

Wird vom Client an die Queue `group6.dispo.jobs.requestAssignment` gesendet.

| Feld | Typ | Beschreibung |
|---|---|---|
| `jobId` | String | ID des gewünschten Auftrags |
| `clientId` | String | ID des anfragenden Clients (z.B. `"group6_ClientAppStub"`) |

### `JobAssignmentMessage` – Zuweisungsentscheidung

Wird vom Dispo-Server auf das Topic `group6.dispo.jobs.assignments` publiziert.

| Feld | Typ | Beschreibung |
|---|---|---|
| `jobId` | String | ID des betroffenen Auftrags |
| `clientId` | String | ID des Clients, dem der Job zugewiesen wurde |
| `assigned` | boolean | `true` = Job wurde zugewiesen, `false` = Anfrage abgelehnt |

---

## Nachrichtenfluss (End-to-End)

Der typische Ablauf einer Auftragsvergabe funktioniert so:

1. **Dispo-Server publiziert einen neuen Job** auf das Topic `group6.dispo.jobs.new`.
2. **MessageReceiver empfängt den Job** und prüft die optionalen Filter (Region und JobType). Passt der Job zu den Filterkriterien, wird er an die GUI weitergegeben.
3. **UI zeigt den Job** in der linken Liste ("offene Jobs") an.
4. **Benutzer wählt einen Job aus** und klickt den Button "Selektierten Job anfordern".
5. **MessageSender erstellt eine `JobRequestMessage`** mit der Job-ID und der eigenen Client-ID und sendet sie an die Queue `group6.dispo.jobs.requestAssignment`.
6. **Dispo-Server empfängt die Anfrage**, entscheidet über die Zuweisung und publiziert eine `JobAssignmentMessage` auf das Topic `group6.dispo.jobs.assignments`.
7. **MessageReceiver empfängt die Zuweisung** und gibt sie an die GUI weiter.
8. **UI entfernt den Job** aus der offenen Liste und zeigt ihn in der rechten Liste ("zugewiesene Jobs") an.

---

## Serialisierung & Type-ID-Mapping

### Das Problem

Die Nachrichten werden als **JSON-Text** über JMS übertragen. Der `MappingJackson2MessageConverter` von Spring serialisiert Java-Objekte zu JSON und setzt dabei einen `_type`-Header in die JMS-Nachricht, damit der Empfänger weiss, in welche Java-Klasse das JSON zurück deserialisiert werden soll.

Die **Dispo-Server-Applikation** verwendet ein anderes Package als unser Client:

| Dispo-Server (Sender) | Client-App (Empfänger) |
|---|---|
| `ch.fhnw.digi.demo.JobMessage` | `ch.fhnw.digi.mockups.case3.JobMessage` |
| `ch.fhnw.digi.demo.JobAssignmentMessage` | `ch.fhnw.digi.mockups.case3.JobAssignmentMessage` |
| `ch.fhnw.digi.demo.JobRequestMessage` | `ch.fhnw.digi.mockups.case3.JobRequestMessage` |

Wenn der Client eine Nachricht vom Server empfängt, steht im `_type`-Header z.B. `ch.fhnw.digi.demo.JobMessage`. Der Client versucht dann, diese Klasse zu laden – die existiert aber nicht in unserem Package → `ClassNotFoundException`.

### Die Lösung: Type-ID-Mappings

Im `jacksonJmsMessageConverter()` konfigurieren wir ein explizites **Mapping**, das den Typ-String des Senders auf unsere lokalen Klassen abbildet:

```java
Map<String, Class<?>> typeIdMappings = new HashMap<>();
typeIdMappings.put("ch.fhnw.digi.demo.JobMessage", JobMessage.class);
typeIdMappings.put("ch.fhnw.digi.demo.JobAssignmentMessage", JobAssignmentMessage.class);
typeIdMappings.put("ch.fhnw.digi.demo.JobRequestMessage", JobRequestMessage.class);
converter.setTypeIdMappings(typeIdMappings);
```

Dieses Mapping funktioniert **bidirektional**:
- **Beim Empfangen:** Der Converter liest `_type = "ch.fhnw.digi.demo.JobMessage"` und weiss durch das Mapping, dass er die lokale Klasse `ch.fhnw.digi.mockups.case3.JobMessage` verwenden soll.
- **Beim Senden:** Der Converter schaut, welcher Mapping-Key zu `JobRequestMessage.class` gehört, und setzt `_type = "ch.fhnw.digi.demo.JobRequestMessage"` – genau das, was der Dispo-Server erwartet.

---

## Filterung

Der Client unterstützt zwei optionale Filter, die in der `application.properties` konfiguriert werden. Die Filter werden im `MessageReceiver` angewendet, **bevor** ein Job an die GUI weitergegeben wird:

### Region-Filter (`client.region`)

Zeigt nur Aufträge aus einer bestimmten Region an. Wenn der Wert leer gelassen wird, werden Aufträge aus allen Regionen angezeigt.

```properties
# Nur Aufträge aus Basel anzeigen:
client.region=basel

# Alle Regionen anzeigen:
client.region=
```

### JobType-Filter (`client.jobType`)

Zeigt nur Aufträge eines bestimmten Typs an (`repair` oder `maintenance`). Wenn leer, werden alle Typen angezeigt.

```properties
# Nur Reparatur-Aufträge:
client.jobType=repair

# Alle Typen anzeigen:
client.jobType=
```

Die Filter werden case-insensitive verglichen (z.B. `"Basel"` matcht `"basel"`).

---

## Konfiguration (`application.properties`)

```properties
# ActiveMQ-Broker-Verbindung
spring.activemq.broker-url=tcp://192.168.111.6:61616
spring.activemq.user=group6
spring.activemq.password=oi374bU0njDlI6m

# Eindeutige Client-ID (wird beim Anfordern von Jobs mitgeschickt)
client.id=group6_ClientAppStub

# Optionale Filter
client.region=basel
client.jobType=repair
```

| Property | Beschreibung | Default |
|---|---|---|
| `spring.activemq.broker-url` | URL des ActiveMQ-Brokers | – |
| `spring.activemq.user` | Benutzername für die Broker-Authentifizierung | – |
| `spring.activemq.password` | Passwort für die Broker-Authentifizierung | – |
| `client.id` | Eindeutige Kennung dieses Clients | `group6` |
| `client.region` | Region-Filter (leer = alle) | leer |
| `client.jobType` | JobType-Filter (leer = alle) | leer |

---

## Projektstruktur

```
src/main/java/ch/fhnw/digi/mockups/case3/
│
├── JobMessage.java              Datenmodell: Neuer Auftrag
├── JobRequestMessage.java       Datenmodell: Auftragsanfrage
├── JobAssignmentMessage.java    Datenmodell: Zuweisungsentscheidung
│
└── client/
    ├── Case3ClientAppApplication.java   Spring Boot Einstiegspunkt
    ├── MessageReceiver.java             JMS-Listener & Converter-Konfiguration
    ├── MessageSender.java               JMS-Nachrichtenversand
    └── UI.java                          Swing-GUI (JFrame)
```

### Komponentenübersicht

| Klasse | Verantwortung |
|---|---|
| `Case3ClientAppApplication` | Startet die Spring Boot Applikation mit `headless(false)`, damit die Swing-GUI angezeigt werden kann. |
| `MessageReceiver` | Empfängt Nachrichten von den beiden Topics, wendet die optionalen Filter an und gibt relevante Nachrichten an die UI weiter. Konfiguriert ausserdem die JMS-`ListenerContainerFactory` und den `MessageConverter`. |
| `MessageSender` | Wird von der UI aufgerufen, wenn der Benutzer einen Job anfordert. Erstellt eine `JobRequestMessage` und sendet sie an die Queue des Dispo-Servers. |
| `UI` | Swing-basierte Oberfläche mit zwei Listen: offene Jobs (links) und zugewiesene Jobs (rechts). Ein Button ermöglicht es, den ausgewählten Job anzufordern. |

---

## Technische Details

### JMS-Konfiguration (ListenerContainerFactory)

Die `myFactory`-Bean in `MessageReceiver` erstellt eine `DefaultJmsListenerContainerFactory` mit folgenden Einstellungen:

- **`setPubSubDomain(true)`** – Die Listener arbeiten im Topic-Modus (Publish/Subscribe), weil beide Empfangs-Destinations Topics sind.
- **`setMessageConverter(...)`** – Der Jackson-basierte Converter wird gesetzt, damit JSON-Nachrichten automatisch in Java-Objekte deserialisiert werden.

### Senden vs. Empfangen: Topic vs. Queue

| | Empfangen (Receiver) | Senden (Sender) |
|---|---|---|
| **Modus** | Topic (Pub/Sub) | Queue (Point-to-Point) |
| **Einstellung** | `factory.setPubSubDomain(true)` | `jmsTemplate.setPubSubDomain(false)` |
| **Grund** | Alle Clients sollen neue Jobs und Zuweisungen erhalten | Jede Job-Anfrage soll genau einmal vom Server verarbeitet werden |

### Thread-Sicherheit in der GUI

Da JMS-Nachrichten auf Hintergrund-Threads empfangen werden, die Swing-GUI aber nur vom **Event Dispatch Thread (EDT)** verändert werden darf, verwenden `addJobToList()` und `assignJob()` in der UI-Klasse `SwingUtilities.invokeLater()`, um die GUI-Updates auf den EDT zu verlagern. Zusätzlich werden die ListModels mit `synchronized`-Blöcken geschützt.

---

## Voraussetzungen

- **Java 11** oder höher
- **Maven** zum Bauen
- **ActiveMQ-Broker** erreichbar unter der konfigurierten URL
- **Dispo-Server-Applikation** muss laufen und Nachrichten publizieren

## Starten

```bash
mvn spring-boot:run
```

Oder in der IDE: `Case3ClientAppApplication.main()` ausführen.

Nach dem Start öffnet sich automatisch ein Swing-Fenster. Sobald der Dispo-Server Aufträge publiziert, erscheinen diese (gefiltert nach Region und JobType) in der linken Liste. Durch Auswählen eines Jobs und Klick auf "Selektierten Job anfordern" wird die Anfrage an den Server gesendet.

