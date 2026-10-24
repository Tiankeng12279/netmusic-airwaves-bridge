package com.netmusicairwaves.client;

import com.netmusicairwaves.NetMusicAirwaves;
import com.netmusicairwaves.registry.ModBlockEntities;
import com.netmusicairwaves.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = NetMusicAirwaves.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.RADIO_MODULATOR.get(), context -> new RadioDeckRenderer());
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SPECTRUM_DECK.get(), SpectrumDeckScreen::new);
    }

    private ClientSetup() {
    }
}
