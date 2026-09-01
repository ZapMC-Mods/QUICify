package zapmc.quicify.quic.mux;

import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.common.CommonPacketTypes;
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
        assertEquals(PacketCategory.UI, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_CONTAINER_SET_SLOT));
        assertEquals(PacketCategory.AMBIENT, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_LEVEL_PARTICLES));
    }

    @Test
    void theChatChainStaysOnOneStream() {
        PacketCategory chat = PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_PLAYER_CHAT);
        assertEquals(chat, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_PLAYER_INFO_UPDATE), "player_chat resolves its sender and that sender's chat session out of player_info_update");
        assertEquals(chat, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_PLAYER_INFO_REMOVE), "a leaving player's last message still needs its player info");
        assertEquals(chat, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_DELETE_CHAT), "delete_chat unpacks from the signature cache player_chat fills, and disconnects when it cannot");
        assertEquals(chat, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_SYSTEM_CHAT));
        assertEquals(chat, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_DISGUISED_CHAT));
    }

    @Test
    void playerLoadedRidesWithTheActionsItUnlocks() {
        PacketCategory loaded = PacketRouting.categoryOf(GamePacketTypes.SERVERBOUND_PLAYER_LOADED);
        List<PacketType<?>> gated = List.of(
                GamePacketTypes.SERVERBOUND_PLAYER_ACTION,
                GamePacketTypes.SERVERBOUND_PLAYER_COMMAND,
                GamePacketTypes.SERVERBOUND_PLAYER_INPUT,
                GamePacketTypes.SERVERBOUND_USE_ITEM,
                GamePacketTypes.SERVERBOUND_USE_ITEM_ON,
                GamePacketTypes.SERVERBOUND_ATTACK,
                GamePacketTypes.SERVERBOUND_INTERACT,
                GamePacketTypes.SERVERBOUND_SPECTATOR_ACTION,
                GamePacketTypes.SERVERBOUND_SET_CARRIED_ITEM,
                GamePacketTypes.SERVERBOUND_MOVE_PLAYER_POS_ROT
        );
        for (PacketType<?> type : gated) {
            assertEquals(loaded, PacketRouting.categoryOf(type), type + " is gated on hasClientLoaded() and must not be able to overtake player_loaded");
        }
    }

    @Test
    void thirdPartyPayloadsShareTheLaneOfAnythingUnclassified() {
        assertEquals(PacketCategory.CONTROL, PacketRouting.categoryOf(CommonPacketTypes.CLIENTBOUND_CUSTOM_PAYLOAD));
        assertEquals(PacketCategory.CONTROL, PacketRouting.categoryOf(CommonPacketTypes.SERVERBOUND_CUSTOM_PAYLOAD));
        assertEquals(PacketCategory.CONTROL, PacketRouting.categoryOf(null), "custom_payload carries protocol whose causal edges cannot be known, so it takes the same lane as an id we cannot classify");
    }

    @Test
    void theBlockAckTrailsTheBlockUpdatesItResolves() {
        PacketCategory ack = PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_BLOCK_CHANGED_ACK);
        assertEquals(ack, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_BLOCK_UPDATE), "an ack that overtakes the block_update it resolves reverts the block to its pre-prediction state until the update lands");
        assertEquals(ack, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_SECTION_BLOCKS_UPDATE));
        assertEquals(ack, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_LEVEL_CHUNK_WITH_LIGHT));
    }

    @Test
    void theMountScreenTravelsWithTheContainerItOpens() {
        PacketCategory mount = PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_MOUNT_SCREEN_OPEN);
        assertEquals(mount, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_CONTAINER_SET_CONTENT), "openHorseInventory sends the screen and then initMenu's contents, and contents for an unopened container id are dropped");
        assertEquals(mount, PacketRouting.categoryOf(GamePacketTypes.CLIENTBOUND_CONTAINER_SET_SLOT));
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
