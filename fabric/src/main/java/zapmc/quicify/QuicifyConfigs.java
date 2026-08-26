package zapmc.quicify;

import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;

public final class QuicifyConfigs {

    public static final QuicifyConfig INSTANCE = load();

    private static QuicifyConfig load() {
        try {
            return ConfigApiJava.registerAndLoadConfig(QuicifyConfig::new, RegisterType.CLIENT);
        } catch (Throwable t) {
            return new QuicifyConfig();
        }
    }

    public static boolean enabled() {
        return INSTANCE.enabled.get();
    }

    public static int serverPort() {
        return INSTANCE.serverPort.get();
    }

    public static QuicifyConfig.ConnectMode connectMode() {
        return INSTANCE.connectMode.get();
    }

    public static boolean multiplexing() {
        return INSTANCE.multiplexing.get();
    }

    public static int compressionLevel() {
        return INSTANCE.compressionLevel.get();
    }

    public static int compressionWindowLog() {
        return INSTANCE.compressionWindowLog.get();
    }

    public static QuicifyConfig.CongestionControl congestionControl() {
        return INSTANCE.congestionControl.get();
    }

    public static int connectTimeoutMs() {
        return INSTANCE.connectTimeoutMs.get();
    }

    public static boolean verbose() {
        return INSTANCE.verbose.get();
    }
}
