package zapmc.quicify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class Quicify {

    public static final String MOD_ID = "quicify";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static volatile Path configDir = Path.of("config");

    public static void init(Path loaderConfigDir) {
        configDir = loaderConfigDir;
        LOGGER.info("Initializing QUICify \uD83D\uDD0C");
    }

    public static Path configDir() {
        return configDir;
    }
}
