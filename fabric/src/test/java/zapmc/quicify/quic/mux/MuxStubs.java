package zapmc.quicify.quic.mux;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.quic.QuicChannel;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class MuxStubs {

    static QuicChannel quicChannel(EmbeddedChannel delegate) {
        return proxy(QuicChannel.class, delegate, (_, _) -> null);
    }

    static <T extends Channel> T proxy(Class<T> type, EmbeddedChannel delegate, Overrides overrides) {
        InvocationHandler handler = (instance, method, args) -> switch (method.getName()) {
            case "equals" -> instance == args[0];
            case "hashCode" -> System.identityHashCode(instance);
            case "toString" -> type.getSimpleName() + "-stub";
            default -> dispatch(delegate, overrides, method, args);
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object dispatch(EmbeddedChannel delegate, Overrides overrides, Method method, Object[] args) throws Exception {
        Object override = overrides.apply(method, args);
        if (override != null) {
            return override;
        }
        Method delegated;
        try {
            delegated = delegate.getClass().getMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException e) {
            return defaultValue(method.getReturnType());
        }
        Object result = delegated.invoke(delegate, args);
        Class<?> returnType = method.getReturnType();
        if (result == null || returnType.isPrimitive() || returnType.isInstance(result)) {
            return result;
        }
        return defaultValue(returnType);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == void.class) {
            return null;
        }
        if (returnType == boolean.class) {
            return Boolean.FALSE;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == double.class) {
            return 0.0d;
        }
        if (returnType == float.class) {
            return 0.0f;
        }
        return 0;
    }

    @FunctionalInterface
    interface Overrides {
        Object apply(Method method, Object[] args);
    }
}
