package zapmc.quicify;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuicAnnouncementTest {

    private static JsonObject announcement(int version, int port) {
        JsonObject json = new JsonObject();
        json.addProperty("v", version);
        json.addProperty("port", port);
        return json;
    }

    @Test
    void readsBackWhatItWrote() {
        QuicAnnouncement parsed = QuicAnnouncement.fromJson(new QuicAnnouncement(QuicProtocol.VERSION, 25565).toJson());

        assertNotNull(parsed);
        assertEquals(QuicProtocol.VERSION, parsed.version());
        assertEquals(25565, parsed.port());
    }

    @Test
    void rejectsForeignVersion() {
        assertNull(QuicAnnouncement.fromJson(announcement(QuicProtocol.VERSION + 1, 25565)));
        assertNull(QuicAnnouncement.fromJson(announcement(0, 25565)));
    }

    @Test
    void rejectsPortOutsideTheValidRange() {
        assertNull(QuicAnnouncement.fromJson(announcement(QuicProtocol.VERSION, 0)));
        assertNull(QuicAnnouncement.fromJson(announcement(QuicProtocol.VERSION, -1)));
        assertNull(QuicAnnouncement.fromJson(announcement(QuicProtocol.VERSION, 65536)));
        assertNull(QuicAnnouncement.fromJson(announcement(QuicProtocol.VERSION, Integer.MAX_VALUE)));
        assertNull(QuicAnnouncement.fromJson(announcement(QuicProtocol.VERSION, Integer.MIN_VALUE)));

        assertNotNull(QuicAnnouncement.fromJson(announcement(QuicProtocol.VERSION, 1)));
        assertNotNull(QuicAnnouncement.fromJson(announcement(QuicProtocol.VERSION, 65535)));
    }

    @Test
    void rejectsValuesThatAreNotWholeNumbers() {
        JsonObject fractionalVersion = new JsonObject();
        fractionalVersion.addProperty("v", 1.9);
        fractionalVersion.addProperty("port", 25565);

        JsonObject fractionalPort = new JsonObject();
        fractionalPort.addProperty("v", QuicProtocol.VERSION);
        fractionalPort.addProperty("port", 25565.5);

        assertNull(QuicAnnouncement.fromJson(fractionalVersion));
        assertNull(QuicAnnouncement.fromJson(fractionalPort));
    }

    @Test
    void rejectsValuesOfTheWrongType() {
        JsonObject stringPort = new JsonObject();
        stringPort.addProperty("v", QuicProtocol.VERSION);
        stringPort.addProperty("port", "25565");

        JsonObject objectPort = new JsonObject();
        objectPort.addProperty("v", QuicProtocol.VERSION);
        objectPort.add("port", new JsonObject());

        JsonObject arrayVersion = new JsonObject();
        arrayVersion.add("v", new JsonArray());
        arrayVersion.addProperty("port", 25565);

        JsonObject nullPort = new JsonObject();
        nullPort.addProperty("v", QuicProtocol.VERSION);
        nullPort.add("port", JsonNull.INSTANCE);

        assertNull(QuicAnnouncement.fromJson(stringPort));
        assertNull(QuicAnnouncement.fromJson(objectPort));
        assertNull(QuicAnnouncement.fromJson(arrayVersion));
        assertNull(QuicAnnouncement.fromJson(nullPort));
    }

    @Test
    void ignoresUnknownFields() {
        JsonObject withExtras = announcement(QuicProtocol.VERSION, 25565);
        withExtras.addProperty("future", "whatever");

        QuicAnnouncement parsed = QuicAnnouncement.fromJson(withExtras);

        assertNotNull(parsed);
        assertEquals(25565, parsed.port());
    }

    @Test
    void rejectsMalformedAnnouncement() {
        JsonObject missingPort = new JsonObject();
        missingPort.addProperty("v", QuicProtocol.VERSION);

        assertNull(QuicAnnouncement.fromJson(null));
        assertNull(QuicAnnouncement.fromJson(new JsonPrimitive("quic")));
        assertNull(QuicAnnouncement.fromJson(missingPort));
    }
}
