package com.hfstudio.minopp.render;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;

import com.hfstudio.minopp.entity.EntityAutoPlayer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class EntityAutoPlayerRenderer extends RenderBiped {

    public EntityAutoPlayerRenderer() {
        super(new ModelBiped(0.0F), 0.5f);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityLiving entity) {
        return getTexture((EntityAutoPlayer) entity);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return getTexture((EntityAutoPlayer) entity);
    }

    private ResourceLocation getTexture(EntityAutoPlayer entity) {
        if (entity.clientSkinLocation != null) return entity.clientSkinLocation;
        return AbstractClientPlayer.locationStevePng;
    }

    @Override
    public void doRender(EntityLiving entity, double x, double y, double z, float yaw, float partialTicks) {
        modelBipedMain.heldItemRight = entity.getHeldItem() != null ? 1 : 0;
        super.doRender(entity, x, y, z, yaw, partialTicks);
    }
}
