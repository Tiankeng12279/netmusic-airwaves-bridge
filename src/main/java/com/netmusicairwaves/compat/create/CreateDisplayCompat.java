package com.netmusicairwaves.compat.create;

import com.netmusicairwaves.NetMusicAirwaves;
import com.netmusicairwaves.registry.ModBlockEntities;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class CreateDisplayCompat {
    public static final ResourceLocation SOURCE_ID =
            ResourceLocation.fromNamespaceAndPath(NetMusicAirwaves.MOD_ID, "netmusic_radio_deck");
    public static final NetMusicRadioDisplaySource SOURCE = NetMusicRadioDisplaySource.INSTANCE;

    private CreateDisplayCompat() {
    }

    public static void registerDisplaySource(RegisterEvent event) {
        if (event.getRegistryKey().equals(CreateRegistries.DISPLAY_SOURCE)) {
            event.register(CreateRegistries.DISPLAY_SOURCE, SOURCE_ID, () -> SOURCE);
        }
    }

    public static void associateDisplaySource(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> DisplaySource.BY_BLOCK_ENTITY.add(
                ModBlockEntities.RADIO_MODULATOR.get(), SOURCE));
    }
}
