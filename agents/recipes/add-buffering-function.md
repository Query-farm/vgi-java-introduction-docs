# Recipe: add a buffering function

**Goal:** add `top_n(data TABLE, n BIGINT) -> *` — emits the `n` rows with the
largest value in the first column. This is the canonical "needs all input"
operation.

## Prompt

> Add a VGI buffering function `top_n(data, n)` that buffers all input, then
> emits the n rows with the largest first-column value (descending). Put it in
> `examples/`, register it, add a test, verify end-to-end.

## Steps

1. **Create** `TopNFunction.java` from
   [../skeletons/BufferingSkeleton.java](../skeletons/BufferingSkeleton.java).
   The skeleton buffers every batch in `process`; the real work goes in the
   finalize producer, which now reads **all** buffered batches, keeps the top-n,
   and emits them. Read `n` from the bind arguments and carry it via
   `params.initParams()`.

   Sketch of the producer:

   ```java
   @Override public void produceTick(OutputCollector out, CallContext ctx) {
       if (done) { out.finish(); return; }
       // 1. drain every buffered batch into a bounded max-heap of (key,row-copy)
       // 2. emit one output batch holding the surviving rows, descending
       // 3. done = true so the next tick finishes
   }
   ```

   For an introductory version, collect all rows then sort — correctness first.
   Note the input arg list now has a positional `n`, so the spec is:

   ```java
   FunctionSpec.builder("top_n")
       .metadata(FunctionMetadata.describe("Top n rows by the first column"))
       .table("data")
       .arg("n", Schemas.INT64)      // a scalar arg alongside the table arg
       .build();
   ```

2. **Register**: `.registerTableBuffering(new TopNFunction())`.

3. **Add a test** to `examples/test/examples.test`:

   ```
   query I
   SELECT v FROM demo.top_n((SELECT * FROM (VALUES (5),(1),(9),(3),(7)) t(v)), 2)
   ORDER BY v DESC;
   ----
   9
   7
   ```

4. **Verify** with the loop in [README.md](README.md).

## Done when

- Compiles, harness green, and `top_n(..., 0)` emits zero rows.

## Notes

- **Buffer through `params.storage()`**, never a field — the Sink may be
  parallel. The skeleton already does this.
- Close every `VectorSchemaRoot` you read from storage (the skeleton's
  `full.close()`); `emitProjected`/`emit` own what they emit.
- This is the function kind where the work *must* be in the Source phase: you
  can't pick the global top-n until you've seen every row. That's why it's
  buffering and not table-in-out.
