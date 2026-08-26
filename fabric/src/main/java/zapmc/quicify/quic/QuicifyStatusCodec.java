package zapmc.quicify.quic;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.network.protocol.status.ServerStatus;
import zapmc.quicify.QuicAnnouncement;
import zapmc.quicify.QuicProtocol;
import zapmc.quicify.QuicifyServerStatus;

public final class QuicifyStatusCodec {

    public static Codec<ServerStatus> wrap(Codec<ServerStatus> vanilla) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<ServerStatus, T>> decode(DynamicOps<T> ops, T input) {
                QuicAnnouncement announcement = null;
                if (input instanceof JsonElement json && json.isJsonObject()) {
                    announcement = QuicAnnouncement.fromJson(json.getAsJsonObject().get(QuicProtocol.STATUS_FIELD));
                }
                DataResult<Pair<ServerStatus, T>> result = vanilla.decode(ops, input);
                if (announcement != null) {
                    QuicAnnouncement captured = announcement;
                    result = result.map(pair -> pair.mapFirst(status -> {
                        if ((Object) status instanceof QuicifyServerStatus quicStatus) {
                            quicStatus.quicify$setAnnouncement(captured);
                        }
                        return status;
                    }));
                }
                return result;
            }

            @Override
            public <T> DataResult<T> encode(ServerStatus input, DynamicOps<T> ops, T prefix) {
                return vanilla.encode(input, ops, prefix).map(encoded -> {
                    QuicAnnouncement announcement = QuicServerState.announcement();
                    if (announcement != null && encoded instanceof JsonElement json && json.isJsonObject()) {
                        json.getAsJsonObject().add(QuicProtocol.STATUS_FIELD, announcement.toJson());
                    }
                    return encoded;
                });
            }
        };
    }
}
