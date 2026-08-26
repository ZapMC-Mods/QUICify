package zapmc.quicify.mixin.client;

import me.fzzyhmstrs.fzzy_config.screen.internal.ConfigScreen;
import me.fzzyhmstrs.fzzy_config.screen.widget.custom.CustomButtonWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zapmc.quicify.Quicify;

@Mixin(ConfigScreen.class)
public abstract class ConfigScreenMixin {

    @Shadow
    public HeaderAndFooterLayout layout;

    @Shadow
    @Final
    private String scope;

    @Shadow
    private CustomButtonWidget doneButton;

    @Inject(method = "initFooter", at = @At("RETURN"))
    private void quicify$simplifyFooter(CallbackInfo ci) {
        if (!this.scope.equals(Quicify.MOD_ID) && !this.scope.startsWith(Quicify.MOD_ID + ".")) {
            return;
        }
        ((HeaderAndFooterLayoutAccessor) this.layout).quicify$getFooterFrame().removeChildren();
        this.doneButton.setWidth(200);
        this.layout.addToFooter(this.doneButton);
    }
}
