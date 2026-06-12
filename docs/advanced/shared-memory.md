---
description: "VGI's POSIX shared-memory transport hands large Arrow batches between DuckDB and a Java worker without copying them through a pipe."
---

# Shared-memory transport

By default, Arrow batches travel between the engine and the worker over the
transport's pipe (the Unix socket or stdio). For large batches that copy is the
dominant cost. VGI can instead hand batches through a **POSIX shared-memory
segment** that both processes map — turning each transfer into a bulk memory
copy (and, optionally, a true zero-copy read).

The best part: **it needs no code changes in your worker.** It's negotiated at
the transport layer.

## How it works

![The client owns the segment and advertises it on init; the worker attaches it, writes each large batch into the segment, and sends a tiny pointer batch over the pipe for the client to read back.](/diagrams/shm-handshake.svg)

1. The client creates and owns a `shm_open` segment when
   `VGI_RPC_SHM_SIZE_BYTES` is set, and advertises its name/size on each `init`.
2. The worker attaches the segment (read/write) and, instead of streaming a big
   output batch down the pipe, writes it into shm and sends a zero-row **pointer
   batch** carrying the offset and length.
3. The client resolves the pointer batch by reading those bytes straight from the
   mapped segment. The same path runs **inbound** (client → worker) for large
   input/parameter batches.

If a batch doesn't fit, or is dictionary-encoded, or the segment is full, the
transport silently falls back to the inline pipe — correctness never depends on
shm being available.

## Enabling it

Set one environment variable on the **client** side (the engine process):

```bash
export VGI_RPC_SHM_SIZE_BYTES=67108864      # 64 MiB segment
```

That's it. The worker attaches automatically. Requirements:

- **JDK 22+** in the worker. The implementation uses the `java.lang.foreign`
  (FFM) API to `mmap` the segment; it's a multi-release overlay that activates
  only on 22+. On JDK 21 the worker transparently uses the pipe.
- The worker JVM must run with `--enable-native-access=ALL-UNNAMED` (the
  [examples](/intro/quickstart) bake this in).

| Variable | Effect |
|----------|--------|
| `VGI_RPC_SHM_SIZE_BYTES` | segment size in bytes; **enables** shm when set (client-side) |
| `VGI_RPC_SHM_DEBUG=1` | log attach / resolve / fallback events |
| `VGI_RPC_SHM_DISABLE=1` | force the pipe path even if a segment is advertised |

## Seeing it work

Running the [examples test](/intro/quickstart) with shm enabled and
`VGI_RPC_SHM_DEBUG=1`, the worker logs the segment attach and, on disconnect, a
summary:

```
[vgi-shm] attached vgi_shm_849b_fc565913_0 size=67108864 data_size=67043328
[vgi-shm] resolved inbound off=65536 len=304 rows=1 (copy)
[vgi-shm] conn closed …: outbound shm=1/1 batches (100.0%), inbound shm=1/1 batches (100.0%)
[vgi-shm] timeline …: worker-busy=4.7 ms (resolve=1.8 process=1.1 emit=1.7), … worker 74% / client 26%
```

Both directions went through shared memory, and the same SQL produced identical
results to the inline run — shm is an optimization, not a behavior change.

## When it helps

- **Big batches.** The win scales with batch size; tiny batches stay inline (the
  pointer-batch overhead isn't worth it below a threshold).
- **Throughput-bound workloads.** Scans and passthroughs that move a lot of bytes
  benefit most.

On a MacBook Air (M3), a 16 GB scan in 32 MB batches runs **2.82× faster** over shared
memory (1303 → 3673 MB/s), and a 4.8 GB echo round-trip **2.62× faster**. Those are
measured over multi-second, multi-gigabyte workloads (median of 9 warm runs) — see
[benchmarks](/advanced/benchmarks) for the methodology and the `bench.sh` script.
The win shrinks as per-row compute grows (the transfer becomes a smaller share of
the wall clock), so measure your own workload rather than assuming a fixed
multiplier.

## What you don't have to do

You don't allocate the segment, free it, or even know it exists. The client owns
the segment lifecycle (create + unlink); the worker only attaches. Your
`compute()` / `produceTick()` / `onInputBatch()` code is identical whether or not
shm is active.

Next: [filter & projection pushdown →](/advanced/filter-projection-pushdown)
