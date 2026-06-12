// Aggregate skeleton — parallel partial aggregation. Fill the TODOs.
// Docs: ../../docs/functions/aggregate.md
package farm.query.vgi.examples;

import farm.query.vgi.aggregate.AggregateFunction;
import farm.query.vgi.function.FunctionSpec;
import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.BigIntVector;          // TODO: your input/output vector types
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public final class AggregateSkeleton implements AggregateFunction<AggregateSkeleton.State> {

    // Per-group accumulator. Serializable: partials may be merged across workers.
    // Keep it small; store plain values, NOT Arrow vectors.
    public static final class State implements Serializable {
        private static final long serialVersionUID = 1L;
        long acc;   // TODO: your accumulator fields
    }

    private static final Schema OUTPUT_SCHEMA =
            new Schema(List.of(Schemas.nullable("result", Schemas.INT64)));   // TODO: output type

    private static final FunctionSpec SPEC = FunctionSpec.builder("TODO_function_name")
            .description("TODO one line")
            .arg("value", Schemas.INT64)   // TODO: declare input args (omit for a nullary aggregate)
            .build();

    @Override public FunctionSpec spec() { return SPEC; }
    @Override public Schema outputSchema() { return OUTPUT_SCHEMA; }
    @Override public State newState() { return new State(); }

    // Fold one batch into the per-group accumulators. groupIds[i] is row i's group.
    @Override public void update(Map<Long, State> states, long[] groupIds, VectorSchemaRoot input) {
        FieldVector fv = input.getFieldVectors().get(0);
        if (!(fv instanceof BigIntVector v)) return;
        for (int i = 0; i < input.getRowCount(); i++) {
            if (v.isNull(i)) continue;
            State s = states.computeIfAbsent(groupIds[i], k -> new State());
            // TODO: fold v.get(i) into s
            s.acc = Math.addExact(s.acc, v.get(i));
        }
    }

    // Merge a partial into target. MUST be associative + commutative.
    @Override public void combine(State target, State source) {
        target.acc = Math.addExact(target.acc, source.acc);
    }

    // Write one group's final value.
    @Override public void finalize(FieldVector result, int rowIndex, State state) {
        ((BigIntVector) result).setSafe(rowIndex, state.acc);
    }

    // Optional: result for an empty group (default NULL). e.g. COUNT returns 0.
    // @Override public void finalizeEmpty(FieldVector result, int rowIndex) {
    //     ((BigIntVector) result).setSafe(rowIndex, 0L);
    // }

    // Register:  .registerAggregate(new AggregateSkeleton())
}
