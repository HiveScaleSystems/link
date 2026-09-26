# Link

Signed player transfers and matchmaking between Hytale servers. Drop the jar in `mods/` on every
server and players can hop with `/server lobby-2` or `/play skywars`. No database is required.

## How it works

Hytale moves a player between servers with a *referral*: the client disconnects and reconnects to
the target, carrying up to 4 KB of data. Since that data passes through the client, the target
cannot trust it. Link puts a signed ticket in it ("the network sent player P from server S to
server D, valid for 30 seconds"). The target checks the signature, that the ticket names this
server and this player, and that it has not been used before. A client cannot forge or reuse one.

Servers find each other through a **registry**. Pick the one that fits:

| Registry | You need | Live player counts | Auto-join |
|---|---|---|---|
| `static` | Nothing | No (round robin) | No, list servers by hand |
| `redis` | A Redis you run | Yes | Yes |
| `http` | A free Cloudflare account (template in `cloudflare-registry/`) | Yes | Yes |

When the registry goes down, transfers keep working from the last known server list.

## Setup

Start each server once. Link writes `link.json` into its data folder (`mods/Link_Link/`).

### Static (no infrastructure)

List every server and put **the same file** on each one. Only `serverId` differs per server, and you
can set it with `LINK_SERVER_ID` instead so the file is truly identical everywhere.

```json
{
  "serverId": "lobby-1",
  "backend": "static",
  "secret": "generated on first boot, copy it to every server",
  "servers": [
    { "id": "lobby-1", "group": "lobby",   "host": "play.example.com", "port": 5520 },
    { "id": "sw-1",    "group": "skywars", "host": "play.example.com", "port": 5521 }
  ]
}
```

### Redis

```json
{
  "serverId": "sw-1",
  "group": "skywars",
  "host": "play.example.com",
  "port": 5521,
  "backend": "redis",
  "redis": { "url": "redis://:your-password@10.0.0.5:6379/0", "namespace": "link" }
}
```

The first server to connect creates the network secret in Redis, so there is nothing to copy.
**Put a password on that Redis**: anyone who can write to it can join your network.

### Cloudflare Worker

Deploy the registry once (see `cloudflare-registry/README.md`), then on every server:

```json
{
  "serverId": "sw-1",
  "group": "skywars",
  "host": "play.example.com",
  "port": 5521,
  "backend": "http",
  "http": { "url": "https://link-registry.you.workers.dev", "token": "your LINK_TOKEN" }
}
```

### All options

| Key | Default | Meaning |
|---|---|---|
| `serverId` | `lobby-1` | Unique name of this server. Tickets are addressed to it. |
| `group` | `lobby` | What `/play <group>` matches on. |
| `host`, `port` | `""`, `5520` | Where other servers send players to reach this one. Not used by `static`, which reads them from `servers`. |
| `strategy` | `fill` | `fill` packs the busiest server with room (minigames); `spread` picks the emptiest (lobbies). |
| `requireTicket` | `false` | Turn on for game servers: players who join directly are sent to `fallbackGroup`. |
| `fallbackGroup` | `lobby` | Where `requireTicket` sends direct joins. |

Environment variables override the file: `LINK_SERVER_ID`, `LINK_GROUP`, `LINK_HOST`, `LINK_PORT`,
`LINK_SECRET`, `LINK_REDIS_URL`, `LINK_HTTP_URL`, `LINK_HTTP_TOKEN`.

## Commands

| Command | Who | Does |
|---|---|---|
| `/server <name>` | Everyone | Go to a specific server |
| `/play <group>` | Everyone | Matchmake into a group |
| `/servers` | Everyone | List the network |
| `/link` | Admins | Connection status |

## For plugin developers

```java
LinkPlugin.send(playerRef, "sw-1");             // to a named server
LinkPlugin.send(playerRef, "farm-1", "farm-42"); // into a specific world there
LinkPlugin.play(playerRef, "skywars");          // matchmake
LinkPlugin.link().servers("skywars");           // read the network
```

Each throws `LinkException` with a message you can show the player.

## Security model

Every server shares one network secret, so every server can admit a player to every other. That
fits a network where one operator runs all the servers. It also means a compromised server, or a
leaked Redis password or Worker token, lets someone forge a transfer to any server in the network.

If you need servers that cannot forge transfers to each other (servers run by different people,
or plugins you do not fully trust), you need a central signer that holds a separate key for each
server. [Hive](https://beehivesys.net) is built around that model, along with network-wide player
data, bans and a dashboard.

## Layout

- `LinkCore/`: tickets, config, matchmaking and the registries. No game server dependency.
- `LinkHytale/`: the Hytale plugin.
- `cloudflare-registry/`: the Worker behind the `http` registry.
