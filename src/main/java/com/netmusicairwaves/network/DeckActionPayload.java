package com.netmusicairwaves.network;

import com.netmusicairwaves.NetMusicAirwaves;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DeckActionPayload(BlockPos deckPos, int action, int index) implements CustomPacketPayload {
    public static final int ACTION_PAUSE = 1;
    public static final int ACTION_PLAY = 2;
    public static final int ACTION_STOP = 3;
    public static final int ACTION_SELECT = 4;
    public static final int ACTION_EJECT = 5;
    public static final int ACTION_SET_PAGE = 7;
    public static final int ACTION_SET_MODE = 8;
    public static final int ACTION_SET_DIRECTION = 9;
    public static final int ACTION_SET_PLAY_MODE = 10;
    public static final int ACTION_NEXT = 11;

    public static final CustomPacketPayload.Type<DeckActionPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(NetMusicAirwaves.MOD_ID, "deck_action"));

    public static final StreamCodec<ByteBuf, DeckActionPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                BlockPos.STREAM_CODEC.encode(buffer, payload.deckPos);
                buffer.writeByte(payload.action);
                buffer.writeByte(payload.index);
            },
            buffer -> new DeckActionPayload(
                    BlockPos.STREAM_CODEC.decode(buffer),
                    buffer.readUnsignedByte(),
                    buffer.readByte()
            )
    );

    public static void handle(DeckActionPayload payload, IPayloadContext context) {
        if (context.flow().isServerbound() && context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> DeckNetwork.handleAction(payload, player));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
