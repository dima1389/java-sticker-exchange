# Java Sticker Exchange

A multiplayer **sticker-trading system** built in Java 21. Each player owns a digital album of
stickers numbered **1–99**. Players have **duplicates** (stickers they own more than once and can
give away) and **missing** stickers (gaps they want to fill). A central server connects all online
players, calculates which trades are possible, and safely applies sticker swaps between two albums.

The project is split into three Maven modules:

| Module   | Responsibility                                                              |
|----------|-----------------------------------------------------------------------------|
| `common` | Shared domain model, network protocol messages, and matching services.      |
| `server` | TCP server that hosts all players and enforces the rules of trading.        |
| `client` | Swing desktop GUI (MVC) that players use to edit albums and propose trades.  |

### Features

- Random starter album generated for every new player.
- Live editing of duplicates / missing stickers (mutually exclusive, range 1–99).
- Automatic **match-finding** against every other online player.
- Trade proposals with **accept / decline** handshakes.
- **Two-phase-commit** safety: a trade is re-validated against the latest albums before it is applied.
- Fully thread-safe server core, one background thread per connected client.

---

## 1. High-Level System Architecture

The system is a classic **client–server** design. Many Swing clients connect over TCP to a single
server. Both sides depend on the shared `common` module so they speak the exact same object protocol.

```mermaid
flowchart TB
    subgraph ClientTier["Client Tier (one per player)"]
        direction TB
        View["StickerExchangeFrame<br/>(Swing View)"]
        Ctrl["ClientController<br/>(Controller)"]
        Conn["ServerConnection<br/>(Net / reader thread)"]
        View <--> Ctrl
        Ctrl <--> Conn
    end

    subgraph Network["Network"]
        direction TB
        TCP{{"TCP Socket<br/>Java Object Streams<br/>port 5050"}}
    end

    subgraph ServerTier["Server Tier (single process)"]
        direction TB
        Main["ServerMain<br/>(accept loop + thread pool)"]
        Handler["ClientHandler x N<br/>(one per client)"]
        Coord["ExchangeCoordinator<br/>(synchronized brain)"]
        Main --> Handler
        Handler <--> Coord
    end

    subgraph Shared["Shared Module: common"]
        direction TB
        Model["model<br/>AlbumState, TradeMatch, TradeProposal"]
        Proto["protocol<br/>Message records"]
        Svc["service<br/>ExchangeCalculator, AlbumInitializer"]
    end

    Conn <--> TCP
    TCP <--> Handler

    ClientTier -.uses.-> Shared
    ServerTier -.uses.-> Shared
```

---

## 2. Maven Module Dependency Graph

`common` is the foundation. Both `server` and `client` depend on it; they never depend on each
other — they only communicate at runtime through serialized protocol messages.

```mermaid
flowchart LR
    common["common<br/>(model · protocol · service)"]
    server["server<br/>(app · core)"]
    client["client<br/>(app · controller · net · ui)"]

    server -->|compile dependency| common
    client -->|compile dependency| common
    server -.runtime: serialized messages.-> client
```

---

## 3. Deployment / Network Topology

A single server process accepts many simultaneous players. Each connection is an independent TCP
socket carrying serialized Java objects.

```mermaid
flowchart TB
    C1["Player A<br/>Client GUI"]
    C2["Player B<br/>Client GUI"]
    C3["Player C<br/>Client GUI"]

    S(["Sticker Exchange Server<br/>ServerSocket : 5050"])

    C1 ---|"TCP ObjectStream"| S
    C2 ---|"TCP ObjectStream"| S
    C3 ---|"TCP ObjectStream"| S

    S --- DB[("In-memory state<br/>users map + proposals map")]
```

---

## 4. Domain Model (Class Diagram)

The domain is built from immutable Java **records**. `AlbumState` is the core value object; trades
are described by `TradeMatch` (a discovered opportunity) and `TradeProposal` (a formal offer).

```mermaid
classDiagram
    class AlbumState {
        +SortedSet~Integer~ duplicates
        +SortedSet~Integer~ missing
        +empty() AlbumState
        +withoutDuplicates(set) AlbumState
        +withoutMissing(set) AlbumState
        +duplicatesContainAll(set) boolean
        +missingContainAll(set) boolean
        +MIN_STICKER = 1
        +MAX_STICKER = 99
    }

    class TradeMatch {
        +String otherUsername
        +SortedSet~Integer~ offerFromCurrentUser
        +SortedSet~Integer~ offerFromOtherUser
        +currentUserMustSelect() boolean
        +otherUserMustSelect() boolean
    }

    class TradeProposal {
        +String proposalId
        +String requesterUsername
        +String recipientUsername
        +SortedSet~Integer~ requesterOffer
        +SortedSet~Integer~ recipientOfferCandidates
        +boolean recipientSelectionRequired
        +int expectedRecipientSelectionSize
        +fixedRecipientOffer() SortedSet
    }

    AlbumState "1" --> "produces" TradeMatch : ExchangeCalculator
    TradeMatch ..> TradeProposal : becomes when proposed
```

---

## 5. Protocol Message Catalog (Class Diagram)

Every object sent across the wire implements the `Message` marker interface. Requests flow
client → server; responses and events flow server → client.

```mermaid
classDiagram
    class Message {
        <<interface>>
    }

    class RegisterRequest
    class AlbumSyncRequest
    class FindMatchesRequest
    class ProposeTradeRequest
    class TradeDecisionRequest

    class RegisterResponse
    class AlbumSyncResponse
    class MatchesResponse
    class IncomingTradeProposal
    class TradeAppliedEvent
    class InfoMessage

    Message <|.. RegisterRequest
    Message <|.. AlbumSyncRequest
    Message <|.. FindMatchesRequest
    Message <|.. ProposeTradeRequest
    Message <|.. TradeDecisionRequest
    Message <|.. RegisterResponse
    Message <|.. AlbumSyncResponse
    Message <|.. MatchesResponse
    Message <|.. IncomingTradeProposal
    Message <|.. TradeAppliedEvent
    Message <|.. InfoMessage

    note for RegisterRequest "client → server"
    note for InfoMessage "server → client"
```

| Direction        | Message                | Purpose                                          |
|------------------|------------------------|--------------------------------------------------|
| client → server  | `RegisterRequest`      | Claim a username.                                |
| server → client  | `RegisterResponse`     | Success/failure + starter album.                 |
| client → server  | `AlbumSyncRequest`     | Save the edited album.                            |
| server → client  | `AlbumSyncResponse`    | Confirm the stored album.                        |
| client → server  | `FindMatchesRequest`   | Ask for all current trade opportunities.         |
| server → client  | `MatchesResponse`      | List of `TradeMatch` results.                    |
| client → server  | `ProposeTradeRequest`  | Offer a trade to another player.                 |
| server → client  | `IncomingTradeProposal`| Deliver a proposal to the recipient.             |
| client → server  | `TradeDecisionRequest` | Accept or decline a proposal.                    |
| server → client  | `TradeAppliedEvent`    | A completed trade + updated album.               |
| server → client  | `InfoMessage`          | Status / error / confirmation text.              |

---

## 6. Server Components

`ServerMain` owns the accept loop and a thread pool. Each `ClientHandler` runs on its own thread
but routes all decisions through the **single** `ExchangeCoordinator`, which owns all shared state.

```mermaid
flowchart TB
    subgraph ServerMain
        Accept["accept() loop"]
        Pool["Cached Thread Pool"]
        Accept -->|new connection| Pool
    end

    Pool --> H1["ClientHandler<br/>(Player A thread)"]
    Pool --> H2["ClientHandler<br/>(Player B thread)"]
    Pool --> H3["ClientHandler<br/>(Player C thread)"]

    H1 --> Coord
    H2 --> Coord
    H3 --> Coord

    subgraph Coord["ExchangeCoordinator (all methods synchronized)"]
        Users[("users:<br/>Map&lt;String, ConnectedUser&gt;")]
        Props[("proposals:<br/>Map&lt;String, TradeProposal&gt;")]
        Calc["ExchangeCalculator"]
        Init["AlbumInitializer"]
    end
```

---

## 7. Client Components (MVC)

The client follows the **Model-View-Controller** pattern. The controller is the hub: it turns UI
actions into protocol messages and routes incoming messages back onto the Swing UI thread (EDT).

```mermaid
flowchart LR
    User(["Player"])

    subgraph Client
        direction TB
        Frame["StickerExchangeFrame<br/>(View · Swing)"]
        Controller["ClientController<br/>(Controller · MessageListener)"]
        Connection["ServerConnection<br/>(Net · daemon reader)"]
    end

    User -->|clicks| Frame
    Frame -->|user actions| Controller
    Controller -->|"send(Message)"| Connection
    Connection -->|onMessageReceived| Controller
    Controller -->|invokeLater · update| Frame
    Connection <-->|TCP| Server([Server])
```

---

## 8. Threading Model

Concurrency is the heart of the design: many client threads on the server funnel through a single
`synchronized` coordinator, while the client keeps networking off the UI thread.

```mermaid
flowchart TB
    subgraph ServerProcess["Server Process"]
        AcceptT["Accept Thread<br/>(serverSocket.accept)"]
        T1["Client Thread A"]
        T2["Client Thread B"]
        Lock{{"synchronized<br/>ExchangeCoordinator"}}
        AcceptT -->|spawns| T1
        AcceptT -->|spawns| T2
        T1 -->|one at a time| Lock
        T2 -->|one at a time| Lock
    end

    subgraph ClientProcess["Client Process"]
        Reader["Daemon Reader Thread<br/>(ServerConnection)"]
        EDT["Event Dispatch Thread<br/>(Swing UI)"]
        Reader -->|invokeLater| EDT
    end

    Lock -.dispatch messages.-> Reader
```

---

## 9. Sequence — Registration & Starter Album

```mermaid
sequenceDiagram
    actor Player
    participant Frame as StickerExchangeFrame
    participant Ctrl as ClientController
    participant Conn as ServerConnection
    participant Handler as ClientHandler
    participant Coord as ExchangeCoordinator

    Player->>Frame: enter host, port, username + Connect
    Frame->>Ctrl: connect(host, port, username)
    Ctrl->>Conn: connect() + send(RegisterRequest)
    Conn->>Handler: RegisterRequest
    Handler->>Coord: register(request, connection)
    alt username free
        Coord->>Coord: createRandomAlbum() + store ConnectedUser
        Coord-->>Handler: RegisterResponse(success, album)
    else username taken
        Coord-->>Handler: RegisterResponse(failure)
    end
    Handler-->>Conn: RegisterResponse
    Conn-->>Ctrl: onMessageReceived
    Ctrl-->>Frame: render album / show status
```

---

## 10. Sequence — Save Album & Find Matches

```mermaid
sequenceDiagram
    actor Player
    participant Ctrl as ClientController
    participant Coord as ExchangeCoordinator
    participant Calc as ExchangeCalculator

    Player->>Ctrl: Save album
    Ctrl->>Coord: AlbumSyncRequest(albumState)
    Coord->>Coord: store user.albumState
    Coord-->>Ctrl: AlbumSyncResponse(updated album)

    Player->>Ctrl: Find matches
    Ctrl->>Coord: FindMatchesRequest
    Coord->>Coord: collect every OTHER user's album
    Coord->>Calc: calculateMatches(myAlbum, others)
    Calc-->>Coord: List~TradeMatch~
    Coord-->>Ctrl: MatchesResponse(matches)
    Ctrl-->>Player: show matches list
```

---

## 11. Sequence — Propose Trade

```mermaid
sequenceDiagram
    actor Requester
    participant RCtrl as Requester Controller
    participant Coord as ExchangeCoordinator
    participant TCtrl as Recipient Controller
    actor Recipient

    Requester->>RCtrl: select match + Propose
    Note over RCtrl: if requester has surplus,<br/>pick which stickers to give
    RCtrl->>Coord: ProposeTradeRequest
    Coord->>Coord: validate (online? self? duplicate proposal?)
    Coord->>Coord: recompute live match + validateProposalRequest
    alt valid
        Coord->>Coord: create TradeProposal (UUID) + store
        Coord-->>RCtrl: InfoMessage("Trade request sent")
        Coord-->>TCtrl: IncomingTradeProposal
        TCtrl-->>Recipient: show proposal dialog
    else invalid
        Coord-->>RCtrl: InfoMessage(error)
    end
```

---

## 12. Sequence — Respond to Trade (Accept, Two-Phase Commit)

```mermaid
sequenceDiagram
    actor Recipient
    participant TCtrl as Recipient Controller
    participant Coord as ExchangeCoordinator
    participant RCtrl as Requester Controller
    actor Requester

    Recipient->>TCtrl: Accept (choose stickers if required)
    TCtrl->>Coord: TradeDecisionRequest(accept = true)
    Coord->>Coord: locate proposal + verify recipient
    Coord->>Coord: resolveRecipientOffer()
    Coord->>Coord: isTradeStillValid()
    alt still valid
        Coord->>Coord: swap stickers<br/>withoutDuplicates / withoutMissing
        Coord->>Coord: remove proposal
        Coord-->>TCtrl: TradeAppliedEvent(new album)
        Coord-->>RCtrl: TradeAppliedEvent(new album)
        TCtrl-->>Recipient: album updated
        RCtrl-->>Requester: album updated
    else outdated / invalid
        Coord->>Coord: remove proposal
        Coord-->>TCtrl: InfoMessage(canceled)
        Coord-->>RCtrl: InfoMessage(canceled)
    end
```

---

## 13. Matching Algorithm (ExchangeCalculator)

A trade between two players exists when each can fill a gap in the other's album. The calculator
intersects one player's **duplicates** with the other's **missing** stickers, in both directions.

```mermaid
flowchart TB
    Start([calculateMatch: me vs other])
    A["iGive = intersection(<br/>myDuplicates, theirMissing)"]
    B["iGet = intersection(<br/>theirDuplicates, myMissing)"]
    Q{"iGive non-empty<br/>AND iGet non-empty?"}
    Yes["build TradeMatch(<br/>offerFromCurrentUser = iGive,<br/>offerFromOtherUser = iGet)"]
    No["no match (empty Optional)"]

    Start --> A --> B --> Q
    Q -->|yes| Yes
    Q -->|no| No
    Yes --> Size{"compare sizes"}
    Size -->|iGive &gt; iGet| Sel1["currentUserMustSelect = true"]
    Size -->|iGive &lt; iGet| Sel2["otherUserMustSelect = true"]
    Size -->|equal| Even["even trade — no selection"]
```

---

## 14. Trade Validation & Two-Phase Commit (Decision Flow)

The server never trusts a stale proposal. It classifies the trade shape (cases A–D) at proposal
time, then **re-validates against the live albums** at acceptance time before mutating any state.

```mermaid
flowchart TB
    P([respondToTrade: accepted]) --> R1{"proposal exists<br/>& addressed to me?"}
    R1 -->|no| Err1["InfoMessage: not available"]
    R1 -->|yes| R2{"requester still online?"}
    R2 -->|no| Err2["cancel + notify"]
    R2 -->|yes| Res["resolveRecipientOffer()"]
    Res --> R3{"selection valid<br/>& owned as duplicates?"}
    R3 -->|no| Err3["cancel + notify both"]
    R3 -->|yes| Live["isTradeStillValid()"]
    Live --> R4{"both albums still<br/>support the swap?"}
    R4 -->|no| Err4["outdated — cancel + notify"]
    R4 -->|yes| Apply["apply swap<br/>update both albums"]
    Apply --> Done["TradeAppliedEvent x2"]
```

---

## 15. Proposal Lifecycle (State Diagram)

```mermaid
stateDiagram-v2
    [*] --> None
    None --> Pending : proposeTrade (validated)
    Pending --> Applied : accept + still valid
    Pending --> Declined : recipient declines
    Pending --> Canceled : invalid selection / outdated
    Pending --> Canceled : a party disconnects
    Applied --> [*]
    Declined --> [*]
    Canceled --> [*]
```

---

## 16. Client Connection Lifecycle (State Diagram)

```mermaid
stateDiagram-v2
    [*] --> Disconnected
    Disconnected --> Connecting : Connect clicked
    Connecting --> Registering : socket open + RegisterRequest
    Connecting --> Disconnected : connection failed
    Registering --> Connected : RegisterResponse(success)
    Registering --> Disconnected : username taken
    Connected --> Connected : save album / find matches / trade
    Connected --> Disconnected : socket closed / onDisconnected
    Disconnected --> [*]
```

---

## 17. Project Layout

```ini
java-sticker-exchange/
├── pom.xml                       # parent / aggregator module
├── common/                       # shared, framework-free module
│   └── com/stickerexchange/common/
│       ├── model/                # AlbumState, TradeMatch, TradeProposal
│       ├── protocol/             # Message records (requests/responses/events)
│       ├── service/              # ExchangeCalculator, AlbumInitializer
│       └── util/                 # StickerSets
├── server/
│   └── com/stickerexchange/server/
│       ├── app/                  # ServerMain (+ ClientHandler)
│       └── core/                 # ExchangeCoordinator (the brain)
└── client/
    └── com/stickerexchange/client/
        ├── app/                  # ClientMain
        ├── controller/           # ClientController (MVC)
        ├── net/                  # ServerConnection
        └── ui/                   # StickerExchangeFrame (Swing)

```

---

## 18. Build & Run

Requirements: **JDK 21+** and **Maven 3.9+**.

### Build all modules

```powershell
mvn install -DskipTests -q

```

### Start the server (default port 5050)

```powershell
mvn -pl server org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.mainClass=com.stickerexchange.server.app.ServerMain"

```

To use a custom port, pass it as an argument (e.g. append `-Dexec.args="6000"`).

### Start a client GUI (run one per player)

```powershell
mvn -pl client org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.mainClass=com.stickerexchange.client.app.ClientMain"

```

> Tip: these commands are also available as the **Build Modules**, **Run Server**, and
> **Run Client GUI** VS Code tasks.

### Typical session

1. Start the server.
2. Launch two or more client GUIs.
3. In each client, enter host (`localhost`), port (`5050`), and a unique username, then **Connect**.
4. Edit your duplicates / missing stickers and **Save album**.
5. Click **Find matches**, pick a player, and **Propose trade**.
6. The recipient accepts or declines; on success, both albums update instantly.