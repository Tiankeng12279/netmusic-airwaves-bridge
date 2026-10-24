package com.netmusicairwaves.network;

import com.netmusicairwaves.NetMusicAirwaves;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record DeckSnapshot(BlockPos pos, int state, int currentSlot, int elapsed, int duration,
                           int flags, int cablePercent, String station, String tuning,
                           List<TrackInfo> tracks, int[] bands) implements CustomPacketPayload {
    public static final int SLOT_COUNT = 16;
    public static final int BAND_COUNT = 16;
    public static final int STATE_STOPPED = 0;
    public static final int STATE_PLAYING = 1;
    public static final int STATE_PAUSED = 2;

    public static final int FLAG_CABLE = 1;
    public static final int FLAG_TRANSMITTER = 2;
    public static final int FLAG_ON_AIR = 4;
    public static final int FLAG_CAROUSEL = 8;
    public static final int FLAG_REVERSE = 16;
    public static final int FLAG_SHUFFLE = 32;
    public static final int FLAG_LOOP = 64;

    public static final CustomPacketPayload.Type<DeckSnapshot> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(NetMusicAirwaves.MOD_ID, "deck_snapshot"));

    public static final StreamCodec<ByteBuf, DeckSnapshot> CODEC = StreamCodec.of(
            DeckSnapshot::write,
            DeckSnapshot::read
    );

    public DeckSnapshot {
        station = station == null ? "" : station;
        tuning = tuning == null ? "" : tuning;
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
        if (bands == null || bands.length != BAND_COUNT) {
            bands = new int[BAND_COUNT];
        }
    }

    private static void write(ByteBuf buffer, DeckSnapshot snapshot) {
        BlockPos.STREAM_CODEC.encode(buffer, snapshot.pos);
        buffer.writeByte(snapshot.state);
        buffer.writeByte(snapshot.currentSlot);
        ByteBufCodecs.VAR_INT.encode(buffer, Math.max(0, snapshot.elapsed));
        ByteBufCodecs.VAR_INT.encode(buffer, Math.max(0, snapshot.duration));
        buffer.writeByte(snapshot.flags);
        ByteBufCodecs.VAR_INT.encode(buffer, Math.max(0, snapshot.cablePercent));
        ByteBufCodecs.STRING_UTF8.encode(buffer, snapshot.station);
        ByteBufCodecs.STRING_UTF8.encode(buffer, snapshot.tuning);
        for (int i = 0; i < SLOT_COUNT; i++) {
            TrackInfo track = i < snapshot.tracks.size() ? snapshot.tracks.get(i) : TrackInfo.EMPTY;
            buffer.writeBoolean(track.filled());
            if (track.filled()) {
                ByteBufCodecs.STRING_UTF8.encode(buffer, track.name());
                ByteBufCodecs.STRING_UTF8.encode(buffer, track.artists());
                ByteBufCodecs.VAR_INT.encode(buffer, Math.max(0, track.seconds()));
                ByteBufCodecs.STRING_UTF8.encode(buffer, track.rawUrl());
            }
        }
        for (int i = 0; i < BAND_COUNT; i++) {
            buffer.writeByte(Math.max(0, Math.min(255, snapshot.bands[i])));
        }
    }

    private static DeckSnapshot read(ByteBuf buffer) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
        int state = buffer.readUnsignedByte();
        int currentSlot = buffer.readByte();
        int elapsed = ByteBufCodecs.VAR_INT.decode(buffer);
        int duration = ByteBufCodecs.VAR_INT.decode(buffer);
        int flags = buffer.readUnsignedByte();
        int cablePercent = ByteBufCodecs.VAR_INT.decode(buffer);
        String station = ByteBufCodecs.STRING_UTF8.decode(buffer);
        String tuning = ByteBufCodecs.STRING_UTF8.decode(buffer);
        List<TrackInfo> tracks = new ArrayList<>(4);
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!buffer.readBoolean()) {
                tracks.add(TrackInfo.EMPTY);
                continue;
            }
            String name = ByteBufCodecs.STRING_UTF8.decode(buffer);
            String artists = ByteBufCodecs.STRING_UTF8.decode(buffer);
            int seconds = ByteBufCodecs.VAR_INT.decode(buffer);
            String rawUrl = ByteBufCodecs.STRING_UTF8.decode(buffer);
            tracks.add(new TrackInfo(true, name, artists, seconds, rawUrl));
        }
        int[] bands = new int[BAND_COUNT];
        for (int i = 0; i < BAND_COUNT; i++) {
            bands[i] = buffer.readUnsignedByte();
        }
        return new DeckSnapshot(pos, state, currentSlot, elapsed, duration, flags, cablePercent,
                station, tuning, tracks, bands);
    }

    public static void handle(DeckSnapshot snapshot, IPayloadContext context) {
        if (context.flow().isClientbound()) {
            context.enqueueWork(() -> com.netmusicairwaves.client.DeckClientHooks.acceptSnapshot(snapshot));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record TrackInfo(boolean filled, String name, String artists, int seconds, String rawUrl) {
        public static final TrackInfo EMPTY = new TrackInfo(false, "", "", 0, "");

        public TrackInfo {
            name = name == null ? "" : name;
            artists = artists == null ? "" : artists;
            rawUrl = rawUrl == null ? "" : rawUrl;
        }
    }
}
