package com.netmusicairwaves.content;

import com.netmusicairwaves.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class RadioModulatorItem extends BlockItem {
    public RadioModulatorItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        boolean spectrum = getBlock() == ModBlocks.SPECTRUM_MONITOR.get();
        tooltip.add(Component.translatable(spectrum
                ? "tooltip.netmusic_airwaves.spectrum_monitor"
                : "tooltip.netmusic_airwaves.radio_modulator").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(spectrum
                ? "tooltip.netmusic_airwaves.spectrum_monitor.usage"
                : "tooltip.netmusic_airwaves.radio_modulator.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
