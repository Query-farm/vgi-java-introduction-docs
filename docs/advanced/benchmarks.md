---
description: "Measured, reproducible throughput numbers for VGI Java workers — scalar and table functions over the pipe and the shared-memory transport."
---

# Benchmarks

These are **measured numbers**, reproducible with the
[`examples/bench.sh`](https://github.com/Query-farm/vgi-java) script in this repo,
not vendor claims. They isolate the cost of moving data between the engine and the
worker, with the [shared-memory transport](/advanced/shared-memory) off (the
inline pipe) and on.

<!--@include: ../_snippets/install-haybarn.md-->

::: warning Read these as orders of magnitude
Benchmarks are workload- and machine-specific. The point here is the *shape* of
the result — shared memory wins on large, transport-bound batches — not the exact
millisecond. Run `./bench.sh` on your own hardware and workload before quoting a
number.
:::

## Setup

| | |
|---|---|
| Machine | MacBook Air (M3, 8 cores, 24 GiB) |
| OS | macOS (Darwin 24.6.0, arm64) |
| JDK | OpenJDK 25 |
| Engine | Haybarn 1.5.3 (DuckDB 1.5.3) via `uvx haybarn-cli` |
| Worker | the `AllInOneWorker` from `examples/`, attached with `launch:` |
| Method | `.timer on`; **median of 9 warm runs** after 2 warmups (LOAD/ATTACH discarded); `[min–max]` shown |

### Why such large workloads?

The figures below move **gigabytes**, so each run takes seconds. That's
deliberate. At sub-second times, scheduler jitter, JIT state, GC, and timer
granularity are a large fraction of the measurement, and a single median hides
that. Multi-second runs push the noise floor down, and reporting the `[min–max]`
spread makes whatever noise remains visible instead of smoothing it away. Both
workloads use **4 M-row (32 MB) batches** — well above the shared-memory
threshold; tiny batches stay inline by design and aren't a useful test of the
transport. `numbers` scans on up to **4 threads** ([parallel-safe via a shared
counter](/advanced/parallelism)), which is why the scan is sized at 2 B rows to
stay multi-second.

## Results

Two workloads, each timed with shared memory off, then on.

### Scan — 2B rows (16 GB), one direction

`sum(n) FROM numbers(2B)`: the worker generates 2 billion `BIGINT`s across 4
parallel scan threads and streams them to the engine in 32 MB batches (worker →
engine only).

| Metric | Inline | Shared memory |
|--------|-------:|--------------:|
| Median time | 12.28 s | **4.36 s** |
| Range (min–max) | 10.8–18.3 s | 3.9–5.6 s |
| Throughput | 1303 MB/s | 3673 MB/s |
| Rows / second | 163M | 459M |
| Speedup | — | **2.82×** |

Cheap per-row work, so it's transport-bound: writing each batch into the segment
instead of the pipe nearly triples throughput. (Parallelism lifts *both* paths —
inline hits 163M rows/s — so shm's *relative* edge is a touch smaller than on a
single-threaded scan, even as absolute throughput climbs.)

### Round-trip — 200M rows (4.8 GB), both directions

`count(*) FROM echo(numbers(200M))`: generate 200 M rows, feed them back into
`echo`, read the output. Three transfers (≈4.8 GB), exercising both **inbound**
and **outbound** shared memory.

| Metric | Inline | Shared memory |
|--------|-------:|--------------:|
| Median time | 8.60 s | **3.29 s** |
| Range (min–max) | 7.2–9.6 s | 2.9–4.2 s |
| Throughput | 558 MB/s | 1460 MB/s |
| Rows / second | 23M | 61M |
| Speedup | — | **2.62×** |

`echo` is single-worker (`maxWorkers` 1), so the round-trip is gated by its
`TransferPair` copy and engine-side work, not by how fast `numbers` can generate.

::: tip The spread matters too
The shm runs are tighter than inline; the inline scan is the noisiest cell here
(10.8–18.3 s). Under heavy parallel load the pipe's wall-clock is both slower
*and* less predictable, which is exactly why the `[min–max]` is shown.
:::

## Function throughput

The two workloads above also answer "how fast can a function go?" — they *are*
functions (`numbers` is a table function, `echo` a table-in-out function). Adding
a scalar makes the picture complete:

| Function | Kind | Inline | Shared memory |
|----------|------|-------:|--------------:|
| `numbers(n)` | table | 163M rows/s | **459M rows/s** |
| `upper_case(s)` | scalar | 9M rows/s | **11M rows/s** |

- **`numbers`** is a numeric generator: cheap per row, so it's transport-bound, and
  it scans on 4 threads — this is the 2B-row scan above.
- **`upper_case`** processes 50M strings (`sum(length(upper_case(i::VARCHAR)))`, so
  the optimizer can't prune the call). It's an order of magnitude slower per row,
  because each row carries real Unicode work *and* a variable-length string in both
  directions. Shared memory still helps (≈1.2×), but the per-row cost — not the
  pipe — is now the ceiling.

The takeaway: a table function delivering simple columns runs at hundreds of
millions of rows per second; a scalar doing real per-row work over strings runs at
tens of millions. Both are measured by `bench.sh`.

## Reproduce it

```bash
cd examples
./bench.sh
```

`bench.sh` builds the worker, runs each workload with shm off then on
(`VGI_RPC_SHM_SIZE_BYTES`), discards `WARMUP` runs, times `MEASURED` warm runs,
and prints `median [min–max]` plus derived throughput. Tweak the row counts and
`batch_size` to model your own data shape.

## How to read this for your workload

Shared memory helps in proportion to how transport-bound you are:

- **Big batches, cheap per-row work** (scans, passthroughs, projections) → largest
  win, as above.
- **Heavy per-row compute** (a parse, a model call) → the transfer is a smaller
  fraction, so the end-to-end win shrinks even though the transfer itself is still
  faster.
- **Tiny batches** → no win; they stay inline.

So the lever isn't "turn on shm and everything is 3× faster" — it's "shm removes
the pipe-copy cost, which matters when that copy is what you're spending time on."
For the worker-side CPU breakdown (generation vs. copy), set
`VGI_RPC_SHM_DEBUG=1` and read the per-connection `timeline` line.

See [shared memory](/advanced/shared-memory) for how the transport works and how
to enable it.
