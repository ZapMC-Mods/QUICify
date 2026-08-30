package zapmc.quicify.quic.mux;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface CategorySource {

    @Nullable PacketCategory current();
}
