# ============================================================================
# MonadQuest — one-command developer iteration
# ============================================================================
# Quest master agent creates an on-chain quest (MON reward); 2 player agents
# race to find a secret hidden in a Minecraft chest and !claim it on-chain.
# The Minecraft server runs LOCALLY via Docker.
#
# ── Typical iteration flow ───────────────────────────────────────────────────
#   make install      # once: deps for blockchain/ + agent-backend/
#   make mc-up        # once per session: boot the local Paper server (Docker)
#   make dev          # over and over: reset quest state, then run the agents
#
#   make chain        # when contracts change: setup wallets + faucet + deploy
#   make mc-down      # when you're done: stop the Minecraft server
#
# ── cwd-relative config GOTCHA (the #1 footgun) ──────────────────────────────
#   The agents run FROM agent-backend/, and the runtime reads config relative to
#   the process cwd. So these MUST live under agent-backend/:
#       agent-backend/keys.json    (API keys + MONAD_RPC_URL + *_PRIVATE_KEY +
#                                    QUEST_FACTORY_ADDRESS once the factory is deployed)
#       agent-backend/tokens.json
#   The mock quest state file (blockchain/quests.js writes ./quests-mock.json,
#   cwd-relative) therefore lands at agent-backend/quests-mock.json.
#   `make reset` deletes THAT file for a fresh quest run — it never touches the
#   Minecraft world.
# ============================================================================

# Run every recipe in a single bash shell with strict flags so failures abort
# the recipe instead of silently continuing.
SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c

# Absolute repo root (dir containing this Makefile) so targets work from anywhere.
ROOT       := $(patsubst %/,%,$(dir $(abspath $(lastword $(MAKEFILE_LIST)))))
BLOCKCHAIN := $(ROOT)/blockchain
AGENTS     := $(ROOT)/agent-backend
COMPOSE    := $(ROOT)/minecraft/server/docker-compose.yml
QUEST_MOCK := $(AGENTS)/quests-mock.json

# Every target is a command, not a file.
.PHONY: help install chain mc-up mc-down mc-logs agents reset dev \
        _check-node _check-node-version _check-docker _check-forge

# Default goal: show help when you just run `make`.
.DEFAULT_GOAL := help

# ── help ─────────────────────────────────────────────────────────────────────
# Default target. Auto-generates the target list from the `## ` comments that
# trail each target name below, so this list can never drift from reality.
help: ## Show this help (list every target)
	@echo ""
	@echo "MonadQuest — developer iteration targets"
	@echo "========================================"
	@grep -hE '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| sort \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-10s\033[0m %s\n", $$1, $$2}' \
		|| true
	@echo ""
	@echo "Typical flow:  make install (once) -> make mc-up (once) -> make dev (repeat)"
	@echo "Fast loop:     make dev   (= reset quest state, then run the agents)"
	@echo ""

# ── prerequisite checks (internal) ───────────────────────────────────────────
# Each fails fast with a friendly, actionable message instead of a cryptic
# 'command not found' deep inside a recipe.
_check-node: # internal: require node + npm
	@command -v node >/dev/null 2>&1 || { \
		echo "ERROR: 'node' not found. Install Node.js 18+ (https://nodejs.org)."; exit 1; }
	@command -v npm  >/dev/null 2>&1 || { \
		echo "ERROR: 'npm' not found. It ships with Node.js (https://nodejs.org)."; exit 1; }

# internal: warn (don't fail) if Node > 20. The agent stack's OPTIONAL viewer/vision
# deps (prismarine-viewer / node-canvas-webgl) build the native 'gl' module, which only
# ships prebuilt binaries up to Node 20 (mindcraft-ce's supported range). On Node 21+ npm
# must compile gl from source — needing a bare `python` + a GL/build toolchain — and that
# is exactly the `make install` failure on bare-metal macOS. Node 20 (see .nvmrc) installs
# gl from prebuilts, so the viewer works too. We only WARN here: the install is made
# resilient by marking those deps optional in agent-backend/package.json, so a failed gl
# build no longer aborts `npm install` (the quest demo doesn't use the viewer/vision).
_check-node-version:
	@NODE_MAJOR="$$(node -p 'process.versions.node.split(".")[0]')"; \
	if [ "$$NODE_MAJOR" -gt 20 ]; then \
		echo "WARNING: Node v$$(node -v | sed 's/^v//') detected (> 20)."; \
		echo "         The optional in-browser viewer/vision deps need to compile native 'gl'"; \
		echo "         (no prebuilt binaries above Node 20), which can fail without python + a"; \
		echo "         build toolchain. The quest demo does NOT need the viewer, so install will"; \
		echo "         still succeed (those deps are optional)."; \
		echo "         RECOMMENDED: use the pinned Node from .nvmrc for full functionality:"; \
		echo "             nvm install && nvm use   # -> Node 20, then re-run make install"; \
	fi

_check-docker: # internal: require docker + a running daemon
	@command -v docker >/dev/null 2>&1 || { \
		echo "ERROR: 'docker' not found. Install Docker Desktop (https://docker.com)."; exit 1; }
	@docker compose version >/dev/null 2>&1 || { \
		echo "ERROR: 'docker compose' (v2) unavailable. Update Docker Desktop."; exit 1; }
	@docker info >/dev/null 2>&1 || { \
		echo "ERROR: Docker daemon not running. Start Docker Desktop and retry."; exit 1; }

_check-forge: # internal: warn (don't fail) if forge is missing — only needed to compile contracts
	@command -v forge >/dev/null 2>&1 || { \
		echo "WARNING: 'forge' not found — needed to COMPILE the Solidity contracts."; \
		echo "         Install Foundry: https://book.getfoundry.sh/getting-started/installation"; \
		echo "         (JS deps still install fine; you only need forge for 'make chain'.)"; }

# ── install ──────────────────────────────────────────────────────────────────
install: _check-node _check-node-version _check-forge ## Install deps in blockchain/ + agent-backend/ (forge needed to compile contracts)
	@echo ">> Installing blockchain/ dependencies..."
	@cd "$(BLOCKCHAIN)" && npm install
	@echo ">> Installing agent-backend/ dependencies..."
	@# The viewer/vision deps are OPTIONAL (see agent-backend/package.json): if the native
	@# 'gl' module can't build on this Node, npm skips them instead of failing the install.
	@cd "$(AGENTS)" && npm install
	@echo ">> Install complete."
	@echo "   Reminder: copy your keys into agent-backend/keys.json (cwd-relative config)."

# ── chain: contracts + wallets + funding ─────────────────────────────────────
# setup (generate wallets) -> faucet (fund deployer + agents with MON) ->
# deploy:quests (deploy the QuestFactory). deploy:quests is added by the on-chain
# teammate; tolerate it not existing yet so this target is usable beforehand.
chain: _check-node _check-forge ## Setup wallets, faucet MON, deploy QuestFactory (deploy:quests added by teammate)
	@echo ">> [1/3] Generating agent wallets (npm run setup)..."
	@cd "$(BLOCKCHAIN)" && npm run setup
	@echo ">> [2/3] Funding deployer + agents with testnet MON (npm run faucet)..."
	@cd "$(BLOCKCHAIN)" && npm run faucet
	@echo ">> [3/3] Deploying the QuestFactory (npm run deploy:quests)..."
	@cd "$(BLOCKCHAIN)" && \
		if npm run | grep -qE '^[[:space:]]*deploy:quests'; then \
			npm run deploy:quests; \
			echo ">> Deployed. Copy QUEST_FACTORY_ADDRESS into agent-backend/keys.json (cwd-relative!)."; \
		else \
			echo "SKIP: 'deploy:quests' script not in blockchain/package.json yet."; \
			echo "      The on-chain teammate adds it; rerun 'make chain' once it exists."; \
		fi

# ── Minecraft server (LOCAL, via Docker) ─────────────────────────────────────
mc-up: _check-docker ## Start the local Paper Minecraft server (docker compose up -d)
	@echo ">> Starting local Minecraft server (Docker)..."
	@docker compose -f "$(COMPOSE)" up -d
	@echo ">> Server starting on localhost:25565 (first boot pulls the image + world — give it a minute)."
	@echo "   Follow startup with: make mc-logs"

mc-down: _check-docker ## Stop the local Minecraft server (docker compose down) — keeps the world volume
	@echo ">> Stopping local Minecraft server..."
	@docker compose -f "$(COMPOSE)" down
	@echo ">> Server stopped. The world volume (minecraft/server/data) is preserved."

mc-logs: _check-docker ## Tail the Minecraft server logs (docker compose logs -f)
	@docker compose -f "$(COMPOSE)" logs -f

# ── agents ───────────────────────────────────────────────────────────────────
# `node main.js` spawns one process per agent and hosts the MindServer UI on
# :8080. Runs from agent-backend/ so cwd-relative config (keys.json, tokens.json,
# the factory address, quests-mock.json) resolves correctly.
agents: _check-node ## Run the agents (cd agent-backend && node main.js) — MindServer UI on :8080
	@echo ">> Starting agents from agent-backend/ (MindServer UI -> http://localhost:8080)..."
	@if [ ! -f "$(AGENTS)/keys.json" ]; then \
		echo "WARNING: agent-backend/keys.json not found — agents need it (cwd-relative)."; \
		echo "         Copy agent-backend/keys.example.json -> agent-backend/keys.json and fill it in."; \
	fi
	@echo "   Note: connects to the server at the 'host' in agent-backend/settings.js."
	@echo "         For the LOCAL Docker server set host to \"localhost\" (or MINECRAFT_PORT=25565)."
	@cd "$(AGENTS)" && node main.js

# ── reset: fresh quest state (NEVER touches the world) ───────────────────────
# Stops any running agents, then deletes the mock quest-state file so the next
# run starts with no quests. Deliberately does NOT touch the Minecraft world.
reset: ## Stop agents + delete agent-backend/quests-mock.json (fresh quest state; world untouched)
	@echo ">> Stopping any running agents (node main.js)..."
	@pkill -f "node main.js" 2>/dev/null && echo "   Stopped running agents." || echo "   No agents were running."
	@if [ -f "$(QUEST_MOCK)" ]; then \
		rm -f "$(QUEST_MOCK)"; \
		echo ">> Deleted $(QUEST_MOCK) — quest state is fresh."; \
	else \
		echo ">> No quests-mock.json to delete — quest state already fresh."; \
	fi
	@echo "   (The Minecraft world is untouched.)"

# ── dev: the fast inner loop ─────────────────────────────────────────────────
# Reset quest state, then start the agents. This is what you run over and over.
# Assumes `make mc-up` is already running (start it once per session).
dev: reset agents ## Fast inner loop: reset quest state, then run the agents (run this repeatedly)
