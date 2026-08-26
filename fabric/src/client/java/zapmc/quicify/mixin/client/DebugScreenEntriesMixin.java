package zapmc.quicify.mixin.client;

import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zapmc.quicify.Quicify;

import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(DebugScreenEntries.class)
public abstract class DebugScreenEntriesMixin {

    @Shadow
    @Mutable
    @Final
    public static Map<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> PROFILES;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void quicify$addToDefaultProfile(CallbackInfo ci) {
        Identifier quicEntry = Identifier.fromNamespaceAndPath(Quicify.MOD_ID, "quic");
        Map<Identifier, DebugScreenEntryStatus> defaultProfile = new LinkedHashMap<>(PROFILES.get(DebugScreenProfile.DEFAULT));
        defaultProfile.put(quicEntry, DebugScreenEntryStatus.IN_OVERLAY);
        Map<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> profiles = new LinkedHashMap<>(PROFILES);
        profiles.put(DebugScreenProfile.DEFAULT, Map.copyOf(defaultProfile));
        PROFILES = Map.copyOf(profiles);
    }
}
