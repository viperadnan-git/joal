# JOAL Codebase Documentation

> **Jack of All Trades** - A Java-based BitTorrent seeding emulator that simulates various BitTorrent client behaviors to trackers.

## Quick Reference

| Property | Value |
|----------|-------|
| **Version** | 2.1.37-SNAPSHOT |
| **Java Version** | 11 |
| **Framework** | Spring Boot 2.7.3 |
| **Build Tool** | Maven |
| **Main Entry** | `org.araymond.joal.JackOfAllTradesApplication` |

---

## Project Structure

```
/home/ubuntu/joal/
├── src/main/java/org/araymond/joal/    # Main application code
│   ├── core/                            # Core seeding logic
│   └── web/                             # Web UI and API
├── src/main/resources/                  # Configuration and static files
├── src/test/java/                       # Test files
├── resources/                           # Client configuration files
├── pom.xml                              # Maven configuration
├── Dockerfile                           # Multi-stage Docker build
└── docker-compose.yml                   # Docker Compose setup
```

---

## Package Overview

### Core Layer (`org.araymond.joal.core`)

| Package | Location | Purpose |
|---------|----------|---------|
| `SeedManager` | `core/SeedManager.java` | Main orchestrator - manages entire seeding lifecycle |
| `config/` | `core/config/` | Configuration management - loads/saves `config.json` |
| `torrent/torrent/` | `core/torrent/torrent/` | Torrent metadata - `MockedTorrent`, `InfoHash` |
| `torrent/watcher/` | `core/torrent/watcher/` | File system monitoring - watches `/torrents` folder |
| `bandwith/` | `core/bandwith/` | Bandwidth allocation - distributes fake upload speeds |
| `client/emulated/` | `core/client/emulated/` | Client emulation - `BitTorrentClient` crafts tracker requests |
| `ttorrent/client/` | `core/ttorrent/client/` | Torrent management - `Client` orchestrates announcements |
| `ttorrent/client/announcer/` | `core/ttorrent/client/announcer/` | Tracker communication - `Announcer` performs HTTP requests |
| `events/` | `core/events/` | Event publishing - Spring Events for state changes |

### Web Layer (`org.araymond.joal.web`)

| Package | Location | Purpose |
|---------|----------|---------|
| `resources/` | `web/resources/` | API endpoints - `WebSocketController` |
| `messages/incoming/` | `web/messages/incoming/` | Client requests - config/torrent messages |
| `messages/outgoing/` | `web/messages/outgoing/` | Server responses - STOMP payloads |
| `config/security/` | `web/config/security/` | WebSocket authentication |
| `config/obfuscation/` | `web/config/obfuscation/` | Path obfuscation filter |
| `services/` | `web/services/` | Message broadcasting via STOMP |

### Application Startup (`org.araymond.joal`)

| Class | Purpose |
|-------|---------|
| `JackOfAllTradesApplication` | Spring Boot entry point |
| `ApplicationReadyListener` | Triggers `SeedManager.init()` and `startSeeding()` |
| `ApplicationClosingListener` | Cleanup on shutdown |
| `CoreEventListener` | Logs core events for debugging |

---

## Key Classes

### SeedManager (Central Coordinator)
**Location:** `src/main/java/org/araymond/joal/core/SeedManager.java`

The main orchestrator that manages the entire seeding lifecycle:
- `init()` → `startSeeding()` → `stop()` → `tearDown()`
- Holds references to all major components
- Exposes methods for web UI: `saveNewConfiguration()`, `saveTorrentToDisk()`, `deleteTorrent()`
- Publishes events: `GlobalSeedStartedEvent`, `GlobalSeedStoppedEvent`, `SeedingSpeedsHasChangedEvent`
- Manages `JoalFoldersPath`: `/torrents`, `/torrents/archived`, `/clients`, `/config.json`

### BitTorrentClient (Request Generation)
**Location:** `src/main/java/org/araymond/joal/core/client/emulated/BitTorrentClient.java`

Stateless request formatter that:
- Uses regex patterns to generate peer IDs and keys
- Replaces placeholders in query template: `{infohash}`, `{peerid}`, `{key}`, `{port}`, `{uploaded}`, `{left}`, etc.
- Handles client spoofing: User-Agent, Accept-Encoding, Connection headers
- Method `createRequestQuery()` builds complete tracker announcement request

### Client (Torrent Orchestration)
**Location:** `src/main/java/org/araymond/joal/core/ttorrent/client/Client.java`

Main seeding loop that:
- Implements `TorrentFileChangeAware` for file system events
- Sleeps 1s, pulls ready announcements from `delayQueue`, executes them
- Maintains list of `currentlySeedingAnnouncers` (up to `simultaneousSeed` count)
- Auto-rotates torrents: removes dead/failed ones, adds new ones
- Uses `ReentrantReadWriteLock` for thread safety

### BandwidthDispatcher (Speed Distribution)
**Location:** `src/main/java/org/araymond/joal/core/bandwith/BandwidthDispatcher.java`

Runs in separate thread that:
- Updates speeds every 5 seconds
- Every 20 minutes: refreshes global speed (min-max random range)
- Uses `WeightHolder` + `PeersAwareWeightCalculator`: weights torrents by seed/leech count
- Allocates bandwidth proportionally to torrent weights

### Announcer (Tracker Communication)
**Location:** `src/main/java/org/araymond/joal/core/ttorrent/client/announcer/Announcer.java`

One instance per torrent that:
- Maintains: `lastKnownInterval`, consecutive failures, last known seeders/leechers
- Method `announce(RequestEvent)`: makes HTTP request, parses response
- Fails after 5 consecutive errors

### TorrentFileWatcher (File System Monitoring)
**Location:** `src/main/java/org/araymond/joal/core/torrent/watcher/TorrentFileWatcher.java`

Uses Apache Commons `FileAlterationMonitor`:
- Scans every 5 seconds
- Filters `.torrent` files only
- Fires events: `onFileCreate`, `onFileChange`, `onFileDelete`

---

## Architecture Flow

```
ApplicationReadyListener
    ↓
SeedManager (orchestrator)
    ├→ TorrentFileProvider (watches /torrents)
    ├→ JoalConfigProvider (loads config.json)
    ├→ BitTorrentClientProvider (loads .client file)
    ├→ BandwidthDispatcher (thread: updates speeds every 5s)
    └→ ClientFacade / Client (main loop: checks delayQueue every 1s)
        └→ DelayQueue<AnnounceRequest>
            └→ AnnouncerExecutor (3-thread pool)
                └→ Announcer (per torrent)
                    └→ TrackerClient (HTTP communication)
```

---

## Configuration

### Application Configuration (`config.json`)

```json
{
  "minUploadRate": 30,              // kB/s minimum
  "maxUploadRate": 160,             // kB/s maximum
  "simultaneousSeed": 20,           // concurrent torrents
  "client": "qbittorrent-4.4.5.client",  // which client to emulate
  "keepTorrentWithZeroLeechers": true,   // keep seeding with no peers?
  "uploadRatioTarget": -1.0         // -1 = never remove, else removal ratio
}
```

### Client Emulation Files (`.client`)

Located in `/resources/clients/`, these JSON files define client emulation behavior:

```json
{
  "peerIdGenerator": {
    "algorithm": { "type": "REGEX", "pattern": "..." },
    "refreshOn": "NEVER|ALWAYS|TIMED|TORRENT_PERSISTENT|TORRENT_VOLATILE",
    "shouldUrlEncode": false
  },
  "keyGenerator": {
    "algorithm": { "type": "HASH_NO_LEADING_ZERO", "length": 8 },
    "refreshOn": "...",
    "keyCase": "lower|upper"
  },
  "urlEncoder": {
    "encodingExclusionPattern": "[A-Za-z0-9...]",
    "encodedHexCase": "lower|upper"
  },
  "query": "info_hash={infohash}&peer_id={peerid}&port={port}&...",
  "numwant": 200,
  "numwantOnStop": 0,
  "requestHeaders": [
    { "name": "User-Agent", "value": "qBittorrent/4.4.5" }
  ]
}
```

**Key Refresh Strategies:**
- `NEVER`: Generate once, never refresh
- `ALWAYS`: Generate for every announce
- `TIMED`: Generate every X milliseconds
- `TORRENT_PERSISTENT`: One key per torrent per session
- `TORRENT_VOLATILE`: New key for started/stopped events

### Spring Configuration

```properties
spring.main.banner-mode=off
spring.main.web-environment=false              # Disabled by default
server.port=${random.int[4000,60000]}          # Random port if not set
```

**CLI Args to enable Web UI:**
```
--spring.main.web-environment=true
--server.port=8080
--joal.ui.path.prefix=SECRET_PATH
--joal.ui.secret-token=SECRET_TOKEN
```

---

## Threading Model

| Thread | Purpose | Lifecycle |
|--------|---------|-----------|
| Main | Spring Boot initialization | App lifetime |
| Client Orchestrator | Main seeding loop, delayQueue polling | `startSeeding()` → `stop()` |
| Bandwidth Dispatcher | Speed calculation & distribution | `start()` → `stop()` |
| Announcer Pool (3) | HTTP requests to trackers | Per `Client` |
| Torrent File Watcher | Directory monitoring | `start()` → `stop()` |
| Async Tasks (5-10) | Event listeners & async methods | App lifetime |
| WebSocket (optional) | Web UI communication | If `web-environment=true` |

**Concurrency Strategy:**
- `ReentrantReadWriteLock` for thread-safe access to shared state
- `ConcurrentHashMap` for executor tracking
- Immutable/copyable payloads for events

---

## Event System

Spring's `ApplicationEventPublisher` broadcasts domain events:

### Configuration Events
| Event | Description |
|-------|-------------|
| `ConfigHasBeenLoadedEvent` | Config loaded/initialized |
| `ConfigurationIsInDirtyStateEvent` | Config modified via web UI |
| `ListOfClientFilesEvent` | Available client configurations |

### Torrent Events
| Event | Description |
|-------|-------------|
| `TorrentFileAddedEvent` | New .torrent detected |
| `TorrentFileDeletedEvent` | Torrent deleted/archived |
| `FailedToAddTorrentFileEvent` | Invalid torrent file |

### Announce Events
| Event | Description |
|-------|-------------|
| `WillAnnounceEvent` | About to announce |
| `SuccessfullyAnnounceEvent` | Tracker response success |
| `FailedToAnnounceEvent` | Announce failed |
| `TooManyAnnouncesFailedEvent` | 5 failures reached |

### Speed Events
| Event | Description |
|-------|-------------|
| `SeedingSpeedsHasChangedEvent` | Speed allocations updated |

### Global State Events
| Event | Description |
|-------|-------------|
| `GlobalSeedStartedEvent` | Seeding session started |
| `GlobalSeedStoppedEvent` | Seeding session stopped |

---

## Application Lifecycle

### Startup Flow
1. Spring Boot initializes `JackOfAllTradesApplication`
2. `ApplicationReadyListener.onApplicationEvent()` fires
3. `SeedManager.init()`:
   - Creates `ConnectionHandler` (manages network interface)
   - Starts `TorrentFileProvider` (begins watching /torrents)
4. `SeedManager.startSeeding()`:
   - Loads `AppConfiguration` from config.json
   - Loads BitTorrent client emulation config
   - Starts `BandwidthDispatcher` thread
   - Builds and starts `Client`

### Announce Loop (Every 1-5 seconds)
1. `Client` thread checks `delayQueue.getAvailables()`
2. For each ready `AnnounceRequest`:
   - `AnnouncerExecutor.execute()` submits to thread pool
   - `Announcer` calls `tracker.announce()`
   - `TrackerClient` builds HTTP GET request
   - Tracker responds with: interval, seeders, leechers, peers
   - Response handler publishes `SuccessfullyAnnounceEvent`
   - Request re-enqueued based on interval

### Bandwidth Update (Every 5 seconds)
1. `BandwidthDispatcher.run()` thread:
   - Updates uploaded bytes for each torrent
   - Every 20 min: refresh global speed
   - Recalculate per-torrent speeds based on weights
   - Publish `SeedingSpeedsHasChangedEvent`

### Torrent Rotation
1. Announcer hits "too many failures" (5x) or upload ratio limit
2. Torrent moved to `/torrents/archived`
3. Next torrent pulled from `/torrents` and added to seeding

### Shutdown Flow
1. `ApplicationClosingListener` or SIGTERM signal
2. `SeedManager.stop()`:
   - Stops client (sends "stopped" events)
   - Stops bandwidth dispatcher
   - Closes connection handler
   - Stops torrent file provider

---

## Key Dependencies

| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-web` | REST/web support |
| `spring-boot-starter-websocket` | WebSocket communication |
| `spring-boot-starter-security` | Authentication |
| `ttorrent-core:1.5` | BitTorrent protocol implementation |
| `generex:1.0.2` | Regex string generation |
| `guava:31.1-jre` | Google utilities |
| `commons-io:2.12.0` | File utilities |
| `log4j2` | Logging |

---

## Security Features

1. **Web UI Security:**
   - Token-based authentication
   - Path obfuscation (`--joal.ui.path.prefix`)
   - WebSocket message validation

2. **HTTP Anonymization:**
   - Custom User-Agent headers
   - Client spoofing
   - Peer ID randomization
   - Key generation strategies

3. **Tracker Detection Prevention:**
   - Path prefix prevents endpoint scanning
   - Socket configuration timeouts (5-30s)
   - Connection pool limits (100 per route, 200 total)

---

## File Locations

| Path | Description |
|------|-------------|
| `/torrents/` | Directory for .torrent files to seed |
| `/torrents/archived/` | Completed/failed torrents moved here |
| `/clients/` | Client emulation configuration files |
| `/config.json` | Application configuration |

---

## Supported Clients

The application can emulate various BitTorrent clients including:
- qBittorrent (multiple versions)
- Deluge (including 2.2.0)
- rTorrent
- Transmission
- Vuze
- uTorrent
- And more...

Client configurations are stored as `.client` JSON files in the `resources/clients/` directory.

---

## Build & Run

### Maven Build
```bash
mvn clean package
```

### Run Locally
```bash
java -jar target/jack-of-all-trades-*.jar
```

### Docker Build
```bash
docker build -t joal .
```

### Docker Compose
```bash
docker-compose up -d
```

---

## Testing

84 test files covering:
- Unit tests for client request generation
- Bandwidth dispatcher allocation logic
- Configuration validation
- Event system behavior
- Web message serialization

Run tests:
```bash
mvn test
```
