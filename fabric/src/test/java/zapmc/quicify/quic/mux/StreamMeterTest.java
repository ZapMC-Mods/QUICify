package zapmc.quicify.quic.mux;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamMeterTest {

    private static void release(EmbeddedChannel channel) {
        channel.finishAndReleaseAll();
    }

    @Test
    void feedsTheVanillaBandwidthMonitorWithWireBytes() {
        LocalSampleLogger logger = new LocalSampleLogger(1);
        BandwidthDebugMonitor monitor = new BandwidthDebugMonitor(logger);
        MuxStats stats = new MuxStats(monitor::onReceive);
        EmbeddedChannel channel = new EmbeddedChannel(new StreamMeter(stats, PacketCategory.WORLD));
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(new byte[400]));
            channel.writeInbound(Unpooled.wrappedBuffer(new byte[112]));
            monitor.tick();

            assertEquals(1, logger.size());
            assertEquals(512, logger.get(0), "the meter must report the bytes as they arrive off the stream");
        } finally {
            release(channel);
        }
    }

    @Test
    void countsOneTxPerWriteAndBindsTheStreamColumn() {
        MuxStats stats = new MuxStats(null);
        EmbeddedChannel channel = new EmbeddedChannel(new StreamMeter(stats, PacketCategory.UI));
        try {
            channel.writeOutbound(Unpooled.wrappedBuffer(new byte[32]));
            channel.writeOutbound(Unpooled.wrappedBuffer(new byte[8]));

            assertEquals(2, stats.txPackets(PacketCategory.UI));
            assertEquals(0, stats.rxPackets(PacketCategory.UI));
        } finally {
            release(channel);
        }
    }

    @Test
    void staysSilentWithoutAMonitor() {
        MuxStats stats = new MuxStats(null);
        EmbeddedChannel channel = new EmbeddedChannel(new StreamMeter(stats, PacketCategory.CONTROL));
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(new byte[64]));
            ByteBuf forwarded = channel.readInbound();
            assertEquals(64, forwarded.readableBytes(), "the meter must pass the buffer on untouched");
            forwarded.release();
        } finally {
            release(channel);
        }
    }
}
