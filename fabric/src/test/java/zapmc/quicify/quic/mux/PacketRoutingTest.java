package zapmc.quicify.quic.mux;

import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.GamePacketTypes;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketRoutingTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static void collect(List<String> unclassified) {
        GameProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type, _) -> check(type, unclassified));
        GameProtocols.SERVERBOUND_TEMPLATE.details().listPackets((type, _) -> check(type, unclassified));
    }

    private static void check(PacketType<?> type, List<String> unclassified) {
        if (!PacketRouting.isClassified(type)) {
            unclassified.add(type.toString());
        }
    }

    @Test
    void everyPlayPacketIsClassified() {
        List<String> unclassified = new ArrayList<>();
        collect(unclassified);
        assertTrue(unclassified.isEmpty(), "unclassified PLAY packet types (add them to PacketRouting): " + unclassified);
    }

    @Test
    void barriersAndPlayEntriesStayOnControl() {
        assertEquals(PacketCategory.CONTROL, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_START_CONFIGURATION));
        assertEquals(PacketCategory.CONTROL, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_RESPAWN));
        assertTrue(PacketRouting.isBarrier(GamePacketTypes.CLIENTBOUND_LOGIN));
        assertTrue(PacketRouting.isPlayEntry(GamePacketTypes.CLIENTBOUND_RESPAWN));
        assertFalse(PacketRouting.isBarrier(GamePacketTypes.CLIENTBOUND_LEVEL_CHUNK_WITH_LIGHT));
    }

    @Test
    void hotPathsLandOnTheirOwnStreams() {
        assertEquals(PacketCategory.WORLD, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_LEVEL_CHUNK_WITH_LIGHT));
        assertEquals(PacketCategory.REALTIME, PacketRouting.categoryOf(GamePacketTypes.SERVERBOUND_MOVE_PLAYER_POS_ROT));
        assertEquals(PacketCategory.UI, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_SYSTEM_CHAT));
        assertEquals(PacketCategory.AMBIENT, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_LEVEL_PARTICLES));
    }

    @Test
    void everyCategoryHasADistinctWireId() {
        for (PacketCategory category : PacketCategory.values()) {
            if (category.secondary()) {
                assertEquals(category, PacketCategory.byWireId(category.wireId()));
                assertEquals(category, PacketCategory.bySecondaryIndex(category.secondaryIndex()));
            }
        }
        assertEquals(4, PacketCategory.SECONDARY_COUNT);
    }
}
