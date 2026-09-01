package zapmc.quicify.quic.mux;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.quic.QuicChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuicMuxSessionTest {

    private EmbeddedChannel parent;

    private StubStream master;

    private List<StubStream> secondaries;

    private QuicMuxSession session;

    @BeforeEach
    void setUp() {
        parent = new EmbeddedChannel();
        master = new StubStream(0);
        secondaries = new ArrayList<>();
        QuicChannel quicChannel = MuxStubs.quicChannel(parent);
        session = new QuicMuxSession(quicChannel, master.handle, false, new MuxStats(null), "splitter");
    }

    @AfterEach
    void tearDown() {
        for (StubStream secondary : secondaries) {
            secondary.channel.finishAndReleaseAll();
        }
        master.channel.finishAndReleaseAll();
        parent.finishAndReleaseAll();
    }

    private void register() {
        for (int i = 0; i < PacketCategory.SECONDARY_COUNT; i++) {
            StubStream secondary = new StubStream(4L * (i + 1));
            secondaries.add(secondary);
            session.registerSecondary(PacketCategory.bySecondaryIndex(i), secondary.handle);
        }
    }

    private void markEveryStreamReady() {
        for (int i = 0; i < PacketCategory.SECONDARY_COUNT; i++) {
            session.markReady(PacketCategory.bySecondaryIndex(i));
        }
    }

    private void activate() {
        session.arm();
        register();
        markEveryStreamReady();
    }

    private StubStream secondary(PacketCategory category) {
        return secondaries.get(category.secondaryIndex());
    }

    private ByteBuf payload(int size) {
        return Unpooled.wrappedBuffer(new byte[size]);
    }

    private ChannelPromise promise() {
        return master.channel.newPromise();
    }

    @Test
    void aSecondaryWriteIsQueuedWhileArmedAndReachesItsOwnStreamOnActivation() {
        session.arm();
        register();
        assertEquals("ARMED", session.stateName());

        ByteBuf buf = payload(16);
        assertTrue(session.route(PacketCategory.WORLD, buf, promise()));
        assertTrue(secondary(PacketCategory.WORLD).channel.outboundMessages().isEmpty(), "an ARMED write reached its secondary before the stream was ready");
        assertTrue(master.channel.outboundMessages().isEmpty(), "an ARMED write went down the master and would overtake bytes already on a secondary");

        markEveryStreamReady();

        assertEquals("ACTIVE", session.stateName());
        assertSame(buf, secondary(PacketCategory.WORLD).channel.readOutbound());
        assertTrue(master.channel.outboundMessages().isEmpty());
    }

    @Test
    void anActiveWriteGoesStraightToItsCategoryStream() {
        activate();
        assertEquals("ACTIVE", session.stateName());

        ByteBuf buf = payload(16);
        assertTrue(session.route(PacketCategory.REALTIME, buf, promise()));
        session.flushSecondaries();

        assertSame(buf, secondary(PacketCategory.REALTIME).channel.readOutbound());
        assertTrue(secondary(PacketCategory.WORLD).channel.outboundMessages().isEmpty());
        assertTrue(master.channel.outboundMessages().isEmpty());
    }

    @Test
    void controlNeverLeavesTheMasterAndADisabledSessionRoutesNothing() {
        activate();

        ByteBuf control = payload(8);
        assertFalse(session.route(PacketCategory.CONTROL, control, promise()));
        control.release();

        session.disable();
        assertEquals("DISABLED", session.stateName());

        ByteBuf afterDisable = payload(8);
        assertFalse(session.route(PacketCategory.WORLD, afterDisable, promise()));
        afterDisable.release();
    }

    @Test
    void theBacklogCapDisablesTheSessionAndFlushesWhatWasQueuedOnTheMaster() {
        session.arm();
        register();

        List<ByteBuf> queued = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ByteBuf buf = payload(1024 * 1024);
            queued.add(buf);
            assertTrue(session.route(PacketCategory.WORLD, buf, promise()));
        }

        ByteBuf overflow = payload(1);
        assertFalse(session.route(PacketCategory.WORLD, overflow, promise()), "the backlog cap did not reject the write that crossed it");
        overflow.release();

        assertEquals("DISABLED", session.stateName());
        for (ByteBuf buf : queued) {
            assertSame(buf, master.channel.readOutbound(), "a queued write was dropped instead of going out on the master");
        }
        assertTrue(secondary(PacketCategory.WORLD).channel.outboundMessages().isEmpty());
    }

    @Test
    void aBarrierShutsEverySecondaryDownAndOnlyCompletesOnceEveryInputHasClosed() {
        activate();

        boolean[] drained = {false};
        session.onDrainComplete(() -> drained[0] = true);
        session.beginBarrier(false);
        session.finishBarrier();

        assertEquals("DRAINING", session.stateName());
        assertTrue(session.draining());
        for (StubStream secondary : secondaries) {
            assertEquals(1, secondary.shutdownOutputs, "a barrier left a secondary output open");
        }

        for (int i = 0; i < PacketCategory.SECONDARY_COUNT - 1; i++) {
            session.onSecondaryInputClosed();
            assertTrue(session.draining(), "the drain completed before every secondary input had closed");
            assertFalse(drained[0]);
        }

        session.onSecondaryInputClosed();
        assertTrue(drained[0], "the drain listener never ran");
        assertFalse(session.draining());
        assertEquals("IDLE", session.stateName());
        for (StubStream secondary : secondaries) {
            assertFalse(secondary.channel.isOpen(), "a drained secondary stream was left open");
        }
    }

    @Test
    void aSecondaryOfferedDuringADrainIsRefusedInsteadOfReplacingTheOneBeingDrained() {
        activate();
        StubStream draining = secondary(PacketCategory.WORLD);

        session.beginBarrier(false);
        session.finishBarrier();
        assertFalse(session.acceptsSecondaries());

        StubStream fresh = new StubStream(20L);
        secondaries.add(fresh);
        assertFalse(session.registerSecondary(PacketCategory.WORLD, fresh.handle), "a next-generation stream was registered mid-drain");
        assertFalse(fresh.channel.isOpen(), "the refused stream was left open");

        for (int i = 0; i < PacketCategory.SECONDARY_COUNT; i++) {
            session.onSecondaryInputClosed();
        }

        assertEquals("IDLE", session.stateName());
        assertFalse(draining.channel.isOpen(), "the drain never closed the stream it was draining");
        assertTrue(session.acceptsSecondaries());
    }

    @Test
    void aDrainedSecondaryIsOnlyClosedOnceItsFinHasGoneOut() {
        activate();
        StubStream world = secondary(PacketCategory.WORLD);
        world.blockShutdown = true;

        session.beginBarrier(false);
        session.finishBarrier();
        for (int i = 0; i < PacketCategory.SECONDARY_COUNT; i++) {
            session.onSecondaryInputClosed();
        }

        assertEquals("IDLE", session.stateName());
        assertEquals(1, world.shutdownOutputs, "the drain shut the same output down twice");
        assertTrue(world.channel.isOpen(), "the stream was closed with its FIN still queued, dropping everything queued behind it");

        world.pendingShutdown.setSuccess();
        assertFalse(world.channel.isOpen(), "the stream was never closed once its FIN went out");
    }

    @Test
    void aSecondaryOfferedAfterDisableIsRefused() {
        activate();
        session.disable();

        StubStream fresh = new StubStream(24L);
        secondaries.add(fresh);
        assertFalse(session.registerSecondary(PacketCategory.UI, fresh.handle));
        assertFalse(fresh.channel.isOpen());
    }

    @Test
    void aFailureWhileActiveClosesTheConnectionInsteadOfReorderingOntoTheMaster() {
        activate();
        assertEquals("ACTIVE", session.stateName());

        session.fail("secondary stream UI failed");

        assertTrue(session.disabled());
        assertFalse(master.channel.isOpen(), "a mid-flight mux failure kept the connection and started routing on the master");
    }

    @Test
    void aFailureWithNothingInFlightStillDegradesToSingleStream() {
        session.arm();
        register();

        ByteBuf buf = payload(16);
        assertTrue(session.route(PacketCategory.UI, buf, promise()));

        session.fail("secondary stream UI failed");

        assertEquals("DISABLED", session.stateName());
        assertTrue(master.channel.isOpen(), "a failure with nothing in flight should not close the connection");
        assertSame(buf, master.channel.readOutbound());
    }

    @Test
    void disablingDuringADrainStillReleasesTheDrainListener() {
        activate();

        boolean[] drained = {false};
        session.onDrainComplete(() -> drained[0] = true);
        session.beginBarrier(false);
        session.finishBarrier();
        assertTrue(session.draining());

        session.disable();

        assertTrue(drained[0], "disable() left the barrier gate waiting on a drain that can never complete");
        assertEquals("DISABLED", session.stateName());
        assertFalse(session.draining());
    }

    @Test
    void armingDuringADrainIsAppliedOnlyWhenTheDrainCompletes() {
        activate();

        session.beginBarrier(false);
        session.finishBarrier();
        session.arm();
        assertEquals("DRAINING", session.stateName());

        for (int i = 0; i < PacketCategory.SECONDARY_COUNT; i++) {
            session.onSecondaryInputClosed();
        }

        assertEquals("ARMED", session.stateName());
    }

    @Test
    void aTerminalBarrierClosesTheSessionAndSendsWhatWasQueuedOnTheMaster() {
        session.arm();
        register();

        ByteBuf buf = payload(16);
        assertTrue(session.route(PacketCategory.UI, buf, promise()));

        session.beginBarrier(true);

        assertEquals("CLOSED", session.stateName());
        assertTrue(session.disabled());
        assertSame(buf, master.channel.readOutbound());
    }

    @Test
    void closingTheMasterDiscardsQueuedBuffersInsteadOfLeakingThem() {
        session.arm();
        register();

        ByteBuf buf = payload(16);
        ChannelPromise promise = promise();
        assertTrue(session.route(PacketCategory.AMBIENT, buf, promise));

        master.channel.close().syncUninterruptibly();

        assertEquals("CLOSED", session.stateName());
        assertEquals(0, buf.refCnt(), "a queued buffer leaked when the master stream closed");
        assertTrue(promise.isSuccess());
        assertNull(master.channel.readOutbound());
    }
}
