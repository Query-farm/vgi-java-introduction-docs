# Recipe: add a table-in-out function

**Goal:** add `drop_nulls(data TABLE) -> *` — passes input through but drops any
row whose first column is NULL.

## Prompt

> Add a VGI table-in-out function `drop_nulls` that emits each input batch with
> rows removed where the first column is NULL. Output schema equals input schema.
> Put it in `examples/`, register it, add a test, verify end-to-end.

## Steps

1. **Create** `DropNullsFunction.java` from
   [../skeletons/TableInOutSkeleton.java](../skeletons/TableInOutSkeleton.java).
   The key difference from echo: you emit fewer rows. Build the output by copying
   only the surviving row indices. Simplest correct approach — compute the
   surviving indices, then use each vector's `TransferPair` per-row copy, or build
   a selection and use `getTransferPair` + `copyValueSafe`:

   ```java
   @Override public void onInputBatch(AnnotatedBatch input, OutputCollector out, CallContext ctx) {
       VectorSchemaRoot in = input.root();
       FieldVector key = in.getFieldVectors().get(0);

       // surviving row indices
       List<Integer> keep = new ArrayList<>();
       for (int i = 0; i < in.getRowCount(); i++) if (!key.isNull(i)) keep.add(i);

       List<FieldVector> outVecs = new ArrayList<>();
       for (FieldVector src : in.getFieldVectors()) {
           FieldVector dst = src.getField().createVector(Allocators.root());
           dst.setInitialCapacity(keep.size());
           dst.allocateNew();
           for (int j = 0; j < keep.size(); j++) dst.copyFromSafe(keep.get(j), j, src);
           dst.setValueCount(keep.size());
           outVecs.add(dst);
       }
       VectorSchemaRoot copy = new VectorSchemaRoot(outVecs);
       copy.setRowCount(keep.size());
       out.emit(copy);
   }
   ```

   (`copyFromSafe` is fine for flat types; for nested LIST/STRUCT use a
   `TransferPair` + `splitAndTransfer` of contiguous runs instead.)

2. **Register**: `.registerTableInOut(new DropNullsFunction())`.

3. **Add a test** to `examples/test/examples.test`:

   ```
   query I
   SELECT v FROM demo.drop_nulls((SELECT * FROM (VALUES (1),(NULL),(3)) t(v))) ORDER BY v;
   ----
   1
   3
   ```

4. **Verify** with the loop in [README.md](README.md).

## Done when

- Compiles, harness green, and a NULL-only input yields zero rows (emit a
  zero-row batch or skip emit for an empty `keep`).

## Notes

- **Do not** `emit(input.root())` — see [AGENTS.md](../AGENTS.md) gotchas.
- Output schema equals input schema, so `PassthroughTIOFunction` is the right
  base and you don't override `onBind`.
