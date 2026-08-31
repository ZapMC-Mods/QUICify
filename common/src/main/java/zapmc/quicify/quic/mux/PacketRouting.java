package zapmc.quicify.quic.mux;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.common.CommonPacketTypes;
import net.minecraft.network.protocol.cookie.CookiePacketTypes;
import net.minecraft.network.protocol.game.GamePacketTypes;
import net.minecraft.network.protocol.ping.PingPacketTypes;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public final class PacketRouting {

    private static final Map<PacketType<?>, PacketCategory> CATEGORIES = new Reference2ObjectOpenHashMap<>(256);

    private static final Set<PacketType<?>> BARRIERS = new ReferenceOpenHashSet<>();

    private static final Set<PacketType<?>> DATAGRAMS = new ReferenceOpenHashSet<>();

    private static final Set<PacketType<?>> PLAY_ENTRIES = Set.of(
            GamePacketTypes.CLIENTBOUND_LOGIN,
            GamePacketTypes.CLIENTBOUND_RESPAWN
    );

    private static final Set<PacketType<?>> TERMINALS = Set.of(
            CommonPacketTypes.CLIENTBOUND_DISCONNECT,
            CommonPacketTypes.CLIENTBOUND_TRANSFER
    );

    static {
        control(
                CommonPacketTypes.SERVERBOUND_CLIENT_INFORMATION,
                CommonPacketTypes.SERVERBOUND_KEEP_ALIVE,
                CommonPacketTypes.SERVERBOUND_PONG,
                CommonPacketTypes.SERVERBOUND_RESOURCE_PACK,
                CommonPacketTypes.CLIENTBOUND_KEEP_ALIVE,
                CommonPacketTypes.CLIENTBOUND_PING,
                CommonPacketTypes.CLIENTBOUND_RESOURCE_PACK_POP,
                CommonPacketTypes.CLIENTBOUND_RESOURCE_PACK_PUSH,
                CommonPacketTypes.CLIENTBOUND_STORE_COOKIE,
                CommonPacketTypes.CLIENTBOUND_CUSTOM_REPORT_DETAILS,
                CommonPacketTypes.CLIENTBOUND_SERVER_LINKS,
                CookiePacketTypes.SERVERBOUND_COOKIE_RESPONSE,
                CookiePacketTypes.CLIENTBOUND_COOKIE_REQUEST,
                PingPacketTypes.SERVERBOUND_PING_REQUEST,
                PingPacketTypes.CLIENTBOUND_PONG_RESPONSE,
                GamePacketTypes.SERVERBOUND_PLAYER_LOADED
        );

        barrier(
                CommonPacketTypes.CLIENTBOUND_DISCONNECT,
                CommonPacketTypes.CLIENTBOUND_TRANSFER,
                GamePacketTypes.CLIENTBOUND_LOGIN,
                GamePacketTypes.CLIENTBOUND_RESPAWN,
                GamePacketTypes.CLIENTBOUND_START_CONFIGURATION,
                GamePacketTypes.SERVERBOUND_CONFIGURATION_ACKNOWLEDGED
        );

        realtime(
                GamePacketTypes.SERVERBOUND_ACCEPT_TELEPORTATION,
                GamePacketTypes.SERVERBOUND_ATTACK,
                GamePacketTypes.SERVERBOUND_BUNDLE_ITEM_SELECTED,
                GamePacketTypes.SERVERBOUND_CLIENT_TICK_END,
                GamePacketTypes.SERVERBOUND_CONTAINER_BUTTON_CLICK,
                GamePacketTypes.SERVERBOUND_CONTAINER_CLICK,
                GamePacketTypes.SERVERBOUND_CONTAINER_CLOSE,
                GamePacketTypes.SERVERBOUND_CONTAINER_SLOT_STATE_CHANGED,
                GamePacketTypes.SERVERBOUND_INTERACT,
                GamePacketTypes.SERVERBOUND_MOVE_PLAYER_POS,
                GamePacketTypes.SERVERBOUND_MOVE_PLAYER_POS_ROT,
                GamePacketTypes.SERVERBOUND_MOVE_PLAYER_ROT,
                GamePacketTypes.SERVERBOUND_MOVE_PLAYER_STATUS_ONLY,
                GamePacketTypes.SERVERBOUND_MOVE_VEHICLE,
                GamePacketTypes.SERVERBOUND_PADDLE_BOAT,
                GamePacketTypes.SERVERBOUND_PICK_ITEM_FROM_BLOCK,
                GamePacketTypes.SERVERBOUND_PICK_ITEM_FROM_ENTITY,
                GamePacketTypes.SERVERBOUND_PLACE_RECIPE,
                GamePacketTypes.SERVERBOUND_PLAYER_ABILITIES,
                GamePacketTypes.SERVERBOUND_PLAYER_ACTION,
                GamePacketTypes.SERVERBOUND_PLAYER_COMMAND,
                GamePacketTypes.SERVERBOUND_PLAYER_INPUT,
                GamePacketTypes.SERVERBOUND_RENAME_ITEM,
                GamePacketTypes.SERVERBOUND_SELECT_TRADE,
                GamePacketTypes.SERVERBOUND_SET_BEACON,
                GamePacketTypes.SERVERBOUND_SET_CARRIED_ITEM,
                GamePacketTypes.SERVERBOUND_SET_CREATIVE_MODE_SLOT,
                GamePacketTypes.SERVERBOUND_SPECTATOR_ACTION,
                GamePacketTypes.SERVERBOUND_SWING,
                GamePacketTypes.SERVERBOUND_TELEPORT_TO_ENTITY,
                GamePacketTypes.SERVERBOUND_USE_ITEM,
                GamePacketTypes.SERVERBOUND_USE_ITEM_ON,
                GamePacketTypes.CLIENTBOUND_BUNDLE,
                GamePacketTypes.CLIENTBOUND_BUNDLE_DELIMITER,
                GamePacketTypes.CLIENTBOUND_ADD_ENTITY,
                GamePacketTypes.CLIENTBOUND_ANIMATE,
                GamePacketTypes.CLIENTBOUND_DAMAGE_EVENT,
                GamePacketTypes.CLIENTBOUND_DEBUG_ENTITY_VALUE,
                GamePacketTypes.CLIENTBOUND_ENTITY_EVENT,
                GamePacketTypes.CLIENTBOUND_ENTITY_POSITION_SYNC,
                GamePacketTypes.CLIENTBOUND_EXPLODE,
                GamePacketTypes.CLIENTBOUND_GAME_EVENT,
                GamePacketTypes.CLIENTBOUND_HURT_ANIMATION,
                GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS,
                GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS_ROT,
                GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_ROT,
                GamePacketTypes.CLIENTBOUND_MOVE_MINECART_ALONG_TRACK,
                GamePacketTypes.CLIENTBOUND_MOVE_VEHICLE,
                GamePacketTypes.CLIENTBOUND_PLAYER_ABILITIES,
                GamePacketTypes.CLIENTBOUND_PLAYER_COMBAT_END,
                GamePacketTypes.CLIENTBOUND_PLAYER_COMBAT_ENTER,
                GamePacketTypes.CLIENTBOUND_PLAYER_COMBAT_KILL,
                GamePacketTypes.CLIENTBOUND_PLAYER_INFO_UPDATE,
                GamePacketTypes.CLIENTBOUND_PLAYER_LOOK_AT,
                GamePacketTypes.CLIENTBOUND_PLAYER_POSITION,
                GamePacketTypes.CLIENTBOUND_PLAYER_ROTATION,
                GamePacketTypes.CLIENTBOUND_PROJECTILE_POWER,
                GamePacketTypes.CLIENTBOUND_REMOVE_ENTITIES,
                GamePacketTypes.CLIENTBOUND_REMOVE_MOB_EFFECT,
                GamePacketTypes.CLIENTBOUND_ROTATE_HEAD,
                GamePacketTypes.CLIENTBOUND_SET_CAMERA,
                GamePacketTypes.CLIENTBOUND_SET_ENTITY_DATA,
                GamePacketTypes.CLIENTBOUND_SET_ENTITY_LINK,
                GamePacketTypes.CLIENTBOUND_SET_ENTITY_MOTION,
                GamePacketTypes.CLIENTBOUND_SET_EQUIPMENT,
                GamePacketTypes.CLIENTBOUND_SET_HEALTH,
                GamePacketTypes.CLIENTBOUND_SET_PASSENGERS,
                GamePacketTypes.CLIENTBOUND_TAKE_ITEM_ENTITY,
                GamePacketTypes.CLIENTBOUND_TELEPORT_ENTITY,
                GamePacketTypes.CLIENTBOUND_TICKING_STATE,
                GamePacketTypes.CLIENTBOUND_TICKING_STEP,
                GamePacketTypes.CLIENTBOUND_UPDATE_ATTRIBUTES,
                GamePacketTypes.CLIENTBOUND_UPDATE_MOB_EFFECT
        );

        ui(
                CommonPacketTypes.SERVERBOUND_CUSTOM_PAYLOAD,
                CommonPacketTypes.SERVERBOUND_CUSTOM_CLICK_ACTION,
                CommonPacketTypes.CLIENTBOUND_CUSTOM_PAYLOAD,
                CommonPacketTypes.CLIENTBOUND_CLEAR_DIALOG,
                CommonPacketTypes.CLIENTBOUND_SHOW_DIALOG,
                CommonPacketTypes.CLIENTBOUND_UPDATE_TAGS,
                GamePacketTypes.SERVERBOUND_BLOCK_ENTITY_TAG_QUERY,
                GamePacketTypes.SERVERBOUND_CHANGE_DIFFICULTY,
                GamePacketTypes.SERVERBOUND_CHANGE_GAME_MODE,
                GamePacketTypes.SERVERBOUND_CHAT,
                GamePacketTypes.SERVERBOUND_CHAT_ACK,
                GamePacketTypes.SERVERBOUND_CHAT_COMMAND,
                GamePacketTypes.SERVERBOUND_CHAT_COMMAND_SIGNED,
                GamePacketTypes.SERVERBOUND_CHAT_SESSION_UPDATE,
                GamePacketTypes.SERVERBOUND_CLIENT_COMMAND,
                GamePacketTypes.SERVERBOUND_COMMAND_SUGGESTION,
                GamePacketTypes.SERVERBOUND_DEBUG_SUBSCRIPTION_REQUEST,
                GamePacketTypes.SERVERBOUND_EDIT_BOOK,
                GamePacketTypes.SERVERBOUND_ENTITY_TAG_QUERY,
                GamePacketTypes.SERVERBOUND_JIGSAW_GENERATE,
                GamePacketTypes.SERVERBOUND_LOCK_DIFFICULTY,
                GamePacketTypes.SERVERBOUND_RECIPE_BOOK_CHANGE_SETTINGS,
                GamePacketTypes.SERVERBOUND_RECIPE_BOOK_SEEN_RECIPE,
                GamePacketTypes.SERVERBOUND_SEEN_ADVANCEMENTS,
                GamePacketTypes.SERVERBOUND_SET_COMMAND_BLOCK,
                GamePacketTypes.SERVERBOUND_SET_COMMAND_MINECART,
                GamePacketTypes.SERVERBOUND_SET_GAME_RULE,
                GamePacketTypes.SERVERBOUND_SET_JIGSAW_BLOCK,
                GamePacketTypes.SERVERBOUND_SET_STRUCTURE_BLOCK,
                GamePacketTypes.SERVERBOUND_SET_TEST_BLOCK,
                GamePacketTypes.SERVERBOUND_SIGN_UPDATE,
                GamePacketTypes.SERVERBOUND_TEST_INSTANCE_BLOCK_ACTION,
                GamePacketTypes.CLIENTBOUND_AWARD_STATS,
                GamePacketTypes.CLIENTBOUND_BOSS_EVENT,
                GamePacketTypes.CLIENTBOUND_CHANGE_DIFFICULTY,
                GamePacketTypes.CLIENTBOUND_CLEAR_TITLES,
                GamePacketTypes.CLIENTBOUND_COMMAND_SUGGESTIONS,
                GamePacketTypes.CLIENTBOUND_COMMANDS,
                GamePacketTypes.CLIENTBOUND_CONTAINER_CLOSE,
                GamePacketTypes.CLIENTBOUND_CONTAINER_SET_CONTENT,
                GamePacketTypes.CLIENTBOUND_CONTAINER_SET_DATA,
                GamePacketTypes.CLIENTBOUND_CONTAINER_SET_SLOT,
                GamePacketTypes.CLIENTBOUND_COOLDOWN,
                GamePacketTypes.CLIENTBOUND_CUSTOM_CHAT_COMPLETIONS,
                GamePacketTypes.CLIENTBOUND_DEBUG_EVENT,
                GamePacketTypes.CLIENTBOUND_DEBUG_SAMPLE,
                GamePacketTypes.CLIENTBOUND_DELETE_CHAT,
                GamePacketTypes.CLIENTBOUND_DISGUISED_CHAT,
                GamePacketTypes.CLIENTBOUND_GAME_RULE_VALUES,
                GamePacketTypes.CLIENTBOUND_INITIALIZE_BORDER,
                GamePacketTypes.CLIENTBOUND_LOW_DISK_SPACE_WARNING,
                GamePacketTypes.CLIENTBOUND_MERCHANT_OFFERS,
                GamePacketTypes.CLIENTBOUND_MOUNT_SCREEN_OPEN,
                GamePacketTypes.CLIENTBOUND_OPEN_BOOK,
                GamePacketTypes.CLIENTBOUND_OPEN_SCREEN,
                GamePacketTypes.CLIENTBOUND_PLACE_GHOST_RECIPE,
                GamePacketTypes.CLIENTBOUND_PLAYER_CHAT,
                GamePacketTypes.CLIENTBOUND_PLAYER_INFO_REMOVE,
                GamePacketTypes.CLIENTBOUND_RECIPE_BOOK_ADD,
                GamePacketTypes.CLIENTBOUND_RECIPE_BOOK_REMOVE,
                GamePacketTypes.CLIENTBOUND_RECIPE_BOOK_SETTINGS,
                GamePacketTypes.CLIENTBOUND_RESET_SCORE,
                GamePacketTypes.CLIENTBOUND_SELECT_ADVANCEMENTS_TAB,
                GamePacketTypes.CLIENTBOUND_SERVER_DATA,
                GamePacketTypes.CLIENTBOUND_SET_ACTION_BAR_TEXT,
                GamePacketTypes.CLIENTBOUND_SET_BORDER_CENTER,
                GamePacketTypes.CLIENTBOUND_SET_BORDER_LERP_SIZE,
                GamePacketTypes.CLIENTBOUND_SET_BORDER_SIZE,
                GamePacketTypes.CLIENTBOUND_SET_BORDER_WARNING_DELAY,
                GamePacketTypes.CLIENTBOUND_SET_BORDER_WARNING_DISTANCE,
                GamePacketTypes.CLIENTBOUND_SET_CURSOR_ITEM,
                GamePacketTypes.CLIENTBOUND_SET_DEFAULT_SPAWN_POSITION,
                GamePacketTypes.CLIENTBOUND_SET_DISPLAY_OBJECTIVE,
                GamePacketTypes.CLIENTBOUND_SET_EXPERIENCE,
                GamePacketTypes.CLIENTBOUND_SET_HELD_SLOT,
                GamePacketTypes.CLIENTBOUND_SET_OBJECTIVE,
                GamePacketTypes.CLIENTBOUND_SET_PLAYER_INVENTORY,
                GamePacketTypes.CLIENTBOUND_SET_PLAYER_TEAM,
                GamePacketTypes.CLIENTBOUND_SET_SCORE,
                GamePacketTypes.CLIENTBOUND_SET_SUBTITLE_TEXT,
                GamePacketTypes.CLIENTBOUND_SET_TITLE_TEXT,
                GamePacketTypes.CLIENTBOUND_SET_TITLES_ANIMATION,
                GamePacketTypes.CLIENTBOUND_SYSTEM_CHAT,
                GamePacketTypes.CLIENTBOUND_TAB_LIST,
                GamePacketTypes.CLIENTBOUND_TAG_QUERY,
                GamePacketTypes.CLIENTBOUND_TEST_INSTANCE_BLOCK_STATUS,
                GamePacketTypes.CLIENTBOUND_UPDATE_ADVANCEMENTS,
                GamePacketTypes.CLIENTBOUND_UPDATE_RECIPES,
                GamePacketTypes.CLIENTBOUND_WAYPOINT
        );

        ambient(
                GamePacketTypes.CLIENTBOUND_LEVEL_EVENT,
                GamePacketTypes.CLIENTBOUND_LEVEL_PARTICLES,
                GamePacketTypes.CLIENTBOUND_MAP_ITEM_DATA,
                GamePacketTypes.CLIENTBOUND_SET_TIME,
                GamePacketTypes.CLIENTBOUND_SOUND,
                GamePacketTypes.CLIENTBOUND_SOUND_ENTITY,
                GamePacketTypes.CLIENTBOUND_STOP_SOUND
        );

        datagram(
                GamePacketTypes.CLIENTBOUND_LEVEL_PARTICLES,
                GamePacketTypes.CLIENTBOUND_SOUND,
                GamePacketTypes.CLIENTBOUND_SOUND_ENTITY,
                GamePacketTypes.CLIENTBOUND_LEVEL_EVENT,
                GamePacketTypes.CLIENTBOUND_SET_TIME,
                GamePacketTypes.CLIENTBOUND_ANIMATE,
                GamePacketTypes.CLIENTBOUND_HURT_ANIMATION,
                GamePacketTypes.CLIENTBOUND_DAMAGE_EVENT
        );

        world(
                GamePacketTypes.SERVERBOUND_CHUNK_BATCH_RECEIVED,
                GamePacketTypes.CLIENTBOUND_BLOCK_CHANGED_ACK,
                GamePacketTypes.CLIENTBOUND_BLOCK_DESTRUCTION,
                GamePacketTypes.CLIENTBOUND_BLOCK_ENTITY_DATA,
                GamePacketTypes.CLIENTBOUND_BLOCK_EVENT,
                GamePacketTypes.CLIENTBOUND_BLOCK_UPDATE,
                GamePacketTypes.CLIENTBOUND_CHUNK_BATCH_FINISHED,
                GamePacketTypes.CLIENTBOUND_CHUNK_BATCH_START,
                GamePacketTypes.CLIENTBOUND_CHUNKS_BIOMES,
                GamePacketTypes.CLIENTBOUND_DEBUG_BLOCK_VALUE,
                GamePacketTypes.CLIENTBOUND_DEBUG_CHUNK_VALUE,
                GamePacketTypes.CLIENTBOUND_FORGET_LEVEL_CHUNK,
                GamePacketTypes.CLIENTBOUND_GAME_TEST_HIGHLIGHT_POS,
                GamePacketTypes.CLIENTBOUND_LEVEL_CHUNK_WITH_LIGHT,
                GamePacketTypes.CLIENTBOUND_LIGHT_UPDATE,
                GamePacketTypes.CLIENTBOUND_OPEN_SIGN_EDITOR,
                GamePacketTypes.CLIENTBOUND_SECTION_BLOCKS_UPDATE,
                GamePacketTypes.CLIENTBOUND_SET_CHUNK_CACHE_CENTER,
                GamePacketTypes.CLIENTBOUND_SET_CHUNK_CACHE_RADIUS,
                GamePacketTypes.CLIENTBOUND_SET_SIMULATION_DISTANCE
        );
    }

    public static PacketCategory categoryOf(@Nullable PacketType<?> type) {
        if (type == null) {
            return PacketCategory.CONTROL;
        }
        PacketCategory category = CATEGORIES.get(type);
        return category == null ? PacketCategory.CONTROL : category;
    }

    public static boolean isClassified(PacketType<?> type) {
        return CATEGORIES.containsKey(type);
    }

    public static boolean isBarrier(@Nullable PacketType<?> type) {
        return type != null && BARRIERS.contains(type);
    }

    public static boolean isPlayEntry(@Nullable PacketType<?> type) {
        return type != null && PLAY_ENTRIES.contains(type);
    }

    public static boolean isTerminal(@Nullable PacketType<?> type) {
        return type != null && TERMINALS.contains(type);
    }

    public static boolean isDatagram(@Nullable PacketType<?> type) {
        return type != null && DATAGRAMS.contains(type);
    }

    public static BarrierClassifier.@Nullable Barrier barrierOf(Object msg) {
        if (!(msg instanceof net.minecraft.network.protocol.Packet<?> packet)) {
            return null;
        }
        PacketType<?> type = packet.type();
        if (!isBarrier(type)) {
            return null;
        }
        return new BarrierClassifier.Barrier(isTerminal(type), isPlayEntry(type));
    }

    private static void put(PacketCategory category, PacketType<?>... types) {
        for (PacketType<?> type : types) {
            if (CATEGORIES.put(type, category) != null) {
                throw new IllegalStateException("duplicate routing entry for " + type);
            }
        }
    }

    private static void control(PacketType<?>... types) {
        put(PacketCategory.CONTROL, types);
    }

    private static void barrier(PacketType<?>... types) {
        put(PacketCategory.CONTROL, types);
        BARRIERS.addAll(java.util.Arrays.asList(types));
    }

    private static void realtime(PacketType<?>... types) {
        put(PacketCategory.REALTIME, types);
    }

    private static void ui(PacketType<?>... types) {
        put(PacketCategory.UI, types);
    }

    private static void ambient(PacketType<?>... types) {
        put(PacketCategory.AMBIENT, types);
    }

    private static void world(PacketType<?>... types) {
        put(PacketCategory.WORLD, types);
    }

    private static void datagram(PacketType<?>... types) {
        for (PacketType<?> type : types) {
            PacketCategory category = CATEGORIES.get(type);
            if (category != PacketCategory.AMBIENT && category != PacketCategory.REALTIME) {
                throw new IllegalStateException("datagram entry " + type + " must be routed to AMBIENT or REALTIME first, was " + category);
            }
            if (BARRIERS.contains(type)) {
                throw new IllegalStateException("barrier " + type + " cannot travel on a datagram");
            }
            if (!DATAGRAMS.add(type)) {
                throw new IllegalStateException("duplicate datagram entry for " + type);
            }
        }
    }
}
