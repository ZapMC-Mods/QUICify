package zapmc.quicify;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

public record QuicAnnouncement(int version, int port) {

    public static @Nullable QuicAnnouncement fromJson(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject json = element.getAsJsonObject();
        if (!json.has("v") || !json.has("port")) {
            return null;
        }
        try {
            Integer version = wholeNumber(json.get("v"));
            if (version == null || version != QuicProtocol.VERSION) {
                return null;
            }
            Integer port = wholeNumber(json.get("port"));
            if (port == null || port < 1 || port > 65535) {
                return null;
            }
            return new QuicAnnouncement(version, port);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @Nullable Integer wholeNumber(JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double value = element.getAsDouble();
        int truncated = (int) value;
        return truncated == value ? truncated : null;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("v", version);
        json.addProperty("port", port);
        return json;
    }
}
