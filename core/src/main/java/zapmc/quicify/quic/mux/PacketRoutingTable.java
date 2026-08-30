package zapmc.quicify.quic.mux;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;
import zapmc.quicify.Quicify;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.Map;

public final class PacketRoutingTable {

    private static final String RESOURCE = "/quicify/packet-routing.json";

    private static volatile @Nullable PacketRoutingTable loaded;

    private static volatile boolean loadFailed;
    private final int protocolVersion;
    private final Map<Phase, Direction> clientbound = new EnumMap<>(Phase.class);
    private final Map<Phase, Direction> serverbound = new EnumMap<>(Phase.class);

    private PacketRoutingTable(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public static @Nullable PacketRoutingTable forProtocol(int protocolVersion) {
        PacketRoutingTable table = load();
        return table != null && table.protocolVersion == protocolVersion ? table : null;
    }

    private static @Nullable PacketRoutingTable load() {
        PacketRoutingTable cached = loaded;
        if (cached != null || loadFailed) {
            return cached;
        }
        synchronized (PacketRoutingTable.class) {
            if (loaded == null && !loadFailed) {
                try (InputStream stream = PacketRoutingTable.class.getResourceAsStream(RESOURCE)) {
                    if (stream == null) {
                        throw new IllegalStateException(RESOURCE + " is missing from the classpath");
                    }
                    loaded = parse(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
                } catch (Exception e) {
                    loadFailed = true;
                    Quicify.LOGGER.warn("QUIC packet routing table could not be read ({}), multiplexing stays off", e.toString());
                }
            }
            return loaded;
        }
    }

    private static PacketRoutingTable parse(JsonObject root) {
        PacketRoutingTable table = new PacketRoutingTable(root.get("protocol").getAsInt());
        JsonObject states = root.getAsJsonObject("states");
        for (Phase phase : Phase.values()) {
            table.clientbound.put(phase, direction(states, phase + ":CLIENTBOUND"));
            table.serverbound.put(phase, direction(states, phase + ":SERVERBOUND"));
        }
        return table;
    }

    private static Direction direction(JsonObject states, String key) {
        JsonObject json = states.getAsJsonObject(key);
        if (json == null) {
            throw new IllegalStateException("missing " + key + " in the routing table");
        }
        JsonObject categories = json.getAsJsonObject("categories");
        int highest = -1;
        for (String id : categories.keySet()) {
            highest = Math.max(highest, Integer.parseInt(id));
        }
        PacketCategory[] byId = new PacketCategory[highest + 1];
        for (Map.Entry<String, JsonElement> entry : categories.entrySet()) {
            byId[Integer.parseInt(entry.getKey())] = PacketCategory.valueOf(entry.getValue().getAsString());
        }
        return new Direction(byId, bits(json, "barriers"), bits(json, "playEntries"), bits(json, "terminals"));
    }

    private static BitSet bits(JsonObject json, String key) {
        BitSet set = new BitSet();
        json.getAsJsonArray(key).forEach(element -> set.set(element.getAsInt()));
        return set;
    }

    private Direction direction(Phase phase, boolean clientbound) {
        return (clientbound ? this.clientbound : this.serverbound).get(phase);
    }

    public PacketCategory category(Phase phase, boolean clientbound, int packetId) {
        return direction(phase, clientbound).category(packetId);
    }

    public boolean isBarrier(Phase phase, boolean clientbound, int packetId) {
        return packetId >= 0 && direction(phase, clientbound).barriers.get(packetId);
    }

    public boolean isPlayEntry(Phase phase, boolean clientbound, int packetId) {
        return packetId >= 0 && direction(phase, clientbound).playEntries.get(packetId);
    }

    public boolean isTerminal(Phase phase, boolean clientbound, int packetId) {
        return packetId >= 0 && direction(phase, clientbound).terminals.get(packetId);
    }

    public enum Phase {
        PLAY,
        CONFIGURATION
    }

    private record Direction(PacketCategory[] byId, BitSet barriers, BitSet playEntries, BitSet terminals) {
        PacketCategory category(int packetId) {
            if (packetId < 0 || packetId >= byId.length || byId[packetId] == null) {
                return PacketCategory.CONTROL;
            }
            return byId[packetId];
        }
    }
}
