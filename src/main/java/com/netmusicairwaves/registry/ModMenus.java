package com.netmusicairwaves.registry;

import com.netmusicairwaves.NetMusicAirwaves;
import com.netmusicairwaves.menu.SpectrumDeckMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, NetMusicAirwaves.MOD_ID);

    // The plain radio deck is GUI-less; only the spectrum deck opens a menu.
    public static final DeferredHolder<MenuType<?>, MenuType<SpectrumDeckMenu>> SPECTRUM_DECK =
            MENUS.register("netmusic_spectrum_deck", () -> IMenuTypeExtension.create(SpectrumDeckMenu::new));

    private ModMenus() {
    }
}
