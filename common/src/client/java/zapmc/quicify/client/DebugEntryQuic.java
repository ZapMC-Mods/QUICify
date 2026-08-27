package zapmc.quicify.client;

import io.netty.handler.codec.quic.QuicChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import zapmc.quicify.Quicify;
import zapmc.quicify.quic.QuicPathSampler;
import zapmc.quicify.quic.QuicifyConnection;
import zapmc.quicify.quic.mux.MuxStats;
import zapmc.quicify.quic.mux.PacketCategory;
import zapmc.quicify.quic.mux.QuicMuxSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DebugEntryQuic implements DebugScreenEntry {

    private static final Identifier GROUP = Identifier.fromNamespaceAndPath(Quicify.MOD_ID, "quic");

    private static final PacketCategory[] CATEGORIES = PacketCategory.values();

    private static final long RATE_WINDOW_MS = 1000L;

    private final long[] lastTx = new long[CATEGORIES.length];

    private final long[] lastRx = new long[CATEGORIES.length];

    private final long[] txRate = new long[CATEGORIES.length];

    private final long[] rxRate = new long[CATEGORIES.length];

    private @Nullable MuxStats sampled;

    private long sampledAt;

    @Override
    public void display(@NonNull DebugScreenDisplayer displayer, @Nullable Level serverOrClientLevel, @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        if (listener == null) {
            return;
        }
        Connection connection = listener.getConnection();
        QuicifyConnection duck = connection instanceof QuicifyConnection quic ? quic : null;
        boolean quic = duck != null && duck.quicify$isQuic();

        QuicChannel channel = quic ? duck.quicify$quicChannel() : null;
        MuxStats stats = MuxStats.of(channel);
        QuicMuxSession session = QuicMuxSession.of(channel);

        List<String> lines = new ArrayList<>();
        lines.add(status(quic, session));
        if (channel != null) {
            lines.add(path(channel));
        }
        if (stats != null) {
            sample(stats);
            for (PacketCategory category : CATEGORIES) {
                if (category.secondary() && session == null) {
                    continue;
                }
                lines.add(row(stats, category));
            }
        }
        displayer.addToGroup(GROUP, lines);
    }

    private String status(boolean quic, @Nullable QuicMuxSession session) {
        if (!quic) {
            return ChatFormatting.RED + Component.translatable("quicify.debug.inactive").getString();
        }
        String mux = session == null ? Component.translatable("quicify.debug.mux.off").getString() : Component.translatable("quicify.debug.mux", session.stateName().toLowerCase(Locale.ROOT)).getString();
        return ChatFormatting.GREEN + Component.translatable("quicify.debug.active").getString() + ChatFormatting.RESET + " - " + mux;
    }

    private String path(QuicChannel channel) {
        QuicPathSampler sampler = QuicPathSampler.of(channel);
        sampler.refresh(channel);
        if (!sampler.sampled()) {
            return Component.translatable("quicify.debug.path.pending").getString();
        }
        long sent = sampler.sent();
        double loss = sent == 0L ? 0.0 : sampler.lost() * 100.0 / sent;
        return Component.translatable("quicify.debug.path",
                String.format(Locale.ROOT, "%3d", sampler.rttMillis()),
                String.format(Locale.ROOT, "%4d", sampler.cwnd() / 1024L),
                String.format(Locale.ROOT, "%4.1f", loss)).getString();
    }

    private String row(MuxStats stats, PacketCategory category) {
        int index = category.ordinal();
        long streamId = stats.streamId(category);
        String id = streamId == MuxStats.NO_STREAM ? "-" : Long.toString(streamId);
        return String.format(Locale.ROOT, "%s #%s, %d tx, %d rx", category.name(), id, txRate[index], rxRate[index]);
    }

    private void sample(MuxStats stats) {
        long now = System.currentTimeMillis();
        if (stats != sampled) {
            sampled = stats;
            sampledAt = now;
            for (int i = 0; i < CATEGORIES.length; i++) {
                lastTx[i] = stats.txPackets(CATEGORIES[i]);
                lastRx[i] = stats.rxPackets(CATEGORIES[i]);
                txRate[i] = 0L;
                rxRate[i] = 0L;
            }
            return;
        }
        long elapsed = now - sampledAt;
        if (elapsed < RATE_WINDOW_MS) {
            return;
        }
        sampledAt = now;
        for (int i = 0; i < CATEGORIES.length; i++) {
            long tx = stats.txPackets(CATEGORIES[i]);
            long rx = stats.rxPackets(CATEGORIES[i]);
            txRate[i] = (tx - lastTx[i]) * 1000L / elapsed;
            rxRate[i] = (rx - lastRx[i]) * 1000L / elapsed;
            lastTx[i] = tx;
            lastRx[i] = rx;
        }
    }
}
