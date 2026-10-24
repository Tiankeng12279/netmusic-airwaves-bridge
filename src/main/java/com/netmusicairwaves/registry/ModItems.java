package com.netmusicairwaves.registry;

import com.netmusicairwaves.NetMusicAirwaves;
import com.netmusicairwaves.content.RadioModulatorItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NetMusicAirwaves.MOD_ID);

    public static final DeferredItem<Item> RADIO_MODULATOR = ITEMS.register("netmusic_radio_modulator",
            () -> new RadioModulatorItem(ModBlocks.RADIO_MODULATOR.get(), new Item.Properties()));

    public static final DeferredItem<Item> SPECTRUM_MONITOR = ITEMS.register("netmusic_spectrum_monitor",
            () -> new RadioModulatorItem(ModBlocks.SPECTRUM_MONITOR.get(), new Item.Properties()));

    private ModItems() {
    }
}
