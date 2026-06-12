# Recipe: add a scalar function

**Goal:** add `reverse_string(s VARCHAR) -> VARCHAR` to the example worker.

## Prompt

> Add a VGI scalar function `reverse_string` that reverses a UTF-8 string,
> preserving NULLs. Put it in the `examples/` project, register it on the
> AllInOneWorker, add an end-to-end test case, and verify it passes through the
> vgi C++ harness.

## Steps

1. **Create** `examples/src/main/java/farm/query/vgi/examples/ReverseStringFunction.java`,
   starting from [../skeletons/ScalarSkeleton.java](../skeletons/ScalarSkeleton.java).
   Use `VarCharVector` for input and output (see
   [ScalarExample.java](../../examples/src/main/java/farm/query/vgi/examples/ScalarExample.java)
   for the UTF-8 read/write idiom):

   ```java
   public final class ReverseStringFunction extends ScalarFn {
       @Override public String name() { return "reverse_string"; }
       @Override public String description() { return "Reverse a string"; }

       public void compute(@Vector VarCharVector value, VarCharVector result) {
           int rows = value.getValueCount();
           result.allocateNew();
           for (int i = 0; i < rows; i++) {
               if (value.isNull(i)) { result.setNull(i); continue; }
               String s = new String(value.get(i), StandardCharsets.UTF_8);
               byte[] r = new StringBuilder(s).reverse().toString()
                              .getBytes(StandardCharsets.UTF_8);
               result.setSafe(i, r, 0, r.length);
           }
       }
   }
   ```

2. **Register** it in `AllInOneWorker.java`:
   `.registerScalar(new ReverseStringFunction())`.

3. **Add a test case** to `examples/test/examples.test`:

   ```
   query I
   SELECT demo.reverse_string('abc');
   ----
   cba

   query I
   SELECT demo.reverse_string(x) FROM (VALUES ('ab'),(NULL)) t(x);
   ----
   ba
   NULL
   ```

4. **Verify** with the loop in [README.md](README.md). Expect `All tests passed`.

## Done when

- `./gradlew installDist` compiles.
- The harness reports the new assertions passing.

## Notes

- Reversing by `char` is fine for the test; note it splits surrogate pairs for
  non-BMP code points. Reverse by code point if that matters.
- `reverse_string` takes no const/setting args, so no `@Const`/`@Setting` needed.
