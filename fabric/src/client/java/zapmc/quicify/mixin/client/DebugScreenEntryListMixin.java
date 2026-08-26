package zapmc.quicify.mixin.client;

import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zapmc.quicify.Quicify;

import java.util.List;

@Mixin(DebugScreenEntryList.class)
public abstract class DebugScreenEntryListMixin {

    @Shadow
    @Final
    private List<Identifier> currentlyEnabled;

    @Inject(method = "rebuildCurrentList", at = @At("RETURN"))
    private void quicify$moveQuicEntryLast(CallbackInfo ci) {
        Identifier quicEntry = Identifier.fromNamespaceAndPath(Quicify.MOD_ID, "quic");
        if (this.currentlyEnabled.remove(quicEntry)) {
            this.currentlyEnabled.add(quicEntry);
        }
    }
}
