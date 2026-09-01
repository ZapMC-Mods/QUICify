package zapmc.quicify.velocity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.proxy.network.Connections;
import com.velocitypowered.proxy.protocol.packet.StatusResponsePacket;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import zapmc.quicify.QuicAnnouncement;
import zapmc.quicify.QuicProtocol;
import zapmc.quicify.Quicify;
import zapmc.quicify.quic.QuicServerState;

@ChannelHandler.Sharable
public final class StatusAnnouncer extends ChannelOutboundHandlerAdapter {

    public static final String NAME = "quicify_status";

    public static final StatusAnnouncer INSTANCE = new StatusAnnouncer();

    private StatusAnnouncer() {
    }

    public static void install(Channel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(Connections.MINECRAFT_ENCODER) != null && pipeline.get(NAME) == null) {
            pipeline.addAfter(Connections.MINECRAFT_ENCODER, NAME, INSTANCE);
        }
    }

    private static String announce(String json, QuicAnnouncement announcement) {
        try {
            JsonObject response = JsonParser.parseString(json).getAsJsonObject();
            response.add(QuicProtocol.STATUS_FIELD, announcement.toJson());
            return response.toString();
        } catch (RuntimeException e) {
            Quicify.LOGGER.warn("QUIC announcement could not be added to the status response ({})", e.toString());
            return json;
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof StatusResponsePacket status) {
            QuicAnnouncement announcement = QuicServerState.announcement();
            if (announcement != null) {
                msg = new StatusResponsePacket(announce(status.getStatus(), announcement));
            }
        }
        ctx.write(msg, promise);
    }
}
