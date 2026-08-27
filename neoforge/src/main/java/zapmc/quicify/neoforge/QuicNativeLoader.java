package zapmc.quicify.neoforge;

import io.netty.util.internal.PlatformDependent;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class QuicNativeLoader {

    private static final String LIBRARY = "netty_quiche42";

    private QuicNativeLoader() {
    }

    public static void load() {
        String name = LIBRARY + '_' + PlatformDependent.normalizedOs() + '_' + PlatformDependent.normalizedArch();
        String fileName = System.mapLibraryName(name);
        InputStream stream = open(fileName);
        if (stream == null && fileName.endsWith(".dylib")) {
            fileName = fileName.substring(0, fileName.length() - "dylib".length()) + "jnilib";
            stream = open(fileName);
        }
        if (stream == null) {
            throw new UnsatisfiedLinkError("No bundled quiche native for " + name);
        }
        try (InputStream in = stream) {
            Path extracted = Files.createTempFile(LIBRARY, fileName.substring(fileName.lastIndexOf('.')));
            Files.copy(in, extracted, StandardCopyOption.REPLACE_EXISTING);
            extracted.toFile().deleteOnExit();
            System.load(extracted.toAbsolutePath().toString());
        } catch (Exception e) {
            UnsatisfiedLinkError error = new UnsatisfiedLinkError("Failed to load the bundled quiche native " + name);
            error.initCause(e);
            throw error;
        }
    }

    private static InputStream open(String fileName) {
        return QuicNativeLoader.class.getClassLoader().getResourceAsStream("META-INF/native/" + fileName);
    }
}
