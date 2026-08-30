package zapmc.quicify.velocity;

import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.network.ConnectionManager;
import com.velocitypowered.proxy.network.ServerChannelInitializer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import zapmc.quicify.Quicify;

public final class QuicifyServerChannelInitializer extends ServerChannelInitializer {

    private QuicifyServerChannelInitializer(VelocityServer server) {
        super(server);
    }

    @SuppressWarnings("deprecation")
    public static void install(VelocityServer server, ConnectionManager connectionManager) {
        ChannelInitializer<Channel> current = connectionManager.serverChannelInitializer.get();
        if (current instanceof ServerChannelInitializer) {
            connectionManager.serverChannelInitializer.set(new QuicifyServerChannelInitializer(server));
            return;
        }
        Quicify.LOGGER.info("Another plugin already replaced the server channel initializer, wrapping {}", current.getClass().getName());
        connectionManager.serverChannelInitializer.set(new Wrapping(current));
    }

    @Override
    protected void initChannel(Channel ch) {
        super.initChannel(ch);
        StatusAnnouncer.install(ch);
    }

    private static final class Wrapping extends ChannelInitializer<Channel> {
        private final ChannelInitializer<Channel> delegate;

        private Wrapping(ChannelInitializer<Channel> delegate) {
            this.delegate = delegate;
        }

        @Override
        protected void initChannel(Channel ch) throws Exception {
            VelocityInternals.initChannel(delegate, ch);
            StatusAnnouncer.install(ch);
        }
    }
}
