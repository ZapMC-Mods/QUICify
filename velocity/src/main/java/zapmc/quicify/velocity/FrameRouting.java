package zapmc.quicify.velocity;

import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.BundleDelimiterPacket;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.Nullable;
import zapmc.quicify.quic.mux.BarrierClassifier;
import zapmc.quicify.quic.mux.PacketCategory;
import zapmc.quicify.quic.mux.PacketRoutingTable;

public final class FrameRouting {

    private static final int NO_ID = -1;

    private static final int UNRESOLVED = -2;

    private final PacketRoutingTable table;

    private final QuicMinecraftConnection connection;

    private final boolean clientbound;

    private final boolean framed;

    private int bundleDelimiterId = UNRESOLVED;

    private FrameRouting(PacketRoutingTable table, QuicMinecraftConnection connection, boolean clientbound, boolean framed) {
        this.table = table;
        this.connection = connection;
        this.clientbound = clientbound;
        this.framed = framed;
    }

    public static FrameRouting outbound(PacketRoutingTable table, QuicMinecraftConnection connection) {
        return new FrameRouting(table, connection, true, true);
    }

    public static FrameRouting inbound(PacketRoutingTable table, QuicMinecraftConnection connection) {
        return new FrameRouting(table, connection, false, false);
    }

    static int peekId(ByteBuf frame, boolean framed) {
        int cursor = frame.readerIndex();
        int end = frame.writerIndex();
        if (framed) {
            cursor = skipVarint(frame, cursor, end);
            if (cursor == NO_ID) {
                return NO_ID;
            }
        }
        return readVarint(frame, cursor, end);
    }

    private static int skipVarint(ByteBuf frame, int cursor, int end) {
        for (int read = 0; read < 5; read++) {
            if (cursor >= end) {
                return NO_ID;
            }
            if ((frame.getByte(cursor++) & 0x80) == 0) {
                return cursor;
            }
        }
        return NO_ID;
    }

    private static int readVarint(ByteBuf frame, int cursor, int end) {
        int value = 0;
        for (int read = 0; read < 5; read++) {
            if (cursor >= end) {
                return NO_ID;
            }
            byte current = frame.getByte(cursor++);
            value |= (current & 0x7F) << read * 7;
            if ((current & 0x80) == 0) {
                return value;
            }
        }
        return NO_ID;
    }

    public PacketCategory category(ByteBuf frame) {
        PacketRoutingTable.Phase phase = phase();
        int id = peekId(frame);
        if (phase == null || id < 0) {
            return PacketCategory.CONTROL;
        }
        return table.category(phase, clientbound, id);
    }

    public boolean isBundleDelimiter(ByteBuf frame) {
        if (phase() != PacketRoutingTable.Phase.PLAY) {
            return false;
        }
        int delimiter = bundleDelimiterId();
        return delimiter >= 0 && peekId(frame) == delimiter;
    }

    private int bundleDelimiterId() {
        if (bundleDelimiterId == UNRESOLVED) {
            try {
                bundleDelimiterId = StateRegistry.PLAY
                        .getProtocolRegistry(ProtocolUtils.Direction.CLIENTBOUND, connection.getProtocolVersion())
                        .getPacketId(BundleDelimiterPacket.INSTANCE);
            } catch (RuntimeException e) {
                bundleDelimiterId = NO_ID;
            }
        }
        return bundleDelimiterId;
    }

    public BarrierClassifier.@Nullable Barrier barrier(Object msg) {
        if (!(msg instanceof ByteBuf frame)) {
            return null;
        }
        PacketRoutingTable.Phase phase = phase();
        int id = peekId(frame);
        if (phase == null || id < 0 || !table.isBarrier(phase, clientbound, id)) {
            return null;
        }
        return new BarrierClassifier.Barrier(table.isTerminal(phase, clientbound, id),
                table.isPlayEntry(phase, clientbound, id));
    }

    private PacketRoutingTable.@Nullable Phase phase() {
        StateRegistry state = connection.getState();
        if (state == StateRegistry.PLAY) {
            return PacketRoutingTable.Phase.PLAY;
        }
        if (state == StateRegistry.CONFIG) {
            return PacketRoutingTable.Phase.CONFIGURATION;
        }
        return null;
    }

    private int peekId(ByteBuf frame) {
        return peekId(frame, framed);
    }
}
