package zapmc.quicify;

import io.netty.handler.codec.quic.QuicCongestionControlAlgorithm;
import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;

public final class QuicifyFzzyConfigs implements QuicifySettings {

    public static final QuicifyConfig INSTANCE = load();

    static {
        QuicifyConfigs.install(new QuicifyFzzyConfigs());
    }

    private static QuicifyConfig load() {
        try {
            return ConfigApiJava.registerAndLoadConfig(QuicifyConfig::new, RegisterType.CLIENT);
        } catch (Throwable t) {
            return new QuicifyConfig();
        }
    }

    public static void install() {
    }

    public static QuicifyConfig.ConnectMode connectMode() {
        return INSTANCE.connectMode.get();
    }

    @Override
    public boolean enabled() {
        return INSTANCE.enabled.get();
    }

    @Override
    public int serverPort() {
        return INSTANCE.serverPort.get();
    }

    @Override
    public boolean multiplexing() {
        return INSTANCE.multiplexing.get();
    }

    @Override
    public int compressionLevel() {
        return INSTANCE.compressionLevel.get();
    }

    @Override
    public int compressionWindowLog() {
        return INSTANCE.compressionWindowLog.get();
    }

    @Override
    public QuicCongestionControlAlgorithm congestionControl() {
        return INSTANCE.congestionControl.get().algorithm();
    }

    @Override
    public int connectTimeoutMs() {
        return INSTANCE.connectTimeoutMs.get();
    }

    @Override
    public boolean verbose() {
        return INSTANCE.verbose.get();
    }
}
