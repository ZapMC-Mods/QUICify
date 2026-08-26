package zapmc.quicify.quic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.RegistryOps;
import org.junit.jupiter.api.Test;
import zapmc.quicify.QuicAnnouncement;
import zapmc.quicify.QuicProtocol;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class QuicifyStatusCodecTest {

    private static final RegistryOps<JsonElement> OPS = RegistryAccess.EMPTY.createSerializationContext(JsonOps.INSTANCE);

    private static ServerStatus status() {
        return new ServerStatus(CommonComponents.EMPTY, Optional.empty(), Optional.empty(), Optional.empty(), false);
    }

    @Test
    void encodeAppendsAnnouncementWhenPublished() {
        Codec<ServerStatus> wrapped = QuicifyStatusCodec.wrap(ServerStatus.CODEC);
        try {
            QuicServerState.publish(25565);
            JsonElement encoded = wrapped.encodeStart(OPS, status()).getOrThrow();
            JsonObject announcement = encoded.getAsJsonObject().getAsJsonObject(QuicProtocol.STATUS_FIELD);
            assertNotNull(announcement);
            assertEquals(QuicProtocol.VERSION, announcement.get("v").getAsInt());
            assertEquals(25565, announcement.get("port").getAsInt());
        } finally {
            QuicServerState.clear();
        }
    }

    @Test
    void encodeOmitsAnnouncementWhenNotPublished() {
        QuicServerState.clear();
        Codec<ServerStatus> wrapped = QuicifyStatusCodec.wrap(ServerStatus.CODEC);
        JsonElement encoded = wrapped.encodeStart(OPS, status()).getOrThrow();
        assertFalse(encoded.getAsJsonObject().has(QuicProtocol.STATUS_FIELD));
    }

    @Test
    void decodeToleratesAndVanillaIgnores() {
        QuicServerState.publish(25565);
        try {
            Codec<ServerStatus> wrapped = QuicifyStatusCodec.wrap(ServerStatus.CODEC);
            JsonElement encoded = wrapped.encodeStart(OPS, status()).getOrThrow();
            QuicAnnouncement announcement = QuicAnnouncement.fromJson(encoded.getAsJsonObject().get(QuicProtocol.STATUS_FIELD));
            assertNotNull(announcement);
            assertEquals(25565, announcement.port());

            ServerStatus decoded = wrapped.decode(OPS, encoded).getOrThrow().getFirst();
            assertEquals(CommonComponents.EMPTY, decoded.description());
        } finally {
            QuicServerState.clear();
        }
    }
}
