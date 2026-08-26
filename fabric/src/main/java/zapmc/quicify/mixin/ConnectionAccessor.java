package zapmc.quicify.mixin;

import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Connection.class)
public interface ConnectionAccessor {

    @Accessor("bandwidthDebugMonitor")
    @Nullable
    BandwidthDebugMonitor quicify$bandwidthDebugMonitor();
}
