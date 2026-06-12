# vgi-java-introduction-docs

Introductory documentation for **VGI-Java** — serving
[Haybarn](https://query.farm) / DuckDB catalogs and functions from a Java
process over Apache Arrow IPC. Published at
**[vgi-java-introduction.query.farm](https://vgi-java-introduction.query.farm)**.

It ships three things, all built from the same set of verified example workers:

| Artifact | Path | What it is |
|----------|------|-----------|
| **HTML guide** | `docs/` | A [VitePress](https://vitepress.dev) site: intro → quickstart → one page per function kind → parallelism & shared memory → reference. |
| **Slide deck** | `slides/` | A [Slidev](https://sli.dev) talk covering the same arc. |
| **Runnable examples** | `examples/` | A standalone Gradle project — a minimal, compile-and-run worker for every function kind. The docs quote these verbatim. |
| **Agent pack** | `agents/` | `AGENTS.md` + task recipes + skeletons for coding agents writing new functions. |

## Quick start

Run the examples (no docs tooling needed):

```bash
cd examples
./run.sh            # build + print the Haybarn ATTACH SQL
```

Read or develop the HTML guide:

```bash
npm install
npm run docs:dev    # http://localhost:5173
```

Present the slides:

```bash
npm run slides:dev
```

## What's covered

- The five function kinds — **scalar**, **table**, **table-in-out**,
  **aggregate**, and **buffering** — each with a minimal, executable example.
- **Parallelism**: `maxWorkers` scan hints and the virtual-thread serving model.
- **Shared-memory transport**: zero-copy-ish batch handoff over POSIX shm.
- A reference for the worker CLI, every `VGI_*` environment variable, and the
  required JVM flags.

Every code sample is lifted from `examples/`, which is verified to compile
against the published `farm.query:vgi` artifact and to run end-to-end against
**Haybarn** (`uvx haybarn-cli`, with `INSTALL vgi FROM community`) — all five
function kinds, inline and over the shared-memory transport.

For the full, exhaustive fixture set (90+ functions exercising every protocol
corner), see the `vgi-example-worker` module in the
[vgi-java](https://github.com/Query-farm/vgi-java) repository.

## Deploying

The site is hosted on **Cloudflare Pages** at
[vgi-java-introduction.query.farm](https://vgi-java-introduction.query.farm)
(project `vgi-java-introduction`).

Pushes to `main` deploy automatically via
[`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) — set the repo
secrets `CLOUDFLARE_API_TOKEN` (with *Cloudflare Pages: Edit*) and
`CLOUDFLARE_ACCOUNT_ID`. To deploy by hand:

```bash
npm run docs:build
npx wrangler pages deploy docs/.vitepress/dist --project-name=vgi-java-introduction
```

The canonical host (Open Graph, `sitemap.xml`, `robots.txt`) is configured in
`docs/.vitepress/config.mts` (`HOSTNAME`).
