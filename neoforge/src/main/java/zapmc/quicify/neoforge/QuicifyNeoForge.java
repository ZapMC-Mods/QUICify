package zapmc.quicify.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import zapmc.quicify.Quicify;

/**
 * NeoForge entry point for the Quicify mod. This class is picked up by NeoForge's mod loader
 * via the {@link Mod} annotation and just hands off to the common module's init logic.
 * All actual QUIC transport logic lives in {@link Quicify}, not here.
 */
@Mod(Quicify.MOD_ID)
public class QuicifyNeoForge {

    public QuicifyNeoForge(IEventBus modEventBus) {
        Quicify.init();
    }
}
