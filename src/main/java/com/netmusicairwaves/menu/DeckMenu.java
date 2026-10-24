package com.netmusicairwaves.menu;

import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import com.netmusicairwaves.content.RadioModulatorBlockEntity;
import com.netmusicairwaves.network.DeckSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Container menu behind the radio deck GUI: 16 CD slots (four per page) plus the
 * player inventory. CD slots only accept NetMusic CDs and only the slots of the
 * visible page are clickable, so pagination stays a pure client-side view.
 */
public class DeckMenu extends AbstractContainerMenu {
    public static final int PAGE_SIZE = 4;
    public static final int PAGES = DeckSnapshot.SLOT_COUNT / PAGE_SIZE;

    // Slot coordinates are relative to the window's left/top edge (see RadioDeckScreen).
    public static final int CARD_X = 12;
    public static final int CARD_Y = 23;
    public static final int SLOT_INSET = 3;
    public static final int ROW_HEIGHT = 26;
    public static final int PLAYER_INV_X = 89;
    public static final int PLAYER_INV_Y = 240;
    public static final int PLAYER_HOTBAR_Y = 298;

    private final BlockPos pos;
    @Nullable
    private final RadioModulatorBlockEntity deck;
    private int page;
    private boolean hadAnyDisc;

    protected DeckMenu(int containerId, Inventory playerInventory, BlockPos pos,
                       @Nullable RadioModulatorBlockEntity deck, MenuType<?> type) {
        super(type, containerId);
        this.pos = pos;
        this.deck = deck;
        this.hadAnyDisc = deck != null && deck.hasAnyDisc();

        IItemHandler discs = handlerFor(deck);
        for (int i = 0; i < DeckSnapshot.SLOT_COUNT; i++) {
            int row = i % PAGE_SIZE;
            addSlot(new DeckSlot(discs, i, CARD_X + SLOT_INSET, CARD_Y + row * ROW_HEIGHT + SLOT_INSET));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + column,
                        PLAYER_INV_X + column * 18, playerInvY() + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, PLAYER_INV_X + column * 18, playerHotbarY()));
        }
    }

    /** Y of the player inventory grid inside the screen; the spectrum layout is taller. */
    protected int playerInvY() {
        return PLAYER_INV_Y;
    }

    protected int playerHotbarY() {
        return PLAYER_HOTBAR_Y;
    }

    private static IItemHandler handlerFor(@Nullable RadioModulatorBlockEntity deck) {
        if (deck != null) {
            return deck.getInventory();
        }
        // Client side: a stand-in handler that accepts the same items; the server
        // menu syncs the real contents into it.
        return new ItemStackHandler(DeckSnapshot.SLOT_COUNT) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return ItemMusicCD.getSongInfo(stack) != null;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, Math.min(PAGES - 1, page));
    }

    @Override
    public void clicked(int slotIndex, int button, net.minecraft.world.inventory.ClickType clickType,
                        Player player) {
        if (slotIndex >= 0 && slotIndex < DeckSnapshot.SLOT_COUNT && !slots.get(slotIndex).isActive()) {
            return;
        }
        super.clicked(slotIndex, button, clickType, player);
        if (deck != null && player instanceof ServerPlayer) {
            boolean nowHasDisc = deck.hasAnyDisc();
            if (nowHasDisc && !hadAnyDisc) {
                deck.tryAutoStart(player);
            }
            hadAnyDisc = nowHasDisc;
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < DeckSnapshot.SLOT_COUNT) {
            if (!moveItemStackTo(source, DeckSnapshot.SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (ItemMusicCD.getSongInfo(source) == null) {
            return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, 0, DeckSnapshot.SLOT_COUNT, false)) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        if (deck == null) {
            return true;
        }
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D
                && player.level().getBlockEntity(pos) == deck;
    }

    private final class DeckSlot extends SlotItemHandler {
        private final int slot;

        private DeckSlot(IItemHandler handler, int slot, int x, int y) {
            super(handler, slot, x, y);
            this.slot = slot;
        }

        @Override
        public boolean isActive() {
            return slot / PAGE_SIZE == page;
        }

        // Carousel locks only apply to automation (hoppers and friends going through
        // the block capability); the GUI is manual, so players may freely move discs
        // in and out of any deck slot.

        @Override
        public boolean mayPlace(ItemStack stack) {
            return ItemMusicCD.getSongInfo(stack) != null;
        }

        @Override
        public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
            return !getItemHandler().getStackInSlot(getSlotIndex()).isEmpty();
        }

        @Override
        public ItemStack remove(int amount) {
            IItemHandler handler = getItemHandler();
            ItemStack stack = handler.getStackInSlot(getSlotIndex());
            if (stack.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = stack.copyWithCount(Math.min(amount, stack.getCount()));
            ItemStack remaining = stack.copyWithCount(stack.getCount() - taken.getCount());
            if (handler instanceof ItemStackHandler itemStackHandler) {
                itemStackHandler.setStackInSlot(getSlotIndex(), remaining);
            }
            return taken;
        }
    }
}
