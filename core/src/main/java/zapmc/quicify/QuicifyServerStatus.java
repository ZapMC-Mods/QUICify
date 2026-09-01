package zapmc.quicify;

import org.jspecify.annotations.Nullable;

public interface QuicifyServerStatus {

    void quicify$setAnnouncement(@Nullable QuicAnnouncement announcement);

    @Nullable
    QuicAnnouncement quicify$announcement();
}
