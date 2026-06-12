---
description: "Reference for VGI worker CLI flags, LOCATION transport schemes, and environment variables."
---

# CLI & environment reference

## Worker CLI flags

`Worker.runFromArgs(args)` parses these. You usually don't pass them yourself —
the `launch:` `LOCATION` scheme appends `--unix`/`--idle-timeout` automatically.

| Flag | Meaning |
|------|---------|
| *(none)* | serve over **stdio** (the engine forks the worker) |
| `--unix <path>` | bind an **AF_UNIX** socket at `<path>`; print `UNIX:<path>` when ready |
| `--idle-timeout <seconds>` | self-exit after this many seconds with no connections (`AF_UNIX`); `0` = never |
| `--http` | serve **HTTP**; print `PORT:<n>` when ready |
| `--host <host>` | HTTP bind host (default `127.0.0.1`) |
| `--port <port>` | HTTP bind port (default `0` = ephemeral) |

Unknown flags exit with status 2.

## `LOCATION` schemes (client side)

What you write in `ATTACH ... (TYPE vgi, LOCATION '<here>')`:

| Scheme | Behavior |
|--------|----------|
| `launch:<argv>` | start the worker **once** behind a flock-coordinated Unix socket and pool it across all queries and processes. **Use this for JVM workers.** |
| `<path>` *(bare)* | fork the worker as a subprocess (stdio) per attach — pays JVM startup each time |
| `http://host:port` / `https://…` | connect to an already-running HTTP worker — local, **remote on another machine**, or reached from a browser-hosted [DuckDB-Wasm](/intro/anatomy-of-a-worker#local-remote-or-in-the-browser) engine |

## Environment variables

### Worker / transport (read by `vgi-java`)

| Variable | Purpose |
|----------|---------|
| `VGI_WORKER_STDERR` | append the worker's stderr to this file. The launcher dup2's `/dev/null` over fd 2, so set this to see crashes in `launch:` mode. |
| `VGI_WORKER_SHARED_STORAGE` | function-state backend for [buffering](/function-kinds/buffering) `storage`: `memory`, `sqlite` (default), or `cloudflare-do`. |
| `VGI_WORKER_SQLITE_PATH` | path to the SQLite file when the backend is `sqlite`. |

### Shared memory ([details](/advanced/shared-memory))

| Variable | Side | Purpose |
|----------|------|---------|
| `VGI_RPC_SHM_SIZE_BYTES` | client | segment size in bytes; **enables** the transport when set |
| `VGI_RPC_SHM_DEBUG` | worker | log attach / resolve / fallback events |
| `VGI_RPC_SHM_DISABLE` | worker | force the inline pipe even if a segment is advertised |
| `VGI_RPC_SHM_STATS` | worker | emit per-connection allocation statistics |
| `VGI_RPC_SHM_ZEROCOPY` | worker | decode shm batches as foreign buffers (no copy); off by default |

## Distribution layout

`./gradlew installDist` (application plugin) produces:

```
build/install/<project>/
  bin/<project>        # the launch script — point LOCATION 'launch:' at this
  bin/<project>.bat    # Windows
  lib/*.jar            # the worker + its dependencies
```

The launch script bakes in the JVM args from `applicationDefaultJvmArgs`
(the [required flags](/reference/jvm-flags)), so `launch:` workers start with
them already applied.

## Debugging a `launch:` worker

`launch:` workers are long-lived and shared. Two things to know:

- **See crashes:** set `VGI_WORKER_STDERR=/tmp/w.log` (fd 2 is otherwise
  `/dev/null`).
- **Pick up a rebuild:** the pooled worker keeps serving the old binary until it
  exits. After rebuilding, kill it (`pkill -f <your main class>`) or let the idle
  timeout expire, so the next attach launches the new binary. A stale pool shows
  up as "function does not exist" for newly added functions.
