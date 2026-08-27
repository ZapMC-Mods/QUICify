package zapmc.quicify.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import zapmc.quicify.Quicify;
import zapmc.quicify.QuicifyConfigs;
import zapmc.quicify.quic.QuicAvailability;
import zapmc.quicify.quic.zstd.ZstdAvailability;

@Mod(Quicify.MOD_ID)
public class QuicifyNeoForge {

    public QuicifyNeoForge(IEventBus modEventBus) {
        Quicify.init(FMLPaths.CONFIGDIR.get());
        QuicifyConfigs.INSTANCE.getId();
        QuicAvailability.check();
        ZstdAvailability.check();
    }
}
