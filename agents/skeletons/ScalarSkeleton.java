// Scalar skeleton — copy into src/main/java/.../<YourName>.java and fill the TODOs.
// Docs: ../../docs/functions/scalar.md
package farm.query.vgi.examples;

import farm.query.vgi.scalar.ScalarFn;
import farm.query.vgi.scalar.Vector;
// import farm.query.vgi.scalar.Const;     // for bind-time constant args
// import farm.query.vgi.scalar.Setting;   // for session settings
import org.apache.arrow.vector.BigIntVector;   // TODO: swap for your input/output vector types

public final class ScalarSkeleton extends ScalarFn {

    @Override public String name() { return "TODO_function_name"; }   // SQL name (snake_case)
    @Override public String description() { return "TODO one line"; }

    // Parameter rules:
    //   @Vector <ArrowVector> in   -> a per-row input column
    //   @Const  <java type>   c    -> a bind-time constant (long/double/String/boolean/byte[])
    //   @Setting<java type>   s    -> a session setting (optional default_ = "...")
    //   last unannotated vector    -> the output (framework-allocated, sized to the batch)
    // Parameter NAMES become SQL arg names — build with -parameters.
    public void compute(@Vector BigIntVector value, BigIntVector result) {
        int rows = value.getValueCount();
        result.allocateNew(rows);
        for (int i = 0; i < rows; i++) {
            if (value.isNull(i)) { result.setNull(i); continue; }
            long v = value.get(i);
            // TODO: compute the output for row i
            result.set(i, v);
        }
    }

    // Optional: override when the output type depends on the input/const args.
    // @Override protected ArrowType outputType(Schema inputSchema, Arguments args) { ... }

    // Register it on your Worker.builder():  .registerScalar(new ScalarSkeleton())
}
