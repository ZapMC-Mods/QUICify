package zapmc.quicify.quic.mux;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamPriority;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.jspecify.annotations.Nullable;
import zapmc.quicify.Quicify;
import zapmc.quicify.QuicifyConfigs;
import zapmc.quicify.quic.zstd.QuicCompression;
import zapmc.quicify.quic.zstd.ZstdParams;

import java.util.ArrayDeque;
import java.util.Deque;

public final class QuicMuxSession {

    public static final AttributeKey<QuicMuxSession> KEY = AttributeKey.valueOf("quicify:mux_session");

    private static final int MAX_QUEUED_BYTES = 4 * 1024 * 1024;

    private final QuicChannel quicChannel;

    private final QuicStreamChannel master;

    private final boolean clientSide;

    private final MuxStats stats;

    private final String injectionPoint;

    private final QuicStreamChannel[] secondaries = new QuicStreamChannel[PacketCategory.SECONDARY_COUNT];

    private final boolean[] ready = new boolean[PacketCategory.SECONDARY_COUNT];

    private final boolean[] dirty = new boolean[PacketCategory.SECONDARY_COUNT];

    private final ChannelFuture[] shutdowns = new ChannelFuture[PacketCategory.SECONDARY_COUNT];

    private final Deque<QueuedWrite> queued = new ArrayDeque<>();

    private State state = State.IDLE;

    private int readyCount;

    private int drainingInputs;

    private int queuedBytes;

    private int generation;

    private boolean datagramsDirty;

    private boolean pendingArm;

    private @Nullable Runnable drainListener;

    private @Nullable ChannelHandlerContext routerContext;

    public QuicMuxSession(QuicChannel quicChannel, QuicStreamChannel master, boolean clientSide, MuxStats stats, String injectionPoint) {
        this.injectionPoint = injectionPoint;
        this.quicChannel = quicChannel;
        this.master = master;
        this.clientSide = clientSide;
        this.stats = stats;
        master.updatePriority(new QuicStreamPriority(PacketCategory.CONTROL.urgency(), PacketCategory.CONTROL.incremental()));
        master.closeFuture().addListener(_ -> {
            state = State.CLOSED;
            discardQueued();
        });
    }

    private static ChannelPromise adapt(QuicStreamChannel target, ChannelPromise promise) {
        if (promise.isVoid()) {
            return target.voidPromise();
        }
        ChannelPromise adapted = target.newPromise();
        adapted.addListener(future -> {
            if (future.isSuccess()) {
                promise.trySuccess();
            } else {
                promise.tryFailure(future.cause());
            }
        });
        return adapted;
    }

    public static @Nullable QuicMuxSession of(@Nullable QuicChannel channel) {
        return channel == null ? null : channel.attr(KEY).get();
    }

    public void bindRouter(ChannelHandlerContext ctx) {
        this.routerContext = ctx;
    }

    private void writeOnMaster(ByteBuf buf, ChannelPromise promise) {
        ChannelHandlerContext ctx = routerContext;
        if (ctx != null) {
            ctx.write(buf, promise);
        } else {
            master.write(buf, promise);
        }
    }

    private void flushMaster() {
        ChannelHandlerContext ctx = routerContext;
        if (ctx != null) {
            ctx.flush();
        } else {
            master.flush();
        }
    }

    public QuicStreamChannel master() {
        return master;
    }

    public String injectionPoint() {
        return injectionPoint;
    }

    public MuxStats stats() {
        return stats;
    }

    public String stateName() {
        return state.name();
    }

    public boolean active() {
        return state == State.ACTIVE;
    }

    public int generation() {
        return generation;
    }

    public boolean disabled() {
        return state == State.DISABLED || state == State.CLOSED;
    }

    public boolean acceptsSecondaries() {
        return state != State.DRAINING;
    }

    public void openSecondaries() {
        if (!clientSide || disabled()) {
            return;
        }
        for (PacketCategory category : new PacketCategory[]{
                PacketCategory.REALTIME, PacketCategory.UI, PacketCategory.AMBIENT, PacketCategory.WORLD}) {
            openSecondary(category);
        }
    }

    private void openSecondary(PacketCategory category) {
        quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, SecondaryStreams.clientInitializer(this, category)).addListener(future -> {
            if (!future.isSuccess()) {
                Quicify.LOGGER.warn("QUIC secondary stream {} could not be opened ({}), staying single-stream", category, String.valueOf(future.cause()));
                disable();
            }
        });
    }

    boolean registerSecondary(PacketCategory category, QuicStreamChannel stream) {
        int index = category.secondaryIndex();
        QuicStreamChannel existing = secondaries[index];
        if (disabled() || state == State.DRAINING || (existing != null && existing != stream && existing.isActive())) {
            stream.close();
            return false;
        }
        secondaries[index] = stream;
        stream.updatePriority(new QuicStreamPriority(category.urgency(), category.incremental()));
        return true;
    }

    void markReady(PacketCategory category) {
        int index = category.secondaryIndex();
        QuicStreamChannel stream = secondaries[index];
        if (ready[index] || stream == null) {
            return;
        }
        ready[index] = true;
        readyCount++;
        QuicCompression.installIfEnabled(quicChannel, stream.pipeline());
        maybeActivate();
    }

    public void installCompression(ZstdParams params) {
        for (int i = 0; i < secondaries.length; i++) {
            QuicStreamChannel stream = secondaries[i];
            if (ready[i] && stream != null && stream.isActive()) {
                QuicCompression.install(stream.pipeline(), params);
            }
        }
    }

    private void maybeActivate() {
        if (state != State.ARMED || readyCount < PacketCategory.SECONDARY_COUNT) {
            return;
        }
        state = State.ACTIVE;
        if (QuicifyConfigs.verbose()) {
            Quicify.LOGGER.info("QUIC multiplexing active ({} secondary streams)", PacketCategory.SECONDARY_COUNT);
        }
        flushQueue();
    }

    public void arm() {
        if (disabled()) {
            return;
        }
        if (state == State.DRAINING) {
            pendingArm = true;
            return;
        }
        if (state == State.IDLE) {
            state = State.ARMED;
            maybeActivate();
        }
    }

    public boolean routeDatagram(ByteBuf framed, ChannelPromise promise) {
        if (state != State.ACTIVE) {
            return false;
        }
        ByteBuf datagram = DatagramLane.wrap(quicChannel, framed, generation);
        if (datagram == null) {
            return false;
        }
        framed.release();
        promise.trySuccess();
        datagramsDirty = true;
        stats.recordDatagramTx();
        quicChannel.write(datagram, quicChannel.voidPromise());
        return true;
    }

    public void flushDatagrams() {
        if (datagramsDirty) {
            datagramsDirty = false;
            quicChannel.flush();
        }
    }

    public boolean route(PacketCategory category, ByteBuf buf, ChannelPromise promise) {
        if (category == PacketCategory.CONTROL) {
            return false;
        }
        switch (state) {
            case ACTIVE -> {
                QuicStreamChannel stream = secondaries[category.secondaryIndex()];
                if (stream == null || !stream.isActive()) {
                    return false;
                }
                dirty[category.secondaryIndex()] = true;
                stream.write(buf, adapt(stream, promise));
                return true;
            }
            case ARMED, DRAINING -> {
                if (queuedBytes + buf.readableBytes() > MAX_QUEUED_BYTES) {
                    Quicify.LOGGER.warn("QUIC multiplexing backlog exceeded {} bytes, staying single-stream", MAX_QUEUED_BYTES);
                    disable();
                    return false;
                }
                queuedBytes += buf.readableBytes();
                queued.addLast(new QueuedWrite(category, buf, promise));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void flushQueue() {
        QueuedWrite write;
        while ((write = queued.pollFirst()) != null) {
            queuedBytes -= write.buf().readableBytes();
            QuicStreamChannel stream = state == State.ACTIVE ? secondaries[write.category().secondaryIndex()] : null;
            if (stream != null && stream.isActive()) {
                dirty[write.category().secondaryIndex()] = true;
                stream.write(write.buf(), adapt(stream, write.promise()));
            } else {
                writeOnMaster(write.buf(), write.promise());
            }
        }
        queuedBytes = 0;
        flushSecondaries();
    }

    public void flushSecondaries() {
        for (int i = 0; i < dirty.length; i++) {
            if (dirty[i]) {
                dirty[i] = false;
                QuicStreamChannel stream = secondaries[i];
                if (stream != null) {
                    stream.flush();
                }
            }
        }
    }

    public void beginBarrier(boolean terminal) {
        if (terminal) {
            state = State.CLOSED;
            releaseQueue();
            return;
        }
        if (state != State.ACTIVE && state != State.ARMED) {
            return;
        }
        state = State.DRAINING;
        drainingInputs = 0;
        for (QuicStreamChannel stream : secondaries) {
            if (stream != null && stream.isActive()) {
                drainingInputs++;
            }
        }
    }

    public void finishBarrier() {
        if (state != State.DRAINING) {
            return;
        }
        for (int i = 0; i < secondaries.length; i++) {
            QuicStreamChannel stream = secondaries[i];
            if (stream != null && stream.isActive()) {
                shutdowns[i] = stream.shutdownOutput();
            }
        }
        if (drainingInputs == 0) {
            completeDrain();
        }
    }

    public void onDrainComplete(Runnable listener) {
        this.drainListener = listener;
    }

    public boolean draining() {
        return state == State.DRAINING && drainingInputs > 0;
    }

    void onSecondaryInputClosed() {
        if (state != State.DRAINING) {
            return;
        }
        if (--drainingInputs <= 0) {
            drainingInputs = 0;
            completeDrain();
        }
    }

    private void closeSecondary(int index, QuicStreamChannel stream) {
        ChannelFuture shutdown = shutdowns[index];
        shutdowns[index] = null;
        if (shutdown == null) {
            shutdown = stream.shutdownOutput();
        }
        shutdown.addListener(_ -> stream.close());
    }

    private void completeDrain() {
        for (int i = 0; i < secondaries.length; i++) {
            QuicStreamChannel stream = secondaries[i];
            secondaries[i] = null;
            ready[i] = false;
            dirty[i] = false;
            if (stream != null) {
                closeSecondary(i, stream);
            }
        }
        readyCount = 0;
        generation++;
        state = pendingArm ? State.ARMED : State.IDLE;
        pendingArm = false;
        openSecondaries();
        MuxStreams.drainPending(quicChannel, this);
        flushQueue();

        Runnable listener = drainListener;
        drainListener = null;
        if (listener != null) {
            listener.run();
        }
    }

    public void fail(String reason) {
        if (state == State.ACTIVE) {
            Quicify.LOGGER.warn("QUIC multiplexing failed while active ({}), closing the connection: routing on the master now would overtake packets already in flight on a secondary", reason);
            state = State.DISABLED;
            discardQueued();
            MuxStreams.drainPending(quicChannel, null);
            master.close();
            return;
        }
        Quicify.LOGGER.warn("QUIC multiplexing failed ({}), staying single-stream", reason);
        disable();
    }

    public void disable() {
        if (state == State.CLOSED) {
            return;
        }
        state = State.DISABLED;
        MuxStreams.drainPending(quicChannel, null);
        releaseQueue();
        for (int i = 0; i < secondaries.length; i++) {
            QuicStreamChannel stream = secondaries[i];
            secondaries[i] = null;
            ready[i] = false;
            if (stream != null) {
                closeSecondary(i, stream);
            }
        }
        readyCount = 0;
        drainingInputs = 0;

        Runnable listener = drainListener;
        drainListener = null;
        if (listener != null) {
            listener.run();
        }
    }

    private void releaseQueue() {
        QueuedWrite write;
        while ((write = queued.pollFirst()) != null) {
            writeOnMaster(write.buf(), write.promise());
        }
        queuedBytes = 0;
        flushMaster();
    }

    private void discardQueued() {
        QueuedWrite write;
        while ((write = queued.pollFirst()) != null) {
            ReferenceCountUtil.release(write.buf());
            write.promise().trySuccess();
        }
        queuedBytes = 0;
    }

    private enum State {
        IDLE,
        ARMED,
        ACTIVE,
        DRAINING,
        DISABLED,
        CLOSED
    }

    private record QueuedWrite(PacketCategory category, ByteBuf buf, ChannelPromise promise) {
    }
}
