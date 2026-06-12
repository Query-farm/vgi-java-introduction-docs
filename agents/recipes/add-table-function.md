# Recipe: add a table function

**Goal:** add `fibonacci(count BIGINT) -> n BIGINT` — the first `count`
Fibonacci numbers, streamed in batches.

## Prompt

> Add a VGI table function `fibonacci(count)` that emits the first `count`
> Fibonacci numbers as a single BIGINT column `n`, in `batch_size`-sized batches.
> Put it in `examples/`, register it, add a test, and verify end-to-end.

## Steps

1. **Create** `FibonacciFunction.java` from
   [../skeletons/TableSkeleton.java](../skeletons/TableSkeleton.java). The base
   `CountdownTableFunction` already provides `count` + `batch_size`. Because
   Fibonacci is a running sequence, carry the running pair across ticks in the
   producer state — `start` tells you the absolute index of the batch's first
   row, so compute from there:

   ```java
   @Override public void produceTick(OutputCollector out, CallContext ctx) {
       BatchUtil.produceBatch(batch, OUTPUT_SCHEMA, filters, out, (root, n, start) -> {
           BigIntVector v = (BigIntVector) root.getVector("n");
           v.allocateNew(n);
           long a = fib(start), b = fib(start + 1);   // or carry a,b on the state
           for (int i = 0; i < n; i++) { v.set(i, a); long t = a + b; a = b; b = t; }
       });
   }
   ```

   (For large `count`, carry `a,b` as `long` fields on the state instead of
   recomputing `fib(start)`; guard overflow with `Math.addExact`.)

2. **Register**: `.registerTable(new FibonacciFunction())` in `AllInOneWorker`.

3. **Add a test** to `examples/test/examples.test`:

   ```
   query I
   SELECT * FROM demo.fibonacci(7) ORDER BY rowid;
   ----
   0
   1
   1
   2
   3
   5
   8

   query I
   SELECT count(*) FROM demo.fibonacci(1000);
   ----
   1000
   ```

4. **Verify** with the loop in [README.md](README.md).

## Done when

- Compiles, and the harness reports the new assertions passing.
- `SELECT count(*) FROM (SELECT * FROM demo.fibonacci(1000000) LIMIT 5)` returns 5
  (proves streaming + LIMIT pushdown).

## Notes

- Keep `metadata().withPushdown(false, true, false)` so LIMIT short-circuits.
- Fibonacci overflows int64 at n≈92; raise a clear `fibonacci: int64 overflow`
  there with `Math.addExact`.
