package tw.mmunki.escape.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import tw.mmunki.escape.Escape;

public final class EscapeEntities {
    public static final EntityType<EscapeEntity> ESCAPE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Escape.id("escape"),
            EntityType.Builder.of(EscapeEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(12)
                    .noLootTable()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Escape.id("escape"))));

    private EscapeEntities() {
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(ESCAPE, EscapeEntity.createAttributes());
    }
}
