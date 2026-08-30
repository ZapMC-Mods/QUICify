package zapmc.quicify.velocity;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import io.netty.handler.codec.quic.QuicCongestionControlAlgorithm;
import zapmc.quicify.Quicify;
import zapmc.quicify.QuicifySettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class QuicifyVelocityConfig implements QuicifySettings {

    private static final String FILE_NAME = "quicify.toml";

    private final boolean enabled;
    private final int serverPort;
    private final boolean multiplexing;
    private final boolean datagrams;
    private final int compressionLevel;
    private final int compressionWindowLog;
    private final QuicCongestionControlAlgorithm congestionControl;
    private final boolean verbose;

    private QuicifyVelocityConfig(CommentedFileConfig config) {
        enabled = config.getOrElse("enabled", true);
        serverPort = clamp(config.getIntOrElse("serverPort", 0), 0, 65535, 0);
        multiplexing = config.getOrElse("multiplexing", true);
        datagrams = config.getOrElse("datagrams", true);
        compressionLevel = clamp(config.getIntOrElse("compressionLevel", 3), 1, 22, 3);
        compressionWindowLog = clamp(config.getIntOrElse("compressionWindowLog", 18), 10, 27, 18);
        congestionControl = algorithm(config.getOrElse("congestionControl", "BBR"));
        verbose = config.getOrElse("verbose", false);
    }

    public static QuicifyVelocityConfig load(Path directory) {
        Path file = directory.resolve(FILE_NAME);
        try {
            Files.createDirectories(directory);
        } catch (Exception e) {
            Quicify.LOGGER.warn("QUICify could not create {} ({}), running on defaults", directory, e.toString());
            return defaults();
        }
        try (CommentedFileConfig config = CommentedFileConfig.builder(file).sync().build()) {
            config.load();
            fillDefaults(config);
            config.save();
            return new QuicifyVelocityConfig(config);
        } catch (Exception e) {
            Quicify.LOGGER.warn("QUICify could not read {} ({}), running on defaults", file, e.toString());
            return defaults();
        }
    }

    private static QuicifyVelocityConfig defaults() {
        try (CommentedFileConfig config = CommentedFileConfig.builder(Path.of(FILE_NAME)).build()) {
            return new QuicifyVelocityConfig(config);
        }
    }

    private static void fillDefaults(CommentedFileConfig config) {
        put(config, "enabled", true, "Enable or disable QUICify");
        put(config, "serverPort", 0, "UDP port of the QUIC listener, 0 reuses the proxy's TCP port (recommended)");
        put(config, "multiplexing", true, "Spread packets over several QUIC streams so chunk transfers stop delaying chat and movement");
        put(config, "datagrams", true, "Send sounds, particles and animations as unreliable QUIC datagrams so a lost one is never retransmitted or blocking");
        put(config, "compressionLevel", 3, "zstd compression level used when sending, higher trades CPU for a better ratio");
        put(config, "compressionWindowLog", 18, "Base 2 logarithm of the compression history kept per stream, higher trades memory for a better ratio");
        put(config, "congestionControl", "BBR", "Congestion control algorithm used, BBR or CUBIC");
        put(config, "verbose", false, "Extra logging about QUIC connections (for development purposes)");
    }

    private static void put(CommentedFileConfig config, String key, Object value, String comment) {
        config.setComment(key, " " + comment);
        if (!config.contains(key)) {
            config.set(key, value);
        }
    }

    private static int clamp(int value, int min, int max, int fallback) {
        return value < min || value > max ? fallback : value;
    }

    private static QuicCongestionControlAlgorithm algorithm(String name) {
        try {
            return QuicCongestionControlAlgorithm.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            Quicify.LOGGER.warn("Unknown congestion control algorithm {}, falling back to BBR", name);
            return QuicCongestionControlAlgorithm.BBR;
        }
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public int serverPort() {
        return serverPort;
    }

    @Override
    public boolean multiplexing() {
        return multiplexing;
    }

    @Override
    public boolean datagrams() {
        return datagrams;
    }

    @Override
    public int compressionLevel() {
        return compressionLevel;
    }

    @Override
    public int compressionWindowLog() {
        return compressionWindowLog;
    }

    @Override
    public QuicCongestionControlAlgorithm congestionControl() {
        return congestionControl;
    }

    @Override
    public boolean verbose() {
        return verbose;
    }
}
