package de.hysky.skyblocker.skyblock.dungeon.secrets;

import com.google.gson.JsonObject;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.hysky.skyblocker.config.SkyblockerConfig;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.utils.render.RenderHelper;
import de.hysky.skyblocker.utils.waypoint.Waypoint;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.EnumArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

public class SecretWaypoint extends Waypoint {
    protected static final Logger LOGGER = LoggerFactory.getLogger(SecretWaypoint.class);
    public static final Codec<SecretWaypoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("secretIndex").forGetter(secretWaypoint -> secretWaypoint.secretIndex),
            Category.CODEC.fieldOf("category").forGetter(secretWaypoint -> secretWaypoint.category),
            Codecs.TEXT.fieldOf("name").forGetter(secretWaypoint -> secretWaypoint.name),
            BlockPos.CODEC.fieldOf("pos").forGetter(secretWaypoint -> secretWaypoint.pos)
    ).apply(instance, SecretWaypoint::new));
    public static final Codec<List<SecretWaypoint>> LIST_CODEC = CODEC.listOf();
    static final List<String> SECRET_ITEMS = List.of("Decoy", "Defuse Kit", "Dungeon Chest Key", "Healing VIII", "Inflatable Jerry", "Spirit Leap", "Training Weights", "Trap", "Treasure Talisman");
    private static final Supplier<SkyblockerConfig.SecretWaypoints> CONFIG = () -> SkyblockerConfigManager.get().locations.dungeons.secretWaypoints;
    private static final Supplier<Type> TYPE_SUPPLIER = () -> CONFIG.get().waypointType;
    final int secretIndex;
    final Category category;
    final Text name;
    private final Vec3d centerPos;

    SecretWaypoint(int secretIndex, JsonObject waypoint, String name, BlockPos pos) {
        this(secretIndex, Category.get(waypoint), name, pos);
    }

    SecretWaypoint(int secretIndex, Category category, String name, BlockPos pos) {
        this(secretIndex, category, Text.of(name), pos);
    }

    SecretWaypoint(int secretIndex, Category category, Text name, BlockPos pos) {
        super(pos, TYPE_SUPPLIER, category.colorComponents);
        this.secretIndex = secretIndex;
        this.category = category;
        this.name = name;
        this.centerPos = pos.toCenterPos();
    }

    static ToDoubleFunction<SecretWaypoint> getSquaredDistanceToFunction(Entity entity) {
        return secretWaypoint -> entity.squaredDistanceTo(secretWaypoint.centerPos);
    }

    static Predicate<SecretWaypoint> getRangePredicate(Entity entity) {
        return secretWaypoint -> entity.squaredDistanceTo(secretWaypoint.centerPos) <= 36D;
    }

    @Override
    public boolean shouldRender() {
        return super.shouldRender() && category.isEnabled();
    }

    boolean needsInteraction() {
        return category.needsInteraction();
    }

    boolean isLever() {
        return category.isLever();
    }

    boolean needsItemPickup() {
        return category.needsItemPickup();
    }

    boolean isBat() {
        return category.isBat();
    }

    /**
     * Renders the secret waypoint, including a filled cube, a beacon beam, the name, and the distance from the player.
     */
    @Override
    public void render(WorldRenderContext context) {
        //TODO In the future, shrink the box for wither essence and items so its more realistic
        super.render(context);

        if (CONFIG.get().showSecretText) {
            Vec3d posUp = centerPos.add(0, 1, 0);
            RenderHelper.renderText(context, name, posUp, true);
            double distance = context.camera().getPos().distanceTo(centerPos);
            RenderHelper.renderText(context, Text.literal(Math.round(distance) + "m").formatted(Formatting.YELLOW), posUp, 1, MinecraftClient.getInstance().textRenderer.fontHeight + 1, true);
        }
    }

    @NotNull
    SecretWaypoint relativeToActual(Room room) {
        return new SecretWaypoint(secretIndex, category, name, room.relativeToActual(pos));
    }

    enum Category implements StringIdentifiable {
        ENTRANCE("entrance", secretWaypoints -> secretWaypoints.enableEntranceWaypoints, 0, 255, 0),
        SUPERBOOM("superboom", secretWaypoints -> secretWaypoints.enableSuperboomWaypoints, 255, 0, 0),
        CHEST("chest", secretWaypoints -> secretWaypoints.enableChestWaypoints, 2, 213, 250),
        ITEM("item", secretWaypoints -> secretWaypoints.enableItemWaypoints, 2, 64, 250),
        BAT("bat", secretWaypoints -> secretWaypoints.enableBatWaypoints, 142, 66, 0),
        WITHER("wither", secretWaypoints -> secretWaypoints.enableWitherWaypoints, 30, 30, 30),
        LEVER("lever", secretWaypoints -> secretWaypoints.enableLeverWaypoints, 250, 217, 2),
        FAIRYSOUL("fairysoul", secretWaypoints -> secretWaypoints.enableFairySoulWaypoints, 255, 85, 255),
        STONK("stonk", secretWaypoints -> secretWaypoints.enableStonkWaypoints, 146, 52, 235),
        AOTV("aotv", secretWaypoints -> secretWaypoints.enableAotvWaypoints, 252, 98, 3),
        PEARL("pearl", secretWaypoints -> secretWaypoints.enablePearlWaypoints, 57, 117, 125),
        DEFAULT("default", secretWaypoints -> secretWaypoints.enableDefaultWaypoints, 190, 255, 252);
        private static final Codec<Category> CODEC = StringIdentifiable.createCodec(Category::values);
        private final String name;
        private final Predicate<SkyblockerConfig.SecretWaypoints> enabledPredicate;
        private final float[] colorComponents;

        Category(String name, Predicate<SkyblockerConfig.SecretWaypoints> enabledPredicate, int... intColorComponents) {
            this.name = name;
            this.enabledPredicate = enabledPredicate;
            colorComponents = new float[intColorComponents.length];
            for (int i = 0; i < intColorComponents.length; i++) {
                colorComponents[i] = intColorComponents[i] / 255F;
            }
        }

        static Category get(JsonObject waypointJson) {
            return CODEC.parse(JsonOps.INSTANCE, waypointJson.get("category")).resultOrPartial(LOGGER::error).orElse(Category.DEFAULT);
        }

        boolean needsInteraction() {
            return this == CHEST || this == WITHER;
        }

        boolean isLever() {
            return this == LEVER;
        }

        boolean needsItemPickup() {
            return this == ITEM;
        }

        boolean isBat() {
            return this == BAT;
        }

        boolean isEnabled() {
            return enabledPredicate.test(SkyblockerConfigManager.get().locations.dungeons.secretWaypoints);
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public String asString() {
            return name;
        }

        static class CategoryArgumentType extends EnumArgumentType<Category> {
            public CategoryArgumentType() {
                super(Category.CODEC, Category::values);
            }

            public static CategoryArgumentType category() {
                return new CategoryArgumentType();
            }

            public static <S> Category getCategory(CommandContext<S> context, String name) {
                return context.getArgument(name, Category.class);
            }
        }
    }
}
