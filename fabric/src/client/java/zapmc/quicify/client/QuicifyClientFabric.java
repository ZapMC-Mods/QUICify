package zapmc.quicify.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.resources.Identifier;
import zapmc.quicify.Quicify;

public class QuicifyClientFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        DebugScreenEntries.register(Identifier.fromNamespaceAndPath(Quicify.MOD_ID, "quic"), new DebugEntryQuic());
    }
}
