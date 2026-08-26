package zapmc.quicify;

import net.fabricmc.api.ModInitializer;
import zapmc.quicify.quic.QuicAvailability;
import zapmc.quicify.quic.zstd.ZstdAvailability;

public class QuicifyFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Quicify.init();
        QuicifyConfigs.INSTANCE.getId();
        QuicAvailability.check();
        ZstdAvailability.check();
    }
}
