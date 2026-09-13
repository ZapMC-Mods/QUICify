package zapmc.quicify.quic.mux;

@FunctionalInterface
public interface RxMeter {

    void onReceive(int bytes);
}
