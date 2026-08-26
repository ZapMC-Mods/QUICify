package zapmc.quicify;

import io.netty.handler.codec.quic.QuicCongestionControlAlgorithm;
import kotlin.ranges.IntRange;
import me.fzzyhmstrs.fzzy_config.annotations.Action;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.annotations.RequiresAction;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.util.EnumTranslatable;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedNumber;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public class QuicifyConfig extends Config {

    @Comment("Enable or disable QUICify")
    @RequiresAction(action = Action.RELOG)
    public ValidatedBoolean enabled = new ValidatedBoolean(true);

    @Comment("UDP port of the server-side QUIC listener, 0 reuses the Minecraft TCP port (recommended)")
    @RequiresAction(action = Action.RESTART)
    public ValidatedCondition<Integer> serverPort = new ValidatedInt(0, new IntRange(0, 65535), ValidatedNumber.WidgetType.TEXTBOX)
            .toCondition(enabled, () -> 0);

    @Comment("AUTO uses QUIC when the server advertises it (TCP fallback otherwise), FORCE_TCP never attempts QUIC, FORCE_QUIC refuses to connect when QUIC fails")
    public ValidatedCondition<ConnectMode> connectMode = new ValidatedEnum<>(ConnectMode.AUTO, ValidatedEnum.WidgetType.CYCLING)
            .toCondition(enabled, () -> ConnectMode.FORCE_TCP);

    @Comment("Spread packets over several QUIC streams so chunk transfers stop delaying chat and movement")
    @RequiresAction(action = Action.RELOG)
    public ValidatedCondition<Boolean> multiplexing = new ValidatedBoolean(true)
            .toCondition(enabled, () -> false);

    @Comment("zstd compression level used when sending, higher trades CPU for a better ratio")
    @RequiresAction(action = Action.RELOG)
    public ValidatedCondition<Integer> compressionLevel = new ValidatedInt(3, new IntRange(1, 22), ValidatedNumber.WidgetType.TEXTBOX)
            .toCondition(enabled, () -> 3);

    @Comment("Base 2 logarithm of the compression history kept per stream, higher trades memory for a better ratio")
    @RequiresAction(action = Action.RELOG)
    public ValidatedCondition<Integer> compressionWindowLog = new ValidatedInt(18, new IntRange(10, 27), ValidatedNumber.WidgetType.TEXTBOX)
            .toCondition(enabled, () -> 18);

    @Comment("Congestion control algorithm used")
    @RequiresAction(action = Action.RELOG)
    public ValidatedCondition<CongestionControl> congestionControl = new ValidatedEnum<>(CongestionControl.BBR, ValidatedEnum.WidgetType.CYCLING)
            .toCondition(enabled, () -> CongestionControl.BBR);

    @Comment("How long the client waits for a QUIC handshake before falling back to TCP")
    public ValidatedCondition<Integer> connectTimeoutMs = new ValidatedInt(3000, new IntRange(250, 30000), ValidatedNumber.WidgetType.TEXTBOX)
            .toCondition(enabled, () -> 3000);

    @Comment("Extra logging about QUIC connection attempts and fallbacks (for development purposes)")
    public ValidatedCondition<Boolean> verbose = new ValidatedBoolean(false)
            .toCondition(enabled, () -> false);

    public QuicifyConfig() {
        super(Identifier.fromNamespaceAndPath("quicify", "quicify"));
    }

    public enum ConnectMode implements EnumTranslatable {

        AUTO,
        FORCE_TCP,
        FORCE_QUIC;

        @Override
        public @NonNull String prefix() {
            return "quicify.quicify.connectMode";
        }
    }

    public enum CongestionControl implements EnumTranslatable {

        BBR,
        CUBIC;

        public QuicCongestionControlAlgorithm algorithm() {
            return QuicCongestionControlAlgorithm.valueOf(name());
        }

        @Override
        public @NonNull String prefix() {
            return "quicify.quicify.congestionControl";
        }
    }
}
