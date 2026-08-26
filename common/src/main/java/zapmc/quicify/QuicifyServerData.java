package zapmc.quicify;

import org.jspecify.annotations.Nullable;

public interface QuicifyServerData {

    void quicify$setPingResult(@Nullable QuicAnnouncement announcement);

    boolean quicify$hasPingResult();

    @Nullable
    QuicAnnouncement quicify$announcement();
}
