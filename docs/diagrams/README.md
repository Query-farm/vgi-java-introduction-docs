# Diagram sources

`*.d2` are [D2](https://d2lang.com) source files. They render to committed SVGs
under `../public/diagrams/`, which the docs embed. The SVGs are checked in so the
VitePress site builds without the `d2` binary.

Regenerate after editing a source:

```bash
npm run diagrams          # needs the `d2` CLI: brew install d2
```

`npm run diagrams` runs `build.sh`, which renders each `.d2` and then strips the
base64 WOFF fonts d2 embeds (a per-diagram decode that delays text painting) and
points the text at the system font stack. That cuts each SVG ~5× and matches the
site font.

| Source | Rendered | Used on |
|--------|----------|---------|
| `architecture.d2` | `architecture.svg` | intro/what-is-vgi |
| `rpc-lifecycle.d2` | `rpc-lifecycle.svg` | intro/anatomy-of-a-worker |
| `shm-handshake.d2` | `shm-handshake.svg` | advanced/shared-memory |
| `partial-aggregation.d2` | `partial-aggregation.svg` | functions/aggregate |
