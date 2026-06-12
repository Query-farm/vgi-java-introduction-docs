// Buffering skeleton — Sink + Source (see ALL input before emitting). Fill TODOs.
// Docs: ../../docs/functions/buffering.md
package farm.query.vgi.examples;

import farm.query.vgi.buffering.BufferingFinalizeProducer;
import farm.query.vgi.buffering.TableBufferingCombineParams;
import farm.query.vgi.buffering.TableBufferingFinalizeParams;
import farm.query.vgi.buffering.TableBufferingFunction;
import farm.query.vgi.buffering.TableBufferingProcessParams;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.function.FunctionSpec;
import farm.query.vgi.internal.BatchUtil;
import farm.query.vgi.internal.SchemaUtil;
import farm.query.vgi.protocol.BindResponse;
import farm.query.vgi.storage.FunctionStorage;
import farm.query.vgi.table.TableProducerState;
import farm.query.vgi.tableinout.TableInOutBindParams;
import farm.query.vgirpc.CallContext;
import farm.query.vgirpc.OutputCollector;
import farm.query.vgirpc.wire.Allocators;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class BufferingSkeleton implements TableBufferingFunction {

    private static final byte[] NS = "buf".getBytes(StandardCharsets.UTF_8);   // your storage namespace
    private static final byte[] KEY = new byte[0];

    private static final FunctionSpec SPEC = FunctionSpec.builder("TODO_function_name")
            .metadata(FunctionMetadata.describe("TODO one line"))
            .table("data")
            .build();

    @Override public FunctionSpec spec() { return SPEC; }

    // Output schema. This skeleton is passthrough (output == input).
    @Override public BindResponse onBind(TableInOutBindParams params) {
        Schema in = params.inputSchema();
        Schema out = (in == null || in.getFields().isEmpty()) ? new Schema(List.of()) : in;
        return BindResponse.forSchema(SchemaUtil.serializeSchema(out));
    }

    // SINK: stash each batch; return a state_id. Storage is durable + execution-scoped,
    // so parallel sink threads can all write here safely.
    @Override public byte[] process(VectorSchemaRoot batch, TableBufferingProcessParams params) {
        params.storage().stateAppend(NS, KEY, BatchUtil.writeSingleBatch(batch));
        // TODO: for top-k / running aggregates, update storage incrementally instead of
        //       buffering every batch.
        return params.executionId();
    }

    // COMBINE: group state_ids into the finalize streams the Source will drain.
    @Override public List<byte[]> combine(List<byte[]> stateIds, TableBufferingCombineParams params) {
        return List.of(params.executionId());   // one output stream
    }

    // SOURCE: emit the buffered result, one batch per tick.
    @Override public TableProducerState createFinalizeProducer(TableBufferingFinalizeParams params) {
        return new Producer(params);
    }

    private static final class Producer extends BufferingFinalizeProducer {
        private long afterId = -1;
        Producer(TableBufferingFinalizeParams params) { super(params); }

        @Override public void produceTick(OutputCollector out, CallContext ctx) {
            List<FunctionStorage.LogEntry> rows = storage.stateLogScan(NS, KEY, afterId, 1);
            if (rows.isEmpty()) { out.finish(); return; }
            FunctionStorage.LogEntry e = rows.get(0);
            VectorSchemaRoot full = BatchUtil.readSingleBatch(e.value(), Allocators.root());
            // TODO: transform here for a real whole-relation pass (sort, top-k, …).
            emitProjected(full, out);   // narrows to projected columns + applies filters
            full.close();
            afterId = e.id();
        }
    }

    // Register:  .registerTableBuffering(new BufferingSkeleton())
}
