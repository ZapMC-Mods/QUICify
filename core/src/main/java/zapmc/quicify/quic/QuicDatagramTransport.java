package zapmc.quicify.quic;

import io.netty.channel.IoHandlerFactory;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;

public enum QuicDatagramTransport {

    KQUEUE {
        @Override
        public IoHandlerFactory ioHandlerFactory() {
            return KQueueIoHandler.newFactory();
        }

        @Override
        public Class<? extends DatagramChannel> channelClass() {
            return KQueueDatagramChannel.class;
        }
    },
    EPOLL {
        @Override
        public IoHandlerFactory ioHandlerFactory() {
            return EpollIoHandler.newFactory();
        }

        @Override
        public Class<? extends DatagramChannel> channelClass() {
            return EpollDatagramChannel.class;
        }
    },
    NIO {
        @Override
        public IoHandlerFactory ioHandlerFactory() {
            return NioIoHandler.newFactory();
        }

        @Override
        public Class<? extends DatagramChannel> channelClass() {
            return NioDatagramChannel.class;
        }
    };

    public static QuicDatagramTransport select(boolean allowNative) {
        if (allowNative) {
            if (KQueue.isAvailable()) {
                return KQUEUE;
            }
            if (Epoll.isAvailable()) {
                return EPOLL;
            }
        }
        return NIO;
    }

    public abstract IoHandlerFactory ioHandlerFactory();

    public abstract Class<? extends DatagramChannel> channelClass();
}
