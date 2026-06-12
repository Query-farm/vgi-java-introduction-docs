# Recipe: add an aggregate function

**Goal:** add `vgi_max(value BIGINT) -> BIGINT` — the maximum value per group.

## Prompt

> Add a VGI aggregate function `vgi_max` over BIGINT that returns the max per
> group, NULL for an empty group. Put it in `examples/`, register it, add a test,
> verify end-to-end.

## Steps

1. **Create** `MaxFunction.java` from
   [../skeletons/AggregateSkeleton.java](../skeletons/AggregateSkeleton.java).
   The accumulator tracks the running max and whether it has seen any value:

   ```java
   public static final class State implements Serializable {
       private static final long serialVersionUID = 1L;
       boolean seen;
       long max;
   }

   @Override public void update(Map<Long,State> states, long[] gids, VectorSchemaRoot in) {
       BigIntVector v = (BigIntVector) in.getFieldVectors().get(0);
       for (int i = 0; i < in.getRowCount(); i++) {
           if (v.isNull(i)) continue;
           State s = states.computeIfAbsent(gids[i], k -> new State());
           if (!s.seen || v.get(i) > s.max) { s.max = v.get(i); s.seen = true; }
       }
   }

   @Override public void combine(State t, State s) {
       if (s.seen && (!t.seen || s.max > t.max)) { t.max = s.max; t.seen = true; }
   }

   @Override public void finalize(FieldVector result, int row, State s) {
       if (s.seen) ((BigIntVector) result).setSafe(row, s.max);
       else        ((BigIntVector) result).setNull(row);   // all-NULL group -> NULL
   }
   ```

   `combine` is associative and commutative — required, since partials merge in
   any order.

2. **Register**: `.registerAggregate(new MaxFunction())`.

3. **Add a test** to `examples/test/examples.test`:

   ```
   query II
   SELECT g, demo.vgi_max(v)
     FROM (VALUES (1,10),(1,30),(1,20),(2,5)) t(g,v) GROUP BY g ORDER BY g;
   ----
   1	30
   2	5
   ```

4. **Verify** with the loop in [README.md](README.md).

## Done when

- Compiles, harness green.
- A group of all-NULL values returns NULL (not 0, not a stale max).

## Notes

- The `seen` flag distinguishes "max is 0" from "no values" — don't initialize
  `max` to `Long.MIN_VALUE` and rely on that; it breaks for all-negative groups
  combined with empty partials.
- Don't store the input vector in `State`; copy the scalar out.
