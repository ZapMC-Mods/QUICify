package zapmc.quicify.velocity;

import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.client.LoginInboundConnection;
import com.velocitypowered.proxy.network.ConnectionManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class VelocityInternals {

    private VelocityInternals() {
    }

    public static ConnectionManager connectionManager(VelocityServer server) throws ReflectiveOperationException {
        Field field = VelocityServer.class.getDeclaredField("cm");
        field.setAccessible(true);
        return (ConnectionManager) field.get(server);
    }

    public static LoginInboundConnection loginInboundConnection(MinecraftSessionHandler handler) throws ReflectiveOperationException {
        Field field = handler.getClass().getDeclaredField("inbound");
        field.setAccessible(true);
        return (LoginInboundConnection) field.get(handler);
    }

    public static MinecraftSessionHandler authSessionHandler(VelocityServer server, LoginInboundConnection inbound,
                                                             GameProfile profile, boolean onlineMode, String serverId)
            throws ReflectiveOperationException {
        Class<?> type = Class.forName("com.velocitypowered.proxy.connection.client.AuthSessionHandler",
                true, VelocityServer.class.getClassLoader());
        Constructor<?> constructor = type.getDeclaredConstructor(VelocityServer.class, LoginInboundConnection.class,
                GameProfile.class, boolean.class, String.class);
        constructor.setAccessible(true);
        return (MinecraftSessionHandler) constructor.newInstance(server, inbound, profile, onlineMode, serverId);
    }

    public static void initChannel(ChannelInitializer<Channel> initializer, Channel channel) throws ReflectiveOperationException {
        Method method = ChannelInitializer.class.getDeclaredMethod("initChannel", Channel.class);
        Method actual = initializer.getClass().getDeclaredMethod(method.getName(), Channel.class);
        actual.setAccessible(true);
        actual.invoke(initializer, channel);
    }
}
