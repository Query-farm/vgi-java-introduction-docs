// Table skeleton — a fixed-schema, batched generator. Fill the TODOs.
// Docs: ../../docs/functions/table.md
package farm.query.vgi.examples;

import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.function.ParameterExtractor;
import farm.query.vgi.internal.BatchUtil;
import farm.query.vgi.pushdown.FilterApplier;
import farm.query.vgi.table.BatchState;
import farm.query.vgi.table.CountdownTableFunction;
import farm.query.vgi.table.TableInitParams;
import farm.query.vgi.table.TableProducerState;
import farm.query.vgi.types.Schemas;
import farm.query.vgirpc.CallContext;
import farm.query.vgirpc.OutputCollector;
import org.apache.arrow.vector.BigIntVector;          // TODO: your column vector types
import org.apache.arrow.vector.types.pojo.Schema;

public final class TableSkeleton extends CountdownTableFunction {

    // TODO: declare your output columns.
    private static final Schema OUTPUT_SCHEMA =
            Schemas.of(Schemas.nullable("n", Schemas.INT64));

    @Override public String name() { return "TODO_function_name"; }

    @Override public FunctionMetadata metadata() {
        // withPushdown(projection, filter, limit) — opt in only to what you exploit.
        return FunctionMetadata.describe("TODO one line").withPushdown(false, true, false);
    }

    @Override protected Schema outputSchema() { return OUTPUT_SCHEMA; }

    // 2048 = DuckDB's STANDARD_VECTOR_SIZE; match it so each emitted batch lines
    // up with one of the engine's vectors.
    @Override protected long defaultBatchSize() { return 2048L; }

    // Base class gives you `count` (positional) + `batch_size := 2048` (named).
    // Add your own named args here if needed:
    // @Override protected List<ArgSpec> extraArgs() {
    //     return List.of(ArgSpec.named("step", Schemas.INT64, "1"));
    // }

    @Override public TableProducerState createProducer(TableInitParams params) {
        ParameterExtractor p = ParameterExtractor.of(params.arguments());
        long count = p.positional(0, "count").asLong().required();
        long batchSize = p.named("batch_size").asLong().ge(1).orElse(2048L);
        return new State(new BatchState(count, batchSize),
                FilterApplier.from(params.pushdownFilters(), params.joinKeys()));
    }

    public static final class State extends TableProducerState {
        public BatchState batch;          // cursor — public, with a no-arg ctor (serialized)
        public FilterApplier filters;
        public State() {}
        State(BatchState batch, FilterApplier filters) { this.batch = batch; this.filters = filters; }

        @Override public void produceTick(OutputCollector out, CallContext ctx) {
            // BatchUtil emits one batch, applies filters, and calls out.finish() at the end.
            BatchUtil.produceBatch(batch, OUTPUT_SCHEMA, filters, out, (root, n, start) -> {
                BigIntVector v = (BigIntVector) root.getVector("n");
                v.allocateNew(n);
                for (int i = 0; i < n; i++) {
                    // TODO: fill row i; `start` is the first absolute index of this batch.
                    v.set(i, start + i);
                }
            });
        }
    }

    // Register:  .registerTable(new TableSkeleton())
}
