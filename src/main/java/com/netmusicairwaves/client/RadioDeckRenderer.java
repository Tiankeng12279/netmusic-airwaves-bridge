package com.netmusicairwaves.client;

import com.airwaves.client.CableRenderer;
import com.netmusicairwaves.content.RadioModulatorBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

/**
 * Renders the Airwaves cables attached to both deck blocks. The live spectrum
 * lives in the playlist player's GUI only; the block face is left to the
 * blockstate-driven models.
 */
public final class RadioDeckRenderer implements BlockEntityRenderer<RadioModulatorBlockEntity> {
    private final CableRenderer<RadioModulatorBlockEntity> cables = new CableRenderer<>();

    @Override
    public void render(RadioModulatorBlockEntity blockEntity, float partialTick,
                       com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        cables.render(blockEntity, partialTick, poseStack, buffer, packedLight, packedOverlay);
    }
}
