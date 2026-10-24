package com.netmusicairwaves.content;

import com.airwaves.api.AirwavesApi;
import com.airwaves.be.CableNode;
import com.airwaves.be.LinkSet;
import com.airwaves.be.TransmitterBE;
import com.airwaves.logic.Cables;
import com.github.tartaricacid.netmusic.api.resolver.MusicPlayResolverManager;
import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import com.netmusicairwaves.Config;
import com.netmusicairwaves.NetMusicAirwaves;
import com.netmusicairwaves.audio.AudioDecoder;
import com.netmusicairwaves.audio.RadioAudioStream;
import com.netmusicairwaves.audio.SpectrumAnalyzer;
import com.netmusicairwaves.network.DeckActionPayload;
import com.netmusicairwaves.network.DeckNetwork;
import com.netmusicairwaves.network.DeckSnapshot;
import com.netmusicairwaves.registry.ModBlockEntities;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RadioModulatorBlockEntity extends BlockEntity implements CableNode, MenuProvider {
    public static final int SLOT_COUNT = DeckSnapshot.SLOT_COUNT;
    public static final int PLAY_MODE_SEQUENTIAL = 0;
    public static final int PLAY_MODE_SHUFFLE = 1;
    public static final int PLAY_MODE_LOOP = 2;
    private static final int STATION_SCAN_INTERVAL = 20;
    private static final int SPECTRUM_INTERVAL = 10;
    private static final Set<RadioModulatorBlockEntity> LOADED = ConcurrentHashMap.newKeySet();
    private static final String INVENTORY_TAG = "Cds";

    private final LinkSet links = new LinkSet();
    private final DeckInventory inventory = new DeckInventory();

    /**
     * Carousel (shift-eject) mode: the input slot is the playing position, middle
     * slots are locked to automation, and when a disc finishes every item shifts
     * one slot toward the output slot (which hoppers may extract).
     */
    private boolean carousel;
    /** Play order: 0 = sequential, 1 = shuffle, 2 = single-disc loop. */
    private int playMode;
    /** Play order: false = first slot to last, true = last slot to first. */
    private boolean reverse;
    /** A finished disc is parked at the input slot because the output slot is occupied. */
    private boolean carouselWaiting;
    /** Set while the deck moves items internally, so change hooks stay quiet. */
    private boolean shifting;

    private class DeckInventory extends ItemStackHandler {
        DeckInventory() {
            super(SLOT_COUNT);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (ItemMusicCD.getSongInfo(stack) == null) {
                return false;
            }
            // Carousel mode only accepts discs at the input slot.
            return !carousel || slot == inputSlot();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Carousel mode only releases discs from the output slot.
            if (carousel && slot != outputSlot()) {
                return ItemStack.EMPTY;
            }
            return super.extractItem(slot, amount, simulate);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (loading || shifting) {
                return;
            }
            if (!(level instanceof ServerLevel serverLevel)) {
                return;
            }
            updateBlockState();
            if (carousel && carouselWaiting && slot == outputSlot() && getCd(outputSlot()).isEmpty()) {
                // The blocked output just cleared: shift the queue and resume.
                carouselWaiting = false;
                advanceCarousel();
                return;
            }
            if (carousel && !carouselWaiting && state == DeckSnapshot.STATE_STOPPED
                    && slot == inputSlot() && !getCd(inputSlot()).isEmpty()) {
                if (playMode == PLAY_MODE_SHUFFLE) {
                    // Shuffle carousel plays middle slots only; pull the input disc
                    // in when the middle pool is empty (bootstrap).
                    if (randomMiddleSlot() < 0) {
                        startCarousel(null);
                    }
                } else {
                    startSlot(inputSlot(), null);
                }
                return;
            }
            if (currentSlot >= 0 && inventory.getStackInSlot(currentSlot).isEmpty()
                    && state != DeckSnapshot.STATE_STOPPED) {
                stopPlayback();
            } else {
                broadcast(serverLevel);
            }
        }
    }

    private boolean loading;
    private int state = DeckSnapshot.STATE_STOPPED;
    private int currentSlot = -1;
    private boolean hasSignal;
    private boolean onAir;
    private boolean positioned;
    private boolean failureAnnounced;
    private boolean hasConnectedTransmitter;
    private int playTicks;
    private int totalTicks;
    private int frameIndex;
    private final int[] spectrumBands = new int[DeckSnapshot.BAND_COUNT];

    @Nullable
    private RadioAudioStream audioStream;
    @Nullable
    private Connection connection;

    public RadioModulatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RADIO_MODULATOR.get(), pos, state);
    }

    public IItemHandler getInventory() {
        return inventory;
    }

    public ItemStack getCd(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    public int getAnalogSignal() {
        if (state == DeckSnapshot.STATE_PLAYING) {
            return 15;
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return 7;
            }
        }
        return 0;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            LOADED.add(this);
            updateBlockState();
        }
    }

    @Override
    public void setRemoved() {
        LOADED.remove(this);
        onBlockRemoved();
        super.setRemoved();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RadioModulatorBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel) || level.isClientSide) {
            return;
        }
        blockEntity.tick(serverLevel);
    }

    private void tick(ServerLevel serverLevel) {
        if (carouselWaiting) {
            if (!carousel) {
                carouselWaiting = false;
            } else if (getCd(outputSlot()).isEmpty()) {
                carouselWaiting = false;
                advanceCarousel();
            }
        }
        if (serverLevel.getGameTime() % STATION_SCAN_INTERVAL == 0) {
            refreshConnection(serverLevel);
        }
        if (state != DeckSnapshot.STATE_PLAYING) {
            return;
        }

        playTicks++;
        if (playTicks > totalTicks) {
            advanceOrStop();
            return;
        }

        ItemMusicCD.SongInfo info = getCurrentInfo();
        if (info == null) {
            stopPlayback();
            return;
        }
        if (audioStream == null) {
            return;
        }
        if (audioStream.isFailed()) {
            if (!failureAnnounced) {
                failureAnnounced = true;
                announceNearby("message.netmusic_airwaves.source_failed",
                        info.songName, shorten(audioStream.getError()));
            }
            stopPlayback();
            return;
        }
        if (!audioStream.isReady()) {
            setOnAir(false);
            return;
        }
        if (connection == null) {
            positioned = false;
            setOnAir(false);
            return;
        }

        if (!positioned) {
            frameIndex = Math.min(playTicks, Math.max(0, audioStream.getFrameCount() - 1));
            positioned = true;
        }
        if (frameIndex >= audioStream.getFrameCount()) {
            advanceOrStop();
            return;
        }

        byte[] frame = audioStream.getFrame(frameIndex++);
        if (frame == null) {
            return;
        }
        float lineGain = Math.max(0.05F, Math.min(1.0F, connection.lineQuality));
        float gain = (float) Math.max(0.0D, Math.min(1.0D, Config.BROADCAST_GAIN.get() * lineGain));
        boolean accepted = AirwavesApi.broadcast(serverLevel, connection.transmitterPos.asLong(),
                getSourceId(), frame, gain);
        setOnAir(accepted);
        if (accepted) {
            if (serverLevel.getGameTime() % 10 == 0) {
                lightTransmitter(serverLevel);
            }
            // The live spectrum plus fresh elapsed time ride along on a light periodic broadcast.
            if (serverLevel.getGameTime() % SPECTRUM_INTERVAL == 0) {
                updateSpectrumBands();
                broadcast(serverLevel);
            }
        }
    }

    private void updateSpectrumBands() {
        RadioAudioStream stream = audioStream;
        if (stream == null) {
            Arrays.fill(spectrumBands, 0);
            return;
        }
        int needed = SpectrumAnalyzer.WINDOW_SAMPLES / AudioDecoder.SAMPLES_PER_FRAME;
        byte[] window = new byte[SpectrumAnalyzer.WINDOW_SAMPLES];
        for (int i = 0; i < needed; i++) {
            byte[] frame = stream.getFrame(frameIndex - needed + i);
            if (frame == null) {
                Arrays.fill(spectrumBands, 0);
                return;
            }
            System.arraycopy(frame, 0, window, i * AudioDecoder.SAMPLES_PER_FRAME, frame.length);
        }
        float[] bands = SpectrumAnalyzer.bands(window, AudioDecoder.SAMPLE_RATE, spectrumBands.length);
        for (int i = 0; i < bands.length; i++) {
            spectrumBands[i] = Math.round(bands[i] * 255.0F);
        }
    }

    private void advanceOrStop() {
        if (playMode == PLAY_MODE_LOOP) {
            if (currentSlot >= 0 && startSlot(currentSlot, null)) {
                return;
            }
            stopPlayback();
            return;
        }
        if (carousel) {
            advanceCarousel();
            return;
        }
        if (playMode == PLAY_MODE_SHUFFLE) {
            int next = randomOtherSlot(currentSlot);
            if (next >= 0 && startSlot(next, null)) {
                return;
            }
            stopPlayback();
            return;
        }
        int next = reverse ? previousFilledSlot(currentSlot) : nextFilledSlot(currentSlot);
        if (next >= 0 && startSlot(next, null)) {
            return;
        }
        stopPlayback();
    }

    public int inputSlot() {
        return reverse ? SLOT_COUNT - 1 : 0;
    }

    public int outputSlot() {
        return reverse ? 0 : SLOT_COUNT - 1;
    }

    /**
     * Carousel advance. Sequential: the finished disc (at the input slot) waits for a
     * free output slot, then the whole queue shifts toward the output and the new
     * input disc plays. Shuffle: the finished disc (a middle slot) is moved straight
     * to the output, the input disc takes its place, and a random middle disc plays.
     */
    private void advanceCarousel() {
        if (playMode == PLAY_MODE_SHUFFLE) {
            advanceCarouselShuffle();
            return;
        }
        if (!getCd(outputSlot()).isEmpty()) {
            stopToWaitForOutput();
            return;
        }
        shiftTowardOutput();
        if (!getCd(inputSlot()).isEmpty()) {
            startSlot(inputSlot(), null);
        } else {
            stopPlayback();
        }
    }

    private void advanceCarouselShuffle() {
        // In shuffle carousel the playing disc sits in a middle slot; the loop mode
        // never reaches here, and a finished input disc only exists right after a
        // mode switch - advance it sequentially, then let shuffle take over.
        if (currentSlot < 0 || currentSlot == inputSlot() || currentSlot == outputSlot()) {
            if (!getCd(outputSlot()).isEmpty()) {
                stopToWaitForOutput();
                return;
            }
            shiftTowardOutput();
            if (!startCarousel(null)) {
                stopPlayback();
            }
            return;
        }
        if (!getCd(outputSlot()).isEmpty()) {
            // Blocked: keep the finished disc parked at its slot until the output clears.
            stopToWaitForOutput();
            return;
        }
        moveFinishedToOutputAndPullInput(currentSlot);
        if (!startCarousel(null)) {
            stopPlayback();
        }
    }

    private void moveFinishedToOutputAndPullInput(int finished) {
        shifting = true;
        try {
            inventory.setStackInSlot(outputSlot(), getCd(finished));
            inventory.setStackInSlot(finished, ItemStack.EMPTY);
            if (!getCd(inputSlot()).isEmpty()) {
                inventory.setStackInSlot(finished, getCd(inputSlot()));
                inventory.setStackInSlot(inputSlot(), ItemStack.EMPTY);
            }
        } finally {
            shifting = false;
        }
    }

    private void shiftTowardOutput() {
        shifting = true;
        try {
            if (!reverse) {
                for (int i = SLOT_COUNT - 2; i >= 0; i--) {
                    moveIfFilled(i, i + 1);
                }
            } else {
                for (int i = 1; i < SLOT_COUNT; i++) {
                    moveIfFilled(i, i - 1);
                }
            }
        } finally {
            shifting = false;
        }
    }

    private void moveIfFilled(int from, int to) {
        ItemStack stack = inventory.getStackInSlot(from);
        if (stack.isEmpty() || !inventory.getStackInSlot(to).isEmpty()) {
            return;
        }
        inventory.setStackInSlot(to, stack);
        inventory.setStackInSlot(from, ItemStack.EMPTY);
    }

    /** A random filled slot other than the playing one; -1 when there is no candidate. */
    private int randomOtherSlot(int exclude) {
        if (level == null) {
            return -1;
        }
        int found = -1;
        int count = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (i == exclude || getCd(i).isEmpty()) {
                continue;
            }
            count++;
            if (level.random.nextInt(count) == 0) {
                found = i;
            }
        }
        return found;
    }

    /** A random filled slot excluding the input and output slots; -1 when empty. */
    private int randomMiddleSlot() {
        if (level == null) {
            return -1;
        }
        int found = -1;
        int count = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (i == inputSlot() || i == outputSlot() || getCd(i).isEmpty()) {
                continue;
            }
            count++;
            if (level.random.nextInt(count) == 0) {
                found = i;
            }
        }
        return found;
    }

    /**
     * Starts carousel playback: shuffle plays a random middle disc (pulling the
     * input disc into the first middle slot to bootstrap an empty pool),
     * sequential plays whatever sits at the input slot.
     */
    private boolean startCarousel(@Nullable Player player) {
        if (carouselWaiting) {
            return false;
        }
        if (playMode == PLAY_MODE_SHUFFLE) {
            int candidate = randomMiddleSlot();
            if (candidate < 0) {
                if (getCd(inputSlot()).isEmpty()) {
                    return false;
                }
                candidate = pullInputToFirstMiddle();
                if (candidate < 0) {
                    return false;
                }
            }
            return startSlot(candidate, player);
        }
        if (getCd(inputSlot()).isEmpty()) {
            return false;
        }
        return startSlot(inputSlot(), player);
    }

    private int pullInputToFirstMiddle() {
        int firstMiddle = reverse ? SLOT_COUNT - 2 : 1;
        if (!getCd(firstMiddle).isEmpty()) {
            return -1;
        }
        shifting = true;
        try {
            inventory.setStackInSlot(firstMiddle, getCd(inputSlot()));
            inventory.setStackInSlot(inputSlot(), ItemStack.EMPTY);
        } finally {
            shifting = false;
        }
        return firstMiddle;
    }

    public void toggleCarousel(Player player) {
        carousel = !carousel;
        if (!carousel) {
            carouselWaiting = false;
        }
        stateChanged();
        notify(player, "message.netmusic_airwaves.mode_toggled", Component.translatable(carousel
                ? "gui.netmusic_airwaves.mode.carousel"
                : "gui.netmusic_airwaves.mode.normal"));
    }

    public boolean insertCd(ItemStack held, Player player) {
        int slot = carousel ? inputSlot() : firstEmptySlot();
        if (slot < 0) {
            notify(player, "message.netmusic_airwaves.playlist_full");
            return false;
        }
        if (!getCd(slot).isEmpty()) {
            notify(player, "message.netmusic_airwaves.slot_occupied");
            return false;
        }
        return insertHeldCd(slot, held, player);
    }
    public boolean insertHeldCd(int slot, ItemStack held, Player player) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return false;
        }
        if (!getCd(slot).isEmpty()) {
            notify(player, "message.netmusic_airwaves.slot_occupied");
            return false;
        }
        ItemMusicCD.SongInfo info = ItemMusicCD.getSongInfo(held);
        if (info == null) {
            notify(player, "message.netmusic_airwaves.not_a_cd");
            return false;
        }
        if (info.vip && !MusicPlayResolverManager.canResolve(info)) {
            notify(player, "message.netmusic_airwaves.need_vip");
            return false;
        }

        inventory.setStackInSlot(slot, held.copyWithCount(1));
        if (!player.isCreative()) {
            held.shrink(1);
        }
        if (state == DeckSnapshot.STATE_STOPPED) {
            startSlot(slot, player);
        } else {
            stateChanged();
        }
        return true;
    }

    public boolean hasAnyDisc() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Starts playback from the current or first disc; used by the deck menu
     * when the first CD is placed while the deck is stopped.
     */
    public void tryAutoStart(@Nullable Player player) {
        if (state == DeckSnapshot.STATE_STOPPED) {
            startCurrentOrFirst(player);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.netmusic_airwaves.spectrum_deck");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new com.netmusicairwaves.menu.SpectrumDeckMenu(containerId, playerInventory, worldPosition, this);
    }

    /**
     * GUI-less decks hand a disc back on empty-hand use: in carousel mode the
     * output slot first (the extraction point), otherwise the playing disc, then
     * the first disc in the playlist.
     */
    public void ejectCurrentOrFirst(Player player) {
        int slot = -1;
        if (carousel && !getCd(outputSlot()).isEmpty()) {
            slot = outputSlot();
        } else if (currentSlot >= 0 && !getCd(currentSlot).isEmpty()) {
            slot = currentSlot;
        } else if (carousel && !getCd(inputSlot()).isEmpty()) {
            slot = inputSlot();
        } else {
            slot = nextFilledSlot(-1);
        }
        if (slot >= 0) {
            ejectSlot(slot, player);
        }
    }

    public void ejectSlot(int slot, Player player) {
        if (slot < 0 || slot >= SLOT_COUNT || getCd(slot).isEmpty()) {
            return;
        }
        if (currentSlot == slot && state != DeckSnapshot.STATE_STOPPED) {
            stopPlayback();
        }
        ItemStack ejected = getCd(slot).copy();
        shifting = true;
        try {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        } finally {
            shifting = false;
        }
        if (currentSlot == slot) {
            currentSlot = -1;
        }
        if (!player.getInventory().add(ejected)) {
            player.drop(ejected, false);
        }
        stateChanged();
    }

    public void handleAction(int action, int index, ServerPlayer player) {
        switch (action) {
            case DeckActionPayload.ACTION_PAUSE -> {
                if (state == DeckSnapshot.STATE_PLAYING) {
                    state = DeckSnapshot.STATE_PAUSED;
                    positioned = false;
                    Arrays.fill(spectrumBands, 0);
                    setOnAir(false);
                    stateChanged();
                }
            }
            case DeckActionPayload.ACTION_PLAY -> {
                if (state == DeckSnapshot.STATE_PAUSED) {
                    state = DeckSnapshot.STATE_PLAYING;
                    positioned = false;
                    stateChanged();
                } else if (state == DeckSnapshot.STATE_STOPPED) {
                    startCurrentOrFirst(player);
                }
            }
            case DeckActionPayload.ACTION_STOP -> stopPlayback();
            case DeckActionPayload.ACTION_NEXT -> skipToNext();
            case DeckActionPayload.ACTION_SELECT -> {
                if (index >= 0 && index < SLOT_COUNT && !getCd(index).isEmpty()) {
                    startSlot(index, null);
                }
            }
            case DeckActionPayload.ACTION_EJECT -> ejectSlot(index, player);
            case DeckActionPayload.ACTION_SET_MODE -> setCarousel(index == 1);
            case DeckActionPayload.ACTION_SET_DIRECTION -> setReverse(index == 1);
            case DeckActionPayload.ACTION_SET_PLAY_MODE ->
                    setPlayMode(Math.max(PLAY_MODE_SEQUENTIAL, Math.min(PLAY_MODE_LOOP, index)));
            default -> {
            }
        }
    }

    public int getPlayMode() {
        return playMode;
    }

    public void setPlayMode(int value) {
        int clamped = Math.max(PLAY_MODE_SEQUENTIAL, Math.min(PLAY_MODE_LOOP, value));
        if (playMode == clamped) {
            return;
        }
        playMode = clamped;
        stateChanged();
    }

    /**
     * The next-track button: switches directly to the next disc using the active
     * mode logic - carousel runs its shift flow, shuffle picks a random other
     * disc, and sequential/loop cycles to the next filled slot in play order
     * (wrapping around; the loop mode does not replay the current disc here).
     */
    public void skipToNext() {
        if (state == DeckSnapshot.STATE_STOPPED) {
            return;
        }
        if (carousel) {
            advanceCarousel();
            return;
        }
        int next = playMode == PLAY_MODE_SHUFFLE ? randomOtherSlot(currentSlot)
                : reverse ? previousFilledSlot(currentSlot) : nextFilledSlot(currentSlot);
        if (next < 0) {
            next = reverse ? previousFilledSlot(SLOT_COUNT) : nextFilledSlot(-1);
        }
        if (next >= 0 && startSlot(next, null)) {
            return;
        }
        stopPlayback();
    }

    public void setCarousel(boolean value) {
        if (carousel == value) {
            return;
        }
        carousel = value;
        if (!carousel) {
            carouselWaiting = false;
        }
        stateChanged();
    }

    public void setReverse(boolean value) {
        if (reverse == value) {
            return;
        }
        reverse = value;
        stateChanged();
    }

    public void onRedstoneChanged(boolean signal) {
        if (signal == hasSignal) {
            return;
        }
        hasSignal = signal;
        setChanged();
        if (signal) {
            if (state != DeckSnapshot.STATE_STOPPED) {
                stopPlayback();
            } else {
                startCurrentOrFirst(null);
            }
        }
    }

    private boolean startCurrentOrFirst(@Nullable Player player) {
        if (carousel) {
            return startCarousel(player);
        }
        if (currentSlot >= 0 && !getCd(currentSlot).isEmpty()) {
            return startSlot(currentSlot, player);
        }
        int first = reverse ? previousFilledSlot(SLOT_COUNT) : nextFilledSlot(-1);
        return first >= 0 && startSlot(first, player);
    }

    private boolean startSlot(int slot, @Nullable Player player) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return false;
        }
        ItemMusicCD.SongInfo info = ItemMusicCD.getSongInfo(getCd(slot));
        if (info == null) {
            return false;
        }
        if (info.vip && !MusicPlayResolverManager.canResolve(info)) {
            if (player != null) {
                notify(player, "message.netmusic_airwaves.need_vip");
            }
            return false;
        }

        closeAudioStream();
        currentSlot = slot;
        state = DeckSnapshot.STATE_PLAYING;
        onAir = false;
        positioned = false;
        failureAnnounced = false;
        playTicks = 0;
        totalTicks = Math.max(20, info.songTime * 20 + 64);
        frameIndex = 0;
        Arrays.fill(spectrumBands, 0);
        audioStream = new RadioAudioStream(info.clone());
        audioStream.start();
        stateChanged();
        return true;
    }

    private void stopPlayback() {
        if (state == DeckSnapshot.STATE_STOPPED && audioStream == null) {
            return;
        }
        state = DeckSnapshot.STATE_STOPPED;
        onAir = false;
        positioned = false;
        failureAnnounced = false;
        playTicks = 0;
        totalTicks = 0;
        frameIndex = 0;
        carouselWaiting = false;
        Arrays.fill(spectrumBands, 0);
        closeAudioStream();
        stateChanged();
    }

    private void stopToWaitForOutput() {
        state = DeckSnapshot.STATE_STOPPED;
        onAir = false;
        positioned = false;
        playTicks = 0;
        totalTicks = 0;
        frameIndex = 0;
        Arrays.fill(spectrumBands, 0);
        closeAudioStream();
        carouselWaiting = true;
        stateChanged();
    }

    private void closeAudioStream() {
        if (audioStream != null) {
            audioStream.close();
            audioStream = null;
        }
    }

    private void refreshConnection(ServerLevel serverLevel) {
        List<Cables.Hit<TransmitterBE>> hits = Cables.reach(serverLevel, worldPosition, TransmitterBE.class);
        hasConnectedTransmitter = !hits.isEmpty();

        Cables.Hit<TransmitterBE> best = null;
        float bestQuality = -1.0F;
        for (Cables.Hit<TransmitterBE> hit : hits) {
            if (!hit.target().active) {
                continue;
            }
            float quality = hit.lineQuality(serverLevel);
            if (quality > bestQuality) {
                best = hit;
                bestQuality = quality;
            }
        }

        Connection next = null;
        if (best != null) {
            next = new Connection(
                    best.target().getBlockPos(),
                    Math.max(0.05F, Math.min(1.0F, bestQuality)),
                    best.target().stationLabel(),
                    best.target().tuning.display()
            );
        }
        if (!Objects.equals(next, connection)) {
            connection = next;
            positioned = false;
            setOnAir(false);
            broadcast(serverLevel);
            notifyDisplay();
        }
    }

    private void lightTransmitter(ServerLevel serverLevel) {
        if (connection == null) {
            return;
        }
        if (serverLevel.getBlockEntity(connection.transmitterPos) instanceof TransmitterBE transmitter
                && transmitter.active) {
            transmitter.onAir(serverLevel);
        }
    }

    private void setOnAir(boolean value) {
        if (onAir == value) {
            return;
        }
        onAir = value;
        broadcastIfServer();
        notifyDisplay();
    }

    private void broadcastIfServer() {
        if (level instanceof ServerLevel serverLevel) {
            broadcast(serverLevel);
        }
    }

    private void broadcast(ServerLevel serverLevel) {
        DeckNetwork.broadcastSnapshot(serverLevel, snapshot());
    }

    public DeckSnapshot snapshot() {
        List<DeckSnapshot.TrackInfo> tracks = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemMusicCD.SongInfo info = ItemMusicCD.getSongInfo(getCd(i));
            if (info == null) {
                tracks.add(DeckSnapshot.TrackInfo.EMPTY);
                continue;
            }
            String artists = info.artists == null ? "" : String.join(" / ", info.artists);
            tracks.add(new DeckSnapshot.TrackInfo(true,
                    info.songName == null ? "" : info.songName,
                    artists,
                    info.songTime,
                    info.songUrl == null ? "" : info.songUrl));
        }

        ItemMusicCD.SongInfo current = getCurrentInfo();
        int duration = current == null ? 0 : current.songTime;
        int elapsed = Math.min(duration, Math.max(0, playTicks / 20));
        int flags = 0;
        if (!links.isEmpty()) {
            flags |= DeckSnapshot.FLAG_CABLE;
        }
        if (hasConnectedTransmitter) {
            flags |= DeckSnapshot.FLAG_TRANSMITTER;
        }
        if (onAir) {
            flags |= DeckSnapshot.FLAG_ON_AIR;
        }
        if (carousel) {
            flags |= DeckSnapshot.FLAG_CAROUSEL;
        }
        if (reverse) {
            flags |= DeckSnapshot.FLAG_REVERSE;
        }
        if (playMode == PLAY_MODE_SHUFFLE) {
            flags |= DeckSnapshot.FLAG_SHUFFLE;
        } else if (playMode == PLAY_MODE_LOOP) {
            flags |= DeckSnapshot.FLAG_LOOP;
        }
        return new DeckSnapshot(
                worldPosition,
                state,
                currentSlot,
                elapsed,
                duration,
                flags,
                connection == null ? 0 : Math.round(connection.lineQuality * 100.0F),
                connection == null ? "" : stationLabel(connection),
                connection == null ? "" : connection.tuning,
                tracks,
                spectrumBands.clone()
        );
    }

    @Nullable
    private ItemMusicCD.SongInfo getCurrentInfo() {
        return currentSlot >= 0 && currentSlot < SLOT_COUNT
                ? ItemMusicCD.getSongInfo(getCd(currentSlot))
                : null;
    }

    private int firstEmptySlot() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private int nextFilledSlot(int after) {
        for (int i = after + 1; i < SLOT_COUNT; i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private int previousFilledSlot(int from) {
        for (int i = (from < 0 ? SLOT_COUNT : from) - 1; i >= 0; i--) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }
        for (int i = SLOT_COUNT - 1; i > from; i--) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public List<MutableComponent> getDisplayText() {
        List<MutableComponent> lines = new ArrayList<>();
        ItemMusicCD.SongInfo info = getCurrentInfo();
        if (info == null) {
            lines.add(Component.translatable("display.netmusic_airwaves.no_cd"));
            lines.add(Component.translatable("display.netmusic_airwaves.no_artists"));
            lines.add(Component.translatable("display.netmusic_airwaves.state.no_cd"));
            lines.add(Component.literal("--:--"));
            return lines;
        }

        lines.add(Component.literal(info.songName == null ? "" : info.songName));
        lines.add(info.artists == null || info.artists.isEmpty()
                ? Component.translatable("display.netmusic_airwaves.no_artists")
                : Component.literal(String.join(" / ", info.artists)));

        MutableComponent stateText;
        if (state == DeckSnapshot.STATE_PAUSED) {
            stateText = Component.translatable("display.netmusic_airwaves.state.paused");
        } else if (state == DeckSnapshot.STATE_STOPPED) {
            stateText = Component.translatable("display.netmusic_airwaves.state.stopped");
        } else if (connection == null) {
            if (!hasConnectedTransmitter) {
                stateText = Component.translatable(links.isEmpty()
                        ? "display.netmusic_airwaves.state.no_cable"
                        : "display.netmusic_airwaves.state.no_transmitter");
            } else {
                stateText = Component.translatable("display.netmusic_airwaves.state.tx_off");
            }
        } else if (audioStream == null || !audioStream.isReady() || !onAir) {
            stateText = Component.translatable("display.netmusic_airwaves.state.preparing");
        } else {
            stateText = Component.translatable("display.netmusic_airwaves.state.on_air")
                    .append(Component.literal(" · " + stationLabel(connection) + " · " + connection.tuning));
        }
        lines.add(stateText);

        int elapsed = Math.min(info.songTime, Math.max(0, playTicks / 20));
        MutableComponent progress = Component.literal(formatTime(elapsed) + " / " + formatTime(info.songTime));
        if (connection != null) {
            progress.append(Component.literal(" · "))
                    .append(Component.translatable("display.netmusic_airwaves.cable",
                            Math.round(connection.lineQuality * 100.0F)));
        }
        lines.add(progress);
        return lines;
    }

    private static String formatTime(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format("%02d:%02d", safe / 60, safe % 60);
    }

    private static String stationLabel(Connection connection) {
        return connection.stationName.isBlank()
                ? Component.translatable("message.netmusic_airwaves.unnamed_station").getString()
                : connection.stationName;
    }

    private UUID getSourceId() {
        return UUID.nameUUIDFromBytes(("netmusic_airwaves:" + worldPosition.asLong())
                .getBytes(StandardCharsets.UTF_8));
    }

    private void announceNearby(String key, Object... args) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Component message = Component.translatable(key, args).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 16 * 16) {
                player.displayClientMessage(message, false);
            }
        }
    }

    private void notify(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.GOLD), false);
    }

    private static String shorten(@Nullable String error) {
        if (error == null || error.isBlank()) {
            return "unknown error";
        }
        return error.length() <= 160 ? error : error.substring(0, 160) + "...";
    }

    private void notifyDisplay() {
        if (level instanceof ServerLevel serverLevel) {
            DisplayLinkBlock.notifyGatherers(serverLevel, worldPosition);
        }
    }

    private void stateChanged() {
        updateBlockState();
        sync();
        notifyDisplay();
        broadcastIfServer();
    }

    /**
     * Mirrors playback state and disc count into blockstate properties so the
     * models can react (play_state on both decks, fill on the playlist player).
     */
    private void updateBlockState() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState current = getBlockState();
        BlockState target = current;
        if (current.hasProperty(RadioModulatorBlock.PLAY_STATE)) {
            RadioModulatorBlock.PlayState playState = hasAnyDisc()
                    ? switch (state) {
                        case DeckSnapshot.STATE_PLAYING -> RadioModulatorBlock.PlayState.PLAYING;
                        case DeckSnapshot.STATE_PAUSED -> RadioModulatorBlock.PlayState.PAUSED;
                        default -> RadioModulatorBlock.PlayState.IDLE;
                    }
                    : RadioModulatorBlock.PlayState.NONE;
            target = target.setValue(RadioModulatorBlock.PLAY_STATE, playState);
        }
        if (current.hasProperty(SpectrumMonitorBlock.FILL)) {
            int count = 0;
            for (int i = 0; i < SLOT_COUNT; i++) {
                if (!inventory.getStackInSlot(i).isEmpty()) {
                    count++;
                }
            }
            SpectrumMonitorBlock.FillLevel fill = count == 0 ? SpectrumMonitorBlock.FillLevel.EMPTY
                    : count <= 4 ? SpectrumMonitorBlock.FillLevel.QUARTER
                    : count <= 8 ? SpectrumMonitorBlock.FillLevel.HALF
                    : count <= 12 ? SpectrumMonitorBlock.FillLevel.THREE_QUARTERS
                    : SpectrumMonitorBlock.FillLevel.FULL;
            target = target.setValue(SpectrumMonitorBlock.FILL, fill);
        }
        if (target != current) {
            level.setBlock(worldPosition, target, Block.UPDATE_CLIENTS);
        }
    }

    private void sync() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    public void onBlockRemoved() {
        closeAudioStream();
        state = DeckSnapshot.STATE_STOPPED;
        onAir = false;
        hasConnectedTransmitter = false;
        connection = null;
        sync();
    }

    public static void stopAll() {
        for (RadioModulatorBlockEntity blockEntity : LOADED) {
            blockEntity.onBlockRemoved();
        }
        LOADED.clear();
    }

    @Override
    public LinkSet links() {
        return links;
    }

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public int maxLinks(int slot) {
        return 1;
    }

    @Override
    public boolean junction(int slot) {
        return true;
    }

    @Override
    public Vec3 anchor(int slot) {
        return new Vec3(worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D,
                worldPosition.getZ() + 0.5D);
    }

    @Override
    public void cablesChanged() {
        sync();
        notifyDisplay();
        broadcastIfServer();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        links.save(tag);
        tag.put(INVENTORY_TAG, inventory.serializeNBT(registries));
        tag.putBoolean("HasSignal", hasSignal);
        tag.putInt("CurrentSlot", currentSlot);
        tag.putBoolean("Carousel", carousel);
        tag.putBoolean("Reverse", reverse);
        tag.putInt("PlayMode", playMode);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        links.load(tag);
        loading = true;
        try {
            if (tag.contains(INVENTORY_TAG)) {
                inventory.deserializeNBT(registries, tag.getCompound(INVENTORY_TAG));
            }
        } finally {
            loading = false;
        }
        hasSignal = tag.getBoolean("HasSignal");
        currentSlot = tag.getInt("CurrentSlot");
        if (currentSlot < -1 || currentSlot >= SLOT_COUNT) {
            currentSlot = -1;
        }
        carousel = tag.getBoolean("Carousel");
        reverse = tag.getBoolean("Reverse");
        playMode = Math.max(PLAY_MODE_SEQUENTIAL, Math.min(PLAY_MODE_LOOP, tag.getInt("PlayMode")));
        state = DeckSnapshot.STATE_STOPPED;
        onAir = false;
        positioned = false;
        failureAnnounced = false;
        hasConnectedTransmitter = false;
        playTicks = 0;
        totalTicks = 0;
        frameIndex = 0;
        carouselWaiting = false;
        shifting = false;
        audioStream = null;
        connection = null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private record Connection(BlockPos transmitterPos, float lineQuality, String stationName, String tuning) {
    }
}
