package zapmc.quicify.quic.mux;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.Bootstrap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public final class PacketRoutingExporter {

    public static final String RESOURCE = "quicify/packet-routing.json";

    private PacketRoutingExporter() {
    }

    static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("usage: PacketRoutingExporter <output file>");
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        Path target = Path.of(args[0]);
        Files.createDirectories(target.getParent());
        Files.writeString(target, export(), StandardCharsets.UTF_8);
        System.out.println("wrote " + target.toAbsolutePath());
    }

    public static String export() {
        JsonObject root = new JsonObject();
        root.addProperty("protocol", SharedConstants.getProtocolVersion());
        root.addProperty("minecraftVersion", SharedConstants.getCurrentVersion().name());

        JsonObject states = new JsonObject();
        states.add("PLAY:CLIENTBOUND", phase(GameProtocols.CLIENTBOUND_TEMPLATE.details(), true));
        states.add("PLAY:SERVERBOUND", phase(GameProtocols.SERVERBOUND_TEMPLATE.details(), true));
        states.add("CONFIGURATION:CLIENTBOUND", phase(ConfigurationProtocols.CLIENTBOUND_TEMPLATE.details(), false));
        states.add("CONFIGURATION:SERVERBOUND", phase(ConfigurationProtocols.SERVERBOUND_TEMPLATE.details(), false));
        root.add("states", states);

        return new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
    }

    private static JsonObject phase(ProtocolInfo.Details details, boolean exhaustive) {
        TreeMap<Integer, PacketType<?>> byId = new TreeMap<>();
        details.listPackets((type, id) -> {
            if (byId.put(id, type) != null) {
                throw new IllegalStateException("duplicate packet id " + id + " in " + details.id() + "/" + details.flow());
            }
        });

        JsonObject categories = new JsonObject();
        List<Integer> barriers = new ArrayList<>();
        List<Integer> playEntries = new ArrayList<>();
        List<Integer> terminals = new ArrayList<>();
        List<Integer> datagrams = new ArrayList<>();
        List<String> unclassified = new ArrayList<>();

        byId.forEach((id, type) -> {
            if (!PacketRouting.isClassified(type)) {
                unclassified.add(type.toString());
                return;
            }
            categories.addProperty(Integer.toString(id), PacketRouting.categoryOf(type).name());
            if (PacketRouting.isBarrier(type)) {
                barriers.add(id);
            }
            if (PacketRouting.isPlayEntry(type)) {
                playEntries.add(id);
            }
            if (PacketRouting.isTerminal(type)) {
                terminals.add(id);
            }
            if (PacketRouting.isDatagram(type)) {
                datagrams.add(id);
            }
        });

        if (exhaustive && !unclassified.isEmpty()) {
            throw new IllegalStateException("unclassified packet types (add them to PacketRouting): " + unclassified);
        }

        JsonObject phase = new JsonObject();
        phase.add("categories", categories);
        phase.add("barriers", array(barriers));
        phase.add("playEntries", array(playEntries));
        phase.add("terminals", array(terminals));
        phase.add("datagrams", array(datagrams));
        return phase;
    }

    private static JsonArray array(List<Integer> ids) {
        JsonArray array = new JsonArray();
        ids.forEach(array::add);
        return array;
    }
}
