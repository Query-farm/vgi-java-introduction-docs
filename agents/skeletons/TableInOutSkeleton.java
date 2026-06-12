// Table-in-out skeleton — a per-batch streaming transform. Fill the TODOs.
// Docs: ../../docs/functions/table-in-out.md
//
// This skeleton is passthrough (output schema == input schema). For a different
// output schema, implement TableInOutFunction directly and return the real
// schema from onBind().
package farm.query.vgi.examples;

import farm.query.vgi.function.ArgSpec;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.tableinout.PassthroughTIOFunction;
import farm.query.vgi.tableinout.TableInOutExchangeState;
import farm.query.vgi.tableinout.TableInOutInitParams;
import farm.query.vgirpc.AnnotatedBatch;
import farm.query.vgirpc.CallContext;
import farm.query.vgirpc.OutputCollector;
import farm.query.vgirpc.wire.Allocators;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.util.TransferPair;

import java.util.ArrayList;
import java.util.List;

public final class TableInOutSkeleton extends PassthroughTIOFunction {

    @Override public String name() { return "TODO_function_name"; }
    @Override public FunctionMetadata metadata() {
        return FunctionMetadata.describe("TODO one line");   // .withPushdown(true,false,false) for projection
    }

    @Override public List<ArgSpec> argumentSpecs() {
        return List.of(ArgSpec.table("data", 0));   // a single TABLE-valued argument
    }

    @Override public TableInOutExchangeState createExchange(TableInOutInitParams params) {
        return new Exchange();
    }

    public static final class Exchange extends TableInOutExchangeState {
        @Override public void onInputBatch(AnnotatedBatch input, OutputCollector out, CallContext ctx) {
            VectorSchemaRoot in = input.root();
            // RULE: never emit(in) directly — the reader reuses `in` for the next
            // batch. Transfer the vectors you want into a fresh root you own.
            List<FieldVector> outVectors = new ArrayList<>();
            for (FieldVector v : in.getFieldVectors()) {
                // TODO: keep, drop, or transform columns here.
                TransferPair tp = v.getTransferPair(Allocators.root());
                tp.transfer();
                outVectors.add((FieldVector) tp.getTo());
            }
            VectorSchemaRoot copy = new VectorSchemaRoot(outVectors);
            copy.setRowCount(in.getRowCount());   // TODO: adjust if you filtered rows
            out.emit(copy);                        // emit() owns and closes `copy`
        }
    }

    // Register:  .registerTableInOut(new TableInOutSkeleton())
}
