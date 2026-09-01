package zapmc.quicify.quic.mux;

import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.game.GamePacketTypes;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketRoutingExportTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static String committed() throws Exception {
        try (InputStream stream = PacketRoutingExportTest.class.getResourceAsStream("/" + PacketRoutingExporter.RESOURCE)) {
            assertNotNull(stream, PacketRoutingExporter.RESOURCE + " is missing, run :fabric:generatePacketRouting");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private static int idOf(net.minecraft.network.protocol.PacketType<?> type) {
        int[] found = {-1};
        net.minecraft.network.protocol.game.GameProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((candidate, id) -> {
            if (candidate == type) {
                found[0] = id;
            }
        });
        assertTrue(found[0] >= 0, "no id for " + type);
        return found[0];
    }

    @Test
    void theCommittedTableMatchesTheRoutingMap() throws Exception {
        assertEquals(PacketRoutingExporter.export(), committed(),
                "the routing table is out of date, run :fabric:generatePacketRouting");
    }

    @Test
    void theTableAgreesWithTheRoutingMapItWasBuiltFrom() {
        PacketRoutingTable table = PacketRoutingTable.forProtocol(SharedConstants.getProtocolVersion());
        assertNotNull(table, "the committed table does not describe this protocol version");

        int chunk = idOf(GamePacketTypes.CLIENTBOUND_LEVEL_CHUNK_WITH_LIGHT);
        int login = idOf(GamePacketTypes.CLIENTBOUND_LOGIN);
        int chat = idOf(GamePacketTypes.CLIENTBOUND_SYSTEM_CHAT);

        assertEquals(PacketCategory.WORLD, table.category(PacketRoutingTable.Phase.PLAY, true, chunk));
        assertEquals(PacketCategory.UI, table.category(PacketRoutingTable.Phase.PLAY, true, chat));
        assertEquals(PacketCategory.CONTROL, table.category(PacketRoutingTable.Phase.PLAY, true, login));
        assertTrue(table.isBarrier(PacketRoutingTable.Phase.PLAY, true, login));
        assertTrue(table.isPlayEntry(PacketRoutingTable.Phase.PLAY, true, login));
    }

    @Test
    void anUnknownProtocolVersionHasNoTable() {
        assertNull(PacketRoutingTable.forProtocol(SharedConstants.getProtocolVersion() + 1));
    }
}
