package zapmc.quicify.quic.mux;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface BarrierClassifier {

    @Nullable Barrier classify(Object msg);

    record Barrier(boolean terminal, boolean playEntry) {
    }
}
