package com.netmusicairwaves;

import com.netmusicairwaves.compat.create.CreateDisplayCompat;
import com.netmusicairwaves.network.DeckNetwork;
import com.netmusicairwaves.registry.ModBlockEntities;
import com.netmusicairwaves.registry.ModBlocks;
import com.netmusicairwaves.registry.ModItems;
import com.netmusicairwaves.registry.ModMenus;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(NetMusicAirwaves.MOD_ID)
public final class NetMusicAirwaves {
    public static final String MOD_ID = "netmusic_airwaves";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public NetMusicAirwaves(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);

        modBus.addListener(this::registerCapabilities);
        modBus.addListener(this::addCreativeTabItems);
        modBus.addListener(CreateDisplayCompat::registerDisplaySource);
        modBus.addListener(CreateDisplayCompat::associateDisplaySource);
        modBus.addListener(DeckNetwork::register);
        NeoForge.EVENT_BUS.addListener(NetMusicAirwaves::onServerStopping);

        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.RADIO_MODULATOR.get(),
                (blockEntity, side) -> blockEntity.getInventory());
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.RADIO_MODULATOR.get());
            event.accept(ModItems.SPECTRUM_MONITOR.get());
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        com.netmusicairwaves.content.RadioModulatorBlockEntity.stopAll();
    }
}
