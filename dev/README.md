# Local test network

Two Hytale servers and a Redis in Docker, to test Link by joining with the game.

| Server | Group | Port |
|---|---|---|
| `lobby-1` | `lobby` | 5520 |
| `sw-1` | `skywars` | 5521 |

```sh
./setup.sh                        # build Link, install it and each server's link.json
docker compose up -d redis lobby  # first boot downloads the server (about 3.4 GB)
./setup.sh                        # copy the download to the game server
docker compose up -d game
```

Each server asks for a Hytale login once: open the `user_code` link from
`docker logs link-lobby` (then `link-game`) and approve it. The server saves the login.
Don't run `/auth login` yourself while one is pending, it cancels the first.

Join `127.0.0.1:5520`. After changing Link: `./setup.sh && docker compose restart lobby game`.
