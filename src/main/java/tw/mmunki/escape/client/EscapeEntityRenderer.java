package tw.mmunki.escape.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import tw.mmunki.escape.Escape;
import tw.mmunki.escape.entity.EscapeEntity;

import java.util.UUID;

public final class EscapeEntityRenderer extends HumanoidMobRenderer<EscapeEntity, EscapeRenderState,
        HumanoidModel<EscapeRenderState>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Escape.id("escape"), "main");

    public EscapeEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(LAYER)), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this,
                ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new),
                context.getEquipmentRenderer()));
    }

    @Override
    public EscapeRenderState createRenderState() {
        return new EscapeRenderState();
    }

    @Override
    public void extractRenderState(EscapeEntity entity, EscapeRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.skinTexture = null;
        UUID uuid = entity.getTargetUuid();
        if (uuid == null) {
            return;
        }
        Player player = entity.level().getPlayerByUUID(uuid);
        if (player instanceof AbstractClientPlayer clientPlayer) {
            state.skinTexture = clientPlayer.getSkin().body().texturePath();
        } else {
            state.skinTexture = DefaultPlayerSkin.get(uuid).body().texturePath();
        }
    }

    @Override
    public Identifier getTextureLocation(EscapeRenderState state) {
        return state.skinTexture == null ? DefaultPlayerSkin.getDefaultTexture() : state.skinTexture;
    }
}
