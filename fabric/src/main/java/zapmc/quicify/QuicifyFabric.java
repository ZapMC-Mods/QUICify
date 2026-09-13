package zapmc.quicify;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import zapmc.quicify.quic.QuicAvailability;
import zapmc.quicify.quic.zstd.ZstdAvailability;

public class QuicifyFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Quicify.init(FabricLoader.getInstance().getConfigDir());
        QuicifyFzzyConfigs.install();
        QuicAvailability.check();
        ZstdAvailability.check();
    }
}
