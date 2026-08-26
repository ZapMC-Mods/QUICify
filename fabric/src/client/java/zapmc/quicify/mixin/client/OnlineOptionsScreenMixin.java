package zapmc.quicify.mixin.client;

import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OnlineOptionsScreen.class)
public abstract class OnlineOptionsScreenMixin extends OptionsSubScreen {

    private OnlineOptionsScreenMixin(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void quicify$addConfigSection(CallbackInfo ci) {
        if (this.list == null) {
            return;
        }
        this.list.addHeader(Component.translatable("quicify.options.header"));
        this.list.addBig(Button.builder(Component.translatable("quicify.options.settings"),
                        _ -> ConfigApiJava.INSTANCE.openScreen("quicify"))
                .tooltip(Tooltip.create(Component.translatable("quicify.options.settings.tooltip")))
                .build());
    }
}
