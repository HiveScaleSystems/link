<p align="center">
  <a href="https://link.beehivesys.net">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset=".github/assets/logo-dark.svg">
      <img src=".github/assets/logo.svg" alt="Link" height="72">
    </picture>
  </a>
</p>

<p align="center">
  Turn your Hytale servers into one network.<br>
  Players move between servers with <code>/play skywars</code>, without leaving the game.
</p>

<p align="center">
  <a href="https://github.com/HiveScaleSystems/link/actions/workflows/build.yml"><img src="https://github.com/HiveScaleSystems/link/actions/workflows/build.yml/badge.svg" alt="build"></a>
  <a href="https://github.com/HiveScaleSystems/link/releases"><img src="https://img.shields.io/github/v/release/HiveScaleSystems/link?include_prereleases&label=release" alt="release"></a>
</p>

<p align="center">
  <a href="https://link.beehivesys.net/docs">Docs</a> ·
  <a href="https://github.com/HiveScaleSystems/link/releases">Download</a> ·
  <a href="https://link.beehivesys.net">Website</a>
</p>

---

Link is a free, open-source plugin for Hytale servers. Put the same jar on every server and
players can hop between them. There is no proxy and no database to run.

- **One command to play.** `/play skywars` finds a game with room. `/server sw-1` picks one.
- **Secure by default.** Every move carries a signed, single-use pass, so players can't fake one.
- **See the whole network.** `/servers`, `/players` and `/find` show every server and who is where.
- **Servers join by themselves** with Redis or a free Cloudflare Worker, or list them in one file.
- **Keeps working** when the registry goes down, from the last known server list.

## Quick start

1. Download the jar from [releases](https://github.com/HiveScaleSystems/link/releases) and put it
   in `mods/` on every server.
2. Start each server once. Link writes `mods/Link_Link/link.json`.
3. List your servers and copy the file to every server. Only `serverId` differs per server.
4. Restart, then type `/servers` in game.

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

Every setting, and the Redis and Cloudflare setups, are in the [docs](https://link.beehivesys.net/docs).

## How servers find each other

| `backend` | You run | Live player counts and lists | New servers join |
|---|---|---|---|
| `static` | Nothing | No, only this server's players | By hand, in `link.json` |
| `redis` | A Redis, with a password | Yes | By themselves |
| `http` | Nothing, a free Cloudflare Worker ([`cloudflare-registry/`](cloudflare-registry)) | Yes | By themselves |

[![Deploy to Cloudflare](https://deploy.workers.cloudflare.com/button)](https://deploy.workers.cloudflare.com/?url=https://github.com/HiveScaleSystems/link/tree/main/cloudflare-registry)

## Commands

| Command | Who | Does |
|---|---|---|
| `/play <group>` | Everyone | Join the best server in a group |
| `/server <name>` | Everyone | Go to one server |
| `/servers [group]` | Everyone | List the network, or one group |
| `/players [server]` | Everyone | Who is online in the network, or on one server |
| `/find <player>` | Everyone | Which server a player is on |
| `/link` | Admins | Connection status |

## For plugin developers

```java
LinkPlugin.play(player, "skywars");           // matchmake into a group
LinkPlugin.send(player, "sw-1");              // to one server
LinkPlugin.send(player, "farm-1", "farm-42"); // into a world there
LinkPlugin.servers("lobby");                  // every lobby
LinkPlugin.find("Steve");                     // which server Steve is on, or null
```

Moves throw `LinkException` with a message you can show the player. See the
[Java API](https://link.beehivesys.net/docs/java-api).

## Security

A ticket is signed with the network secret, names one player and one server, expires after 30
seconds and works once. All servers share that secret, so this fits a network where you run every
server. If some servers are run by others, you need a separate key per server, which Link
doesn't do. Details in the [security model](https://link.beehivesys.net/docs/security).

## Development

```sh
./gradlew build              # plugin jar in LinkHytale/build/libs, runs the tests
cd dev && ./setup.sh         # two real servers and a Redis in Docker, see dev/README.md
```

| Folder | Holds |
|---|---|
| `LinkCore/` | Tickets, config, matchmaking and the registries. No game server dependency. |
| `LinkHytale/` | The Hytale plugin: commands, events and `LinkPlugin`. |
| `cloudflare-registry/` | The Worker behind the `http` registry. |
| `dev/` | A local test network. |

Bug reports, ideas and pull requests are welcome.
