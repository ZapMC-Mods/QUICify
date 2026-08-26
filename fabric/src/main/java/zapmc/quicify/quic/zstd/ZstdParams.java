package zapmc.quicify.quic.zstd;

public record ZstdParams(int threshold, boolean validate, int level, int windowLog) {
}
