package zapmc.quicify.mixin.client;

import net.minecraft.client.multiplayer.ServerData;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import zapmc.quicify.QuicAnnouncement;
import zapmc.quicify.QuicifyServerData;

import java.util.Objects;

@Mixin(ServerData.class)
public abstract class ServerDataMixin implements QuicifyServerData {

    @Shadow
    public String ip;

    @Unique
    private volatile boolean quicify$hasPingResult;

    @Unique
    @Nullable
    private volatile QuicAnnouncement quicify$announcement;

    @Unique
    @Nullable
    private volatile String quicify$pingedIp;

    @Override
    public void quicify$setPingResult(@Nullable QuicAnnouncement announcement) {
        this.quicify$announcement = announcement;
        this.quicify$pingedIp = this.ip;
        this.quicify$hasPingResult = true;
    }

    @Override
    public boolean quicify$hasPingResult() {
        return quicify$hasPingResult && quicify$pingIsStillCurrent();
    }

    @Override
    public @Nullable QuicAnnouncement quicify$announcement() {
        return quicify$pingIsStillCurrent() ? quicify$announcement : null;
    }

    @Unique
    private boolean quicify$pingIsStillCurrent() {
        return Objects.equals(quicify$pingedIp, ip);
    }
}
