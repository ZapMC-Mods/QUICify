package zapmc.quicify.velocity;

import zapmc.quicify.Quicify;

import java.lang.invoke.MethodHandles;

public final class QuicNativeIsolation {

    private static final String HELPER = "io/netty/util/internal/NativeLibraryUtil";

    private QuicNativeIsolation() {
    }

    public static void prepare() {
        try {
            ClassLoader loader = QuicNativeIsolation.class.getClassLoader();
            byte[] helper;
            try (var stream = loader.getResourceAsStream(HELPER + ".class")) {
                if (stream == null) {
                    Quicify.LOGGER.warn("Netty's native library helper could not be read, QUIC may not load on this proxy");
                    return;
                }
                helper = stream.readAllBytes();
            }
            MethodHandles.Lookup lookup = io.netty.util.internal.QuicifyNativeLibraryAnchor.LOOKUP;
            lookup.defineClass(helper);
        } catch (Throwable t) {
            Quicify.LOGGER.warn("Could not move the QUIC native load into this plugin's class loader ({}), QUIC may not load on this proxy", t.toString());
        }
    }
}
