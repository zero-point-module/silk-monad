# Minecraft Paper Server (1.21.6)

Dockerized Paper server using [itzg/minecraft-server](https://github.com/itzg/docker-minecraft-server).

## Start

```bash
docker compose up -d
```

First boot downloads Paper 1.21.6 and generates the world into `./data`. Tail logs:

```bash
docker compose logs -f
```

## Connect

Multiplayer → `localhost:25565`.

## Console

```bash
docker attach minecraft-paper   # Ctrl-p Ctrl-q to detach
# or
docker compose exec minecraft rcon-cli
```

## Stop

```bash
docker compose down
```

World data persists in `./data`. Adjust `MEMORY` in `docker-compose.yml` to tune heap size.
