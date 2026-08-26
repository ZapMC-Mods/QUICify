package zapmc.quicify.quic;

import org.junit.jupiter.api.Test;
import zapmc.quicify.QuicAnnouncement;
import zapmc.quicify.QuicProtocol;
import zapmc.quicify.QuicifyConfig.ConnectMode;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuicConnectDecisionTest {

    private static final InetSocketAddress SERVER = new InetSocketAddress("127.0.0.1", 25565);

    private static QuicAnnouncement announcing(int port) {
        return new QuicAnnouncement(QuicProtocol.VERSION, port);
    }

    @Test
    void aPingWithoutAnAnnouncementGoesStraightToTcp() {
        QuicConnectDecision decision = QuicConnectDecision.resolve(true, null, SERVER, ConnectMode.AUTO);

        assertFalse(decision.attemptQuic());
    }

    @Test
    void aPingWithAnAnnouncementUsesTheAnnouncedPort() {
        QuicConnectDecision decision = QuicConnectDecision.resolve(true, announcing(25570), SERVER, ConnectMode.AUTO);

        assertTrue(decision.attemptQuic());
        assertEquals(25570, decision.target().getPort());
        assertEquals(SERVER.getAddress(), decision.target().getAddress());
    }

    @Test
    void anAnnouncementOnTheServerPortKeepsTheOriginalAddress() {
        QuicConnectDecision decision = QuicConnectDecision.resolve(true, announcing(25565), SERVER, ConnectMode.AUTO);

        assertTrue(decision.attemptQuic());
        assertSame(SERVER, decision.target());
    }

    @Test
    void aServerThatWasNeverPingedIsTriedOptimistically() {
        QuicConnectDecision decision = QuicConnectDecision.resolve(false, null, SERVER, ConnectMode.AUTO);

        assertTrue(decision.attemptQuic());
        assertSame(SERVER, decision.target());
    }

    @Test
    void forceQuicIgnoresAMissingAnnouncement() {
        QuicConnectDecision decision = QuicConnectDecision.resolve(true, null, SERVER, ConnectMode.FORCE_QUIC);

        assertTrue(decision.attemptQuic());
        assertSame(SERVER, decision.target());
    }

    @Test
    void anUnresolvedAddressKeepsItsHostnameWhenThePortChanges() {
        InetSocketAddress unresolved = InetSocketAddress.createUnresolved("mc.example.com", 25565);

        QuicConnectDecision decision = QuicConnectDecision.resolve(true, announcing(25570), unresolved, ConnectMode.AUTO);

        assertTrue(decision.attemptQuic());
        assertEquals("mc.example.com", decision.target().getHostString());
        assertEquals(25570, decision.target().getPort());
        assertTrue(decision.target().isUnresolved());
    }
}
