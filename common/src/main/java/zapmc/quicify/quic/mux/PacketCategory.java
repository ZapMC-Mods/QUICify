package zapmc.quicify.quic.mux;

public enum PacketCategory {

    CONTROL(0, false),
    REALTIME(1, false),
    UI(2, false),
    AMBIENT(4, true),
    WORLD(6, true);

    private static final PacketCategory[] VALUES = values();

    public static final int SECONDARY_COUNT = VALUES.length - 1;

    private final int urgency;

    private final boolean incremental;

    PacketCategory(int urgency, boolean incremental) {
        this.urgency = urgency;
        this.incremental = incremental;
    }

    public static PacketCategory bySecondaryIndex(int index) {
        return VALUES[index + 1];
    }

    public static PacketCategory byWireId(byte id) {
        return id > 0 && id < VALUES.length ? VALUES[id] : null;
    }

    public int urgency() {
        return urgency;
    }

    public boolean incremental() {
        return incremental;
    }

    public boolean secondary() {
        return this != CONTROL;
    }

    public int secondaryIndex() {
        return ordinal() - 1;
    }

    public byte wireId() {
        return (byte) ordinal();
    }
}
