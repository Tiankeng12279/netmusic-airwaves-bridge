package com.netmusicairwaves.menu;

import com.netmusicairwaves.content.RadioModulatorBlockEntity;
import com.netmusicairwaves.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

/**
 * Menu behind the spectrum deck GUI: identical CD slots and behavior to the
 * plain deck menu, but registered under its own type so the client opens the
 * analyzer-style screen, with the inventory placed lower for the taller panel.
 */
public final class SpectrumDeckMenu extends DeckMenu {
    public static final int PLAYER_INV_Y = 254;
    public static final int PLAYER_HOTBAR_Y = 312;

    public SpectrumDeckMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(containerId, playerInventory, buf.readBlockPos(), null, ModMenus.SPECTRUM_DECK.get());
    }

    public SpectrumDeckMenu(int containerId, Inventory playerInventory, BlockPos pos,
                            @Nullable RadioModulatorBlockEntity deck) {
        super(containerId, playerInventory, pos, deck, ModMenus.SPECTRUM_DECK.get());
    }

    @Override
    protected int playerInvY() {
        return PLAYER_INV_Y;
    }

    @Override
    protected int playerHotbarY() {
        return PLAYER_HOTBAR_Y;
    }
}
