package com.netmusicairwaves.client;

import com.github.tartaricacid.netmusic.NetMusic;
import com.github.tartaricacid.netmusic.api.lyric.LyricParser;
import com.github.tartaricacid.netmusic.api.lyric.LyricRecord;
import com.netmusicairwaves.menu.DeckMenu;
import com.netmusicairwaves.menu.SpectrumDeckMenu;
import com.netmusicairwaves.network.DeckActionPayload;
import com.netmusicairwaves.network.DeckNetwork;
import com.netmusicairwaves.network.DeckSnapshot;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The playlist disc player GUI: the full deck feature set (16 CD slots, player
 * inventory, transport, lyrics) with a live spectrum panel, styled after the
 * Create stock-ticker GUI (copper body, cream inset panels, red-brown accents).
 */
public final class SpectrumDeckScreen extends AbstractContainerScreen<SpectrumDeckMenu> {
    private static final int PAGE_SIZE = DeckMenu.PAGE_SIZE;
    private static final int PAGES = DeckMenu.PAGES;
    private static final Pattern SONG_ID = Pattern.compile("^.*?\\?id=(\\d+)\\.mp3$");
    private static final int BARS = 26;

    // Create stock-ticker palette.
    private static final int BODY = 0xFF9C6A52;
    private static final int BORDER = 0xFF2B1D16;
    private static final int BEVEL = 0xFFD8CFC0;
    private static final int PANEL = 0xFFE9DFC8;
    private static final int PANEL_ALT = 0xFFDFD2B6;
    private static final int PANEL_HOVER = 0xFFF0E8D3;
    private static final int INNER = 0xFF5A4432;
    private static final int TEXT = 0xFF3B2A1C;
    private static final int MUTED = 0xFF8A7862;
    private static final int BODY_TEXT = 0xFFF0E6D2;
    private static final int ACCENT = 0xFFA5502E;
    private static final int ACCENT_BRIGHT = 0xFFC26A3F;
    private static final int GREEN = 0xFF5F8F52;
    private static final int AMBER = 0xFFB07A2E;
    private static final int GRID = 0xFFC9BBA2;
    private static final int BAR_IDLE = 0xFFB09B82;
    private static final int BAR_ACTIVE = 0xFFA5502E;
    private static final int CAP = 0xFF3B2A1C;
    private static final int WASH_CURRENT = 0xFFD9B08A;
    private static final int WASH_INPUT = 0xFFC9D2B2;
    private static final int WASH_OUTPUT = 0xFFD9AE8C;
    private static final int SLOT_BG = 0xFFB3A486;
    private static final int DISABLED = 0xFFCFC4AC;

    private final BlockPos deckPos;
    @Nullable
    private DeckSnapshot snapshot;
    private long snapshotAt;
    private int requestCooldown;
    private int lastCurrentSlot = -2;
    private String lyricUrl = "";
    @Nullable
    private LyricRecord lyricRecord;
    private final SpectrumAnimation spectrum = new SpectrumAnimation();

    public SpectrumDeckScreen(SpectrumDeckMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.deckPos = menu.getPos();
        this.imageWidth = 340;
        this.imageHeight = 336;
        this.inventoryLabelY = -100;
        this.titleLabelY = -100;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        DeckNetwork.requestSnapshot(deckPos);
        requestCooldown = 100;
    }

    @Override
    protected void containerTick() {
        if (--requestCooldown <= 0) {
            DeckNetwork.requestSnapshot(deckPos);
            requestCooldown = 100;
        }
        DeckSnapshot latest = DeckClientData.get(deckPos);
        if (latest != null && latest != snapshot) {
            if (latest.currentSlot() != lastCurrentSlot) {
                if (latest.currentSlot() >= 0) {
                    setPage(Mth.clamp(latest.currentSlot() / PAGE_SIZE, 0, PAGES - 1));
                }
                lastCurrentSlot = latest.currentSlot();
            }
            snapshot = latest;
            snapshotAt = Util.getMillis();
        }
        ensureLyrics();
    }

    private void setPage(int page) {
        int clamped = Mth.clamp(page, 0, PAGES - 1);
        if (clamped != getMenu().getPage()) {
            getMenu().setPage(clamped);
            DeckNetwork.sendAction(deckPos, DeckActionPayload.ACTION_SET_PAGE, clamped);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x + 3, y + 4, x + imageWidth + 3, y + imageHeight + 4, 0x66000000);
        graphics.fill(x, y, x + imageWidth, y + imageHeight, BODY);
        frame(graphics, x, y, imageWidth, imageHeight, BORDER);
        frame(graphics, x + 1, y + 1, imageWidth - 2, imageHeight - 2, BEVEL);

        drawPlaylistPanel(graphics, x, y, mouseX, mouseY);
        drawSpectrumPanel(graphics, x, y);
        drawNowPlaying(graphics, x, y);
        drawSettingsRow(graphics, x, y, mouseX, mouseY);
        drawStationRow(graphics, x, y);
        drawTransportButtons(graphics, x, y, mouseX, mouseY);
        drawInventoryPanel(graphics, x, y);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = leftPos;
        int y = topPos;
        if (inside(mouseX, mouseY, x + imageWidth - 76, y + 6, 16, 16)) {
            setPage(getMenu().getPage() - 1);
            return true;
        }
        if (inside(mouseX, mouseY, x + imageWidth - 26, y + 6, 16, 16)) {
            setPage(getMenu().getPage() + 1);
            return true;
        }
        for (int row = 0; row < PAGE_SIZE; row++) {
            int global = getMenu().getPage() * PAGE_SIZE + row;
            int rowY = y + 23 + row * 26;
            if (inside(mouseX, mouseY, x + 76, rowY, imageWidth - 88, 24)) {
                return handleTrackClick(global, button);
            }
        }
        if (handleToggleClick(mouseX, mouseY)) {
            return true;
        }
        int buttonY = y + 226;
        int[] transport = {DeckActionPayload.ACTION_NEXT, DeckActionPayload.ACTION_PAUSE,
                DeckActionPayload.ACTION_PLAY, DeckActionPayload.ACTION_STOP};
        for (int i = 0; i < transport.length; i++) {
            int buttonX = x + 258 + i * 18;
            if (inside(mouseX, mouseY, buttonX, buttonY, 16, 16)) {
                sendAction(transport[i], 0);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Settings row: three Create-style labeled toggles (mode / order / play mode). */
    private boolean handleToggleClick(double mouseX, double mouseY) {
        int x = leftPos;
        int y = topPos + 208;
        boolean carousel = snapshot != null && (snapshot.flags() & DeckSnapshot.FLAG_CAROUSEL) != 0;
        boolean reverse = snapshot != null && (snapshot.flags() & DeckSnapshot.FLAG_REVERSE) != 0;
        if (inside(mouseX, mouseY, x + 12, y, 98, 14)) {
            sendAction(DeckActionPayload.ACTION_SET_MODE, carousel ? 0 : 1);
            return true;
        }
        if (inside(mouseX, mouseY, x + 114, y, 98, 14)) {
            sendAction(DeckActionPayload.ACTION_SET_DIRECTION, reverse ? 0 : 1);
            return true;
        }
        if (inside(mouseX, mouseY, x + 216, y, 98, 14)) {
            sendAction(DeckActionPayload.ACTION_SET_PLAY_MODE, (currentPlayMode() + 1) % 3);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, leftPos + 8, topPos + 8, imageWidth - 16, 122) && scrollY != 0.0D) {
            setPage(getMenu().getPage() - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        if (inside(mouseX, mouseY, x + imageWidth - 76, y + 6, 16, 16)) {
            graphics.renderTooltip(font, Component.translatable("gui.netmusic_airwaves.previous_page"), mouseX, mouseY);
            return;
        }
        if (inside(mouseX, mouseY, x + imageWidth - 26, y + 6, 16, 16)) {
            graphics.renderTooltip(font, Component.translatable("gui.netmusic_airwaves.next_page"), mouseX, mouseY);
            return;
        }
        for (int row = 0; row < PAGE_SIZE; row++) {
            int global = getMenu().getPage() * PAGE_SIZE + row;
            int cardY = y + 23 + row * 26;
            if (inside(mouseX, mouseY, x + 76, cardY, imageWidth - 88, 24)) {
                DeckSnapshot.TrackInfo track = track(global);
                if (track != null && track.filled()) {
                    graphics.renderTooltip(font, List.of(
                            Component.literal(track.name()).getVisualOrderText(),
                            Component.literal(track.artists()).getVisualOrderText(),
                            Component.translatable("gui.netmusic_airwaves.track_hint").getVisualOrderText()
                    ), mouseX, mouseY);
                }
                return;
            }
        }
        int settingsY = y + 208;
        if (inside(mouseX, mouseY, x + 12, settingsY, 98, 14)) {
            graphics.renderTooltip(font, Component.translatable("gui.netmusic_airwaves.mode.hint"), mouseX, mouseY);
            return;
        }
        if (inside(mouseX, mouseY, x + 114, settingsY, 98, 14)) {
            graphics.renderTooltip(font, Component.translatable("gui.netmusic_airwaves.direction.hint"), mouseX, mouseY);
            return;
        }
        if (inside(mouseX, mouseY, x + 216, settingsY, 98, 14)) {
            int mode = currentPlayMode();
            graphics.renderTooltip(font, List.of(
                    Component.translatable(switch (mode) {
                        case 1 -> "gui.netmusic_airwaves.play_mode.shuffle";
                        case 2 -> "gui.netmusic_airwaves.play_mode.loop";
                        default -> "gui.netmusic_airwaves.play_mode.sequential";
                    }).getVisualOrderText(),
                    Component.translatable("gui.netmusic_airwaves.play_mode.hint").getVisualOrderText()
            ), mouseX, mouseY);
            return;
        }
        int buttonY = y + 226;
        String[] keys = {"next", "pause", "play", "stop"};
        for (int i = 0; i < keys.length; i++) {
            if (inside(mouseX, mouseY, x + 258 + i * 18, buttonY, 16, 16)) {
                graphics.renderTooltip(font, Component.translatable("gui.netmusic_airwaves." + keys[i]), mouseX, mouseY);
                return;
            }
        }
        Slot hovered = this.hoveredSlot;
        if (hovered != null && hovered.index < DeckSnapshot.SLOT_COUNT && !hovered.hasItem()) {
            boolean carousel = snapshot != null && (snapshot.flags() & DeckSnapshot.FLAG_CAROUSEL) != 0;
            boolean reverse = snapshot != null && (snapshot.flags() & DeckSnapshot.FLAG_REVERSE) != 0;
            int inputSlot = reverse ? DeckSnapshot.SLOT_COUNT - 1 : 0;
            int outputSlot = reverse ? 0 : DeckSnapshot.SLOT_COUNT - 1;
            if (carousel && hovered.index == inputSlot) {
                graphics.renderTooltip(font, Component.translatable("gui.netmusic_airwaves.slot_input_hint"), mouseX, mouseY);
            } else if (carousel && hovered.index == outputSlot) {
                graphics.renderTooltip(font, Component.translatable("gui.netmusic_airwaves.slot_output_hint"), mouseX, mouseY);
            } else {
                graphics.renderTooltip(font, Component.translatable("gui.netmusic_airwaves.drop_hint"), mouseX, mouseY);
            }
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    private int currentPlayMode() {
        if (snapshot == null) {
            return 0;
        }
        int flags = snapshot.flags();
        if ((flags & DeckSnapshot.FLAG_LOOP) != 0) {
            return 2;
        }
        return (flags & DeckSnapshot.FLAG_SHUFFLE) != 0 ? 1 : 0;
    }

    private boolean handleTrackClick(int index, int button) {
        DeckSnapshot.TrackInfo track = track(index);
        if (track == null || !track.filled()) {
            return true;
        }
        if (button == 1 || (button == 0 && hasShiftDown())) {
            sendAction(DeckActionPayload.ACTION_EJECT, index);
        } else if (button == 0) {
            sendAction(DeckActionPayload.ACTION_SELECT, index);
        }
        return true;
    }

    private void sendAction(int action, int index) {
        DeckNetwork.sendAction(deckPos, action, index);
    }

    private void drawPlaylistPanel(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        graphics.fill(x + 8, y + 8, x + imageWidth - 8, y + 130, PANEL);
        panelFrame(graphics, x + 8, y + 8, imageWidth - 16, 122);

        graphics.drawString(font, Component.translatable("gui.netmusic_airwaves.playlist").getString(),
                x + 78, y + 11, MUTED, false);
        int prevX = x + imageWidth - 76;
        int nextX = x + imageWidth - 26;
        boolean prevHover = inside(mouseX, mouseY, prevX, y + 6, 16, 16);
        boolean nextHover = inside(mouseX, mouseY, nextX, y + 6, 16, 16);
        int page = getMenu().getPage();
        graphics.drawString(font, "<", prevX + 4, y + 11, page > 0 ? (prevHover ? ACCENT : TEXT) : GRID, false);
        graphics.drawString(font, ">", nextX + 4, y + 11, page < PAGES - 1 ? (nextHover ? ACCENT : TEXT) : GRID, false);
        graphics.drawString(font, (page + 1) + "/" + PAGES, x + imageWidth - 55, y + 11, MUTED, false);

        for (int row = 0; row < PAGE_SIZE; row++) {
            int global = page * PAGE_SIZE + row;
            drawDiscCard(graphics, x, y, global, row, mouseX, mouseY);
            drawTrackRow(graphics, x, y, global, row, mouseX, mouseY);
        }
    }

    private void drawDiscCard(GuiGraphics graphics, int x, int y, int globalIndex, int row, int mouseX, int mouseY) {
        int cardX = x + DeckMenu.CARD_X;
        int cardY = y + DeckMenu.CARD_Y + row * DeckMenu.ROW_HEIGHT;
        boolean current = snapshot != null && snapshot.currentSlot() == globalIndex;
        boolean hover = inside(mouseX, mouseY, cardX, cardY, 54, 24);
        boolean filled = track(globalIndex) != null && track(globalIndex).filled();
        boolean carousel = snapshot != null && (snapshot.flags() & DeckSnapshot.FLAG_CAROUSEL) != 0;
        boolean reverse = snapshot != null && (snapshot.flags() & DeckSnapshot.FLAG_REVERSE) != 0;
        int inputSlot = reverse ? DeckSnapshot.SLOT_COUNT - 1 : 0;
        int outputSlot = reverse ? 0 : DeckSnapshot.SLOT_COUNT - 1;
        boolean isInput = carousel && globalIndex == inputSlot;
        boolean isOutput = carousel && globalIndex == outputSlot;
        int bg = isInput ? WASH_INPUT : isOutput ? WASH_OUTPUT : current ? WASH_CURRENT
                : hover ? PANEL_HOVER : PANEL_ALT;

        graphics.fill(cardX, cardY, cardX + 54, cardY + 24, bg);
        frame(graphics, cardX, cardY, 54, 24,
                isInput ? GREEN : isOutput ? ACCENT : current ? ACCENT : INNER);
        drawSlotBackground(graphics, cardX + 3, cardY + 3);
        String tag = isInput ? Component.translatable("gui.netmusic_airwaves.slot_input").getString()
                : isOutput ? Component.translatable("gui.netmusic_airwaves.slot_output").getString()
                : Component.translatable("gui.netmusic_airwaves.disc").getString();
        graphics.drawString(font, tag, cardX + 25, cardY + 3, filled ? TEXT : MUTED, false);
        graphics.drawString(font, String.valueOf(globalIndex + 1), cardX + 25, cardY + 13, MUTED, false);
    }

    private void drawTrackRow(GuiGraphics graphics, int x, int y, int globalIndex, int row, int mouseX, int mouseY) {
        int rowX = x + 76;
        int rowY = y + 23 + row * 26;
        boolean current = snapshot != null && snapshot.currentSlot() == globalIndex;
        boolean hover = inside(mouseX, mouseY, rowX, rowY, imageWidth - 88, 24);
        DeckSnapshot.TrackInfo track = track(globalIndex);

        if (current) {
            graphics.fill(rowX, rowY, rowX + imageWidth - 88, rowY + 24, WASH_CURRENT);
            graphics.fill(rowX, rowY, rowX + 3, rowY + 24, ACCENT);
        } else if (hover) {
            graphics.fill(rowX, rowY, rowX + imageWidth - 88, rowY + 24, PANEL_HOVER);
        }
        frame(graphics, rowX, rowY, imageWidth - 88, 24, GRID);

        if (track == null || !track.filled()) {
            graphics.drawString(font, Component.translatable("gui.netmusic_airwaves.empty_slot").getString(),
                    rowX + 8, rowY + 8, MUTED, false);
            return;
        }

        String time = formatTime(track.seconds());
        int timeWidth = font.width(time);
        graphics.drawString(font, fit(track.name(), imageWidth - 112 - timeWidth), rowX + 8, rowY + 4, TEXT, false);
        graphics.drawString(font, time, rowX + imageWidth - 88 - timeWidth - 8, rowY + 4,
                current ? ACCENT : MUTED, false);
        graphics.drawString(font, fit(track.artists(), imageWidth - 100), rowX + 8, rowY + 14, MUTED, false);
    }

    private void drawSpectrumPanel(GuiGraphics graphics, int x, int y) {
        int panelX = x + 12;
        int panelY = y + 136;
        int panelW = 204;
        int panelH = 66;
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
        panelFrame(graphics, panelX, panelY, panelW, panelH);

        int statusColor = statusColor();
        graphics.fill(panelX + 8, panelY + 8, panelX + 12, panelY + 12, statusColor);
        graphics.drawString(font, statusText(), panelX + 16, panelY + 6, statusColor, false);
        String label = Component.translatable("gui.netmusic_airwaves.spectrum_label").getString();
        graphics.drawString(font, label, panelX + panelW - font.width(label) - 8, panelY + 6, MUTED, false);

        int innerX = panelX + 10;
        int innerY = panelY + 18;
        int innerW = panelW - 20;
        int innerH = 34;
        int baseline = innerY + innerH;

        graphics.fill(innerX, baseline, innerX + innerW, baseline + 1, GRID);
        graphics.fill(innerX, innerY + innerH / 2, innerX + innerW, innerY + innerH / 2 + 1, GRID);

        boolean playing = snapshot != null && snapshot.state() == DeckSnapshot.STATE_PLAYING
                && (snapshot.flags() & DeckSnapshot.FLAG_ON_AIR) != 0;
        boolean paused = snapshot != null && snapshot.state() == DeckSnapshot.STATE_PAUSED;
        float[] levels = spectrum.sample(BARS, snapshot);
        int barSlot = innerW / BARS;
        for (int i = 0; i < BARS; i++) {
            float level = levels[i];
            if (paused) {
                level = Math.min(level, 0.3F);
            } else if (!playing) {
                level = Math.min(level, 0.06F);
            }
            int h = Mth.clamp(Math.round(2 + level * (innerH - 4)), 2, innerH);
            int bx = innerX + i * barSlot + (barSlot - 4) / 2;
            int top = baseline - h;
            int color = playing ? BAR_ACTIVE : paused ? 0x88A5502E : BAR_IDLE;
            graphics.fill(bx, top, bx + 4, baseline, color);
            graphics.fill(bx, top, bx + 4, top + 1, playing ? CAP : 0x883B2A1C);
        }

        if (playing) {
            double phase = (Util.getMillis() % 5000L) / 5000.0;
            int scanX = innerX + (int) (innerW * phase);
            graphics.fill(scanX, innerY, scanX + 1, baseline, 0x66B07A2E);
        }

        graphics.drawString(font, "20 HZ", panelX + 10, panelY + panelH - 11, MUTED, false);
        String high = "8 KHZ";
        graphics.drawString(font, high, panelX + panelW - font.width(high) - 10, panelY + panelH - 11, MUTED, false);
    }

    private void drawNowPlaying(GuiGraphics graphics, int x, int y) {
        int nowX = x + 224;
        int nowY = y + 136;
        int nowW = 104;
        int nowH = 66;
        graphics.fill(nowX, nowY, nowX + nowW, nowY + nowH, PANEL);
        panelFrame(graphics, nowX, nowY, nowW, nowH);

        DeckSnapshot.TrackInfo track = currentTrack();
        if (track == null || !track.filled()) {
            graphics.drawString(font, Component.translatable("gui.netmusic_airwaves.no_track").getString(),
                    nowX + 5, nowY + 5, MUTED, false);
        } else {
            graphics.drawString(font, fit(track.name(), nowW - 10), nowX + 5, nowY + 4, TEXT, false);
            graphics.drawString(font, fit(track.artists(), nowW - 10), nowX + 5, nowY + 14, MUTED, false);
            String lyric = currentLyric();
            graphics.drawString(font, fit(lyric == null || lyric.isBlank() ? statusText() : lyric, nowW - 10),
                    nowX + 5, nowY + 25, ACCENT, false);
        }

        int elapsed = elapsedSeconds();
        int duration = snapshot == null ? 0 : snapshot.duration();
        int barX = nowX + 5;
        int barY = nowY + 40;
        int barW = nowW - 10;
        graphics.fill(barX, barY, barX + barW, barY + 4, GRID);
        if (duration > 0) {
            int filled = Mth.clamp((int) (barW * (elapsed / (double) duration)), 0, barW);
            graphics.fill(barX, barY, barX + filled, barY + 4, ACCENT);
        }
        graphics.drawString(font, formatTime(elapsed) + " / " + formatTime(duration),
                nowX + 5, nowY + 47, MUTED, false);
    }

    @Nullable
    private String currentLyric() {
        if (lyricRecord == null) {
            return null;
        }
        int tick = elapsedSeconds() * 20;
        String line = null;
        for (var entry : lyricRecord.getLyrics().int2ObjectEntrySet()) {
            if (entry.getIntKey() <= tick) {
                line = entry.getValue();
            } else {
                break;
            }
        }
        return line == null || line.isBlank() ? null : line;
    }

    private void drawSettingsRow(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int rowY = y + 208;
        boolean carousel = snapshot != null && (snapshot.flags() & DeckSnapshot.FLAG_CAROUSEL) != 0;
        boolean reverse = snapshot != null && (snapshot.flags() & DeckSnapshot.FLAG_REVERSE) != 0;

        drawToggleButton(graphics, x + 12, rowY, 98, 14,
                Component.translatable("gui.netmusic_airwaves.mode").getString() + ": "
                        + Component.translatable(carousel
                        ? "gui.netmusic_airwaves.mode.carousel"
                        : "gui.netmusic_airwaves.mode.normal").getString(),
                carousel, mouseX, mouseY);
        drawToggleButton(graphics, x + 114, rowY, 98, 14,
                Component.translatable("gui.netmusic_airwaves.direction").getString() + ": "
                        + Component.translatable(reverse
                        ? "gui.netmusic_airwaves.direction.reverse"
                        : "gui.netmusic_airwaves.direction.forward").getString(),
                reverse, mouseX, mouseY);
        drawToggleButton(graphics, x + 216, rowY, 98, 14,
                Component.translatable("gui.netmusic_airwaves.play_mode").getString() + ": "
                        + Component.translatable(switch (currentPlayMode()) {
                            case 1 -> "gui.netmusic_airwaves.play_mode.shuffle.short_name";
                            case 2 -> "gui.netmusic_airwaves.play_mode.loop.short_name";
                            default -> "gui.netmusic_airwaves.play_mode.sequential.short_name";
                        }).getString(),
                currentPlayMode() != 0, mouseX, mouseY);
    }

    private void drawToggleButton(GuiGraphics graphics, int bx, int by, int bw, int bh,
                                  String label, boolean active, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, bx, by, bw, bh);
        graphics.fill(bx, by, bx + bw, by + bh, hover ? PANEL_HOVER : PANEL_ALT);
        frame(graphics, bx, by, bw, bh, active ? ACCENT : INNER);
        graphics.drawString(font, label, bx + (bw - font.width(label)) / 2, by + 3,
                active ? ACCENT : TEXT, false);
    }

    private void drawStationRow(GuiGraphics graphics, int x, int y) {
        int rowY = y + 226;
        if (snapshot != null && !snapshot.station().isBlank()) {
            String station = fit(snapshot.station() + " · " + snapshot.tuning()
                    + " · " + snapshot.cablePercent() + "%", 150);
            graphics.drawString(font, station, x + 12, rowY + 4, BODY_TEXT, false);
        }
        int statusColor = statusColor();
        graphics.fill(x + 168, rowY + 7, x + 172, rowY + 11, statusColor);
        graphics.drawString(font, fit(statusText(), 74), x + 176, rowY + 4, statusColor, false);
    }

    private void drawTransportButtons(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int buttonY = y + 226;
        int state = snapshot == null ? DeckSnapshot.STATE_STOPPED : snapshot.state();
        boolean hasTracks = hasAnyTrack();

        boolean[] enabled = {state != DeckSnapshot.STATE_STOPPED, state == DeckSnapshot.STATE_PLAYING,
                hasTracks && state != DeckSnapshot.STATE_PLAYING, state != DeckSnapshot.STATE_STOPPED};
        for (int i = 0; i < enabled.length; i++) {
            int buttonX = x + 258 + i * 18;
            boolean hover = inside(mouseX, mouseY, buttonX, buttonY, 16, 16);
            graphics.fill(buttonX, buttonY, buttonX + 16, buttonY + 16,
                    !enabled[i] ? DISABLED : hover ? WASH_CURRENT : PANEL_ALT);
            frame(graphics, buttonX, buttonY, 16, 16, enabled[i] ? (hover ? ACCENT : INNER) : GRID);
            int iconColor = enabled[i] ? TEXT : MUTED;
            int cx = buttonX + 8;
            int cy = buttonY + 8;
            switch (i) {
                case 0 -> {
                    drawRightTriangle(graphics, cx - 2, cy, iconColor);
                    graphics.fill(cx + 3, cy - 4, cx + 5, cy + 5, iconColor);
                }
                case 1 -> {
                    graphics.fill(cx - 4, cy - 4, cx - 1, cy + 4, iconColor);
                    graphics.fill(cx + 1, cy - 4, cx + 4, cy + 4, iconColor);
                }
                case 2 -> drawRightTriangle(graphics, cx, cy, iconColor);
                default -> graphics.fill(cx - 4, cy - 4, cx + 4, cy + 4, iconColor);
            }
        }
    }

    private void drawInventoryPanel(GuiGraphics graphics, int x, int y) {
        int invX = x + DeckMenu.PLAYER_INV_X - 6;
        int invY = y + SpectrumDeckMenu.PLAYER_INV_Y - 4;
        int invW = 9 * 18 + 12;
        graphics.fill(invX, invY, invX + invW, invY + 82, PANEL);
        panelFrame(graphics, invX, invY, invW, 82);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlotBackground(graphics, x + DeckMenu.PLAYER_INV_X + column * 18,
                        y + SpectrumDeckMenu.PLAYER_INV_Y + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlotBackground(graphics, x + DeckMenu.PLAYER_INV_X + column * 18,
                    y + SpectrumDeckMenu.PLAYER_HOTBAR_Y);
        }
    }

    /** Classic inset slot: recessed look with dark top/left and light bottom/right edges. */
    private void drawSlotBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, SLOT_BG);
        graphics.fill(x, y, x + 18, y + 1, INNER);
        graphics.fill(x, y, x + 1, y + 18, INNER);
        graphics.fill(x, y + 17, x + 18, y + 18, BEVEL);
        graphics.fill(x + 17, y, x + 18, y + 18, BEVEL);
    }

    private void drawRightTriangle(GuiGraphics graphics, int cx, int cy, int color) {
        for (int i = 0; i < 5; i++) {
            graphics.fill(cx + 4 - i, cy - i, cx + 5 - i, cy + i + 1, color);
        }
    }

    private void frame(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Inset panel: dark inner border plus a light bevel, like Create's cream fields. */
    private void panelFrame(GuiGraphics graphics, int x, int y, int w, int h) {
        frame(graphics, x, y, w, h, INNER);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 2, BEVEL);
        graphics.fill(x + 1, y + 1, x + 2, y + h - 1, BEVEL);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Nullable
    private DeckSnapshot.TrackInfo track(int index) {
        if (snapshot == null || index < 0 || index >= snapshot.tracks().size()) {
            return null;
        }
        return snapshot.tracks().get(index);
    }

    @Nullable
    private DeckSnapshot.TrackInfo currentTrack() {
        return snapshot == null ? null : track(snapshot.currentSlot());
    }

    private boolean hasAnyTrack() {
        if (snapshot == null) {
            return false;
        }
        for (DeckSnapshot.TrackInfo track : snapshot.tracks()) {
            if (track.filled()) {
                return true;
            }
        }
        return false;
    }

    private int elapsedSeconds() {
        if (snapshot == null) {
            return 0;
        }
        int elapsed = snapshot.elapsed();
        if (snapshot.state() == DeckSnapshot.STATE_PLAYING) {
            elapsed += (int) ((Util.getMillis() - snapshotAt) / 1000L);
        }
        int duration = snapshot.duration();
        if (duration > 0) {
            elapsed = Math.min(elapsed, duration);
        }
        return Math.max(0, elapsed);
    }

    public String statusText() {
        DeckSnapshot.TrackInfo track = currentTrack();
        if (track == null || !track.filled()) {
            return Component.translatable("gui.netmusic_airwaves.status.no_track").getString();
        }
        if (snapshot == null) {
            return Component.translatable("gui.netmusic_airwaves.status.offline").getString();
        }
        if (snapshot.state() == DeckSnapshot.STATE_PAUSED) {
            return Component.translatable("gui.netmusic_airwaves.status.paused").getString();
        }
        if (snapshot.state() == DeckSnapshot.STATE_STOPPED) {
            return Component.translatable("gui.netmusic_airwaves.status.stopped").getString();
        }
        if ((snapshot.flags() & DeckSnapshot.FLAG_CABLE) == 0) {
            return Component.translatable("gui.netmusic_airwaves.status.no_cable").getString();
        }
        if ((snapshot.flags() & DeckSnapshot.FLAG_TRANSMITTER) == 0) {
            return Component.translatable("gui.netmusic_airwaves.status.no_transmitter").getString();
        }
        if ((snapshot.flags() & DeckSnapshot.FLAG_ON_AIR) == 0) {
            return Component.translatable(snapshot.station().isBlank()
                    ? "gui.netmusic_airwaves.status.tx_off"
                    : "gui.netmusic_airwaves.status.preparing").getString();
        }
        return Component.translatable("gui.netmusic_airwaves.status.on_air").getString();
    }

    private int statusColor() {
        if (snapshot == null) {
            return MUTED;
        }
        if (snapshot.state() == DeckSnapshot.STATE_PAUSED) {
            return AMBER;
        }
        if (snapshot.state() == DeckSnapshot.STATE_STOPPED) {
            return MUTED;
        }
        return (snapshot.flags() & DeckSnapshot.FLAG_ON_AIR) != 0 ? GREEN : AMBER;
    }

    private void ensureLyrics() {
        DeckSnapshot.TrackInfo track = currentTrack();
        String rawUrl = track == null || !track.filled() ? "" : track.rawUrl();
        if (Objects.equals(rawUrl, lyricUrl)) {
            return;
        }
        lyricUrl = rawUrl;
        lyricRecord = null;
        if (rawUrl.isEmpty()) {
            return;
        }
        Matcher matcher = SONG_ID.matcher(rawUrl);
        if (!matcher.find()) {
            return;
        }
        long songId;
        try {
            songId = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return;
        }
        String songName = track == null ? "" : track.name();
        CompletableFuture.supplyAsync(() -> {
            try {
                return NetMusic.NET_EASE_WEB_API == null ? null : NetMusic.NET_EASE_WEB_API.lyric(songId);
            } catch (Exception ignored) {
                return null;
            }
        }, Util.backgroundExecutor()).thenAccept(json -> {
            if (json == null) {
                return;
            }
            Minecraft.getInstance().execute(() -> {
                if (Objects.equals(lyricUrl, rawUrl)) {
                    lyricRecord = LyricParser.parseLyric(json, songName);
                }
            });
        });
    }

    private String fit(String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, Math.max(0, maxWidth - 6)) + "...";
    }

    private static String formatTime(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%02d:%02d", safe / 60, safe % 60);
    }
}
