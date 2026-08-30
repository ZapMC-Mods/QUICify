package zapmc.quicify.velocity;

import org.junit.jupiter.api.Test;
import zapmc.quicify.quic.mux.PacketCategory;
import zapmc.quicify.quic.mux.PacketRoutingTable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketRoutingTableTest {

    private static final int PROTOCOL = 776;

    @Test
    void theCommittedTableIsReadableWithoutMinecraft() {
        PacketRoutingTable table = PacketRoutingTable.forProtocol(PROTOCOL);
        assertNotNull(table, "the committed routing table should describe protocol " + PROTOCOL);
    }

    @Test
    void anotherProtocolVersionHasNoTable() {
        assertNull(PacketRoutingTable.forProtocol(PROTOCOL + 1));
    }

    @Test
    void barriersAndPlayEntriesAreOnControl() {
        PacketRoutingTable table = PacketRoutingTable.forProtocol(PROTOCOL);
        assertNotNull(table);

        boolean sawBarrier = false;
        boolean sawPlayEntry = false;
        for (int id = 0; id < 256; id++) {
            if (table.isBarrier(PacketRoutingTable.Phase.PLAY, true, id)) {
                sawBarrier = true;
                assertEquals(PacketCategory.CONTROL, table.category(PacketRoutingTable.Phase.PLAY, true, id), "a barrier has to stay on the master stream, id " + id);
            }
            if (table.isPlayEntry(PacketRoutingTable.Phase.PLAY, true, id)) {
                sawPlayEntry = true;
                assertTrue(table.isBarrier(PacketRoutingTable.Phase.PLAY, true, id), "a play entry is also a barrier, id " + id);
            }
        }
        assertTrue(sawBarrier, "the table should carry clientbound PLAY barriers");
        assertTrue(sawPlayEntry, "the table should carry clientbound PLAY entries");
    }

    @Test
    void anUnknownIdStaysOnTheMasterStream() {
        PacketRoutingTable table = PacketRoutingTable.forProtocol(PROTOCOL);
        assertNotNull(table);

        assertEquals(PacketCategory.CONTROL, table.category(PacketRoutingTable.Phase.PLAY, true, 9999));
        assertEquals(PacketCategory.CONTROL, table.category(PacketRoutingTable.Phase.PLAY, true, -1));
        assertFalse(table.isBarrier(PacketRoutingTable.Phase.PLAY, true, 9999));
    }

    @Test
    void everySecondaryCategoryIsUsedByTheClientboundPlayTable() {
        PacketRoutingTable table = PacketRoutingTable.forProtocol(PROTOCOL);
        assertNotNull(table);

        for (PacketCategory category : PacketCategory.values()) {
            if (!category.secondary()) {
                continue;
            }
            boolean used = false;
            for (int id = 0; id < 256 && !used; id++) {
                used = table.category(PacketRoutingTable.Phase.PLAY, true, id) == category;
            }
            assertTrue(used, "no clientbound PLAY packet routes to " + category);
        }
    }
}
