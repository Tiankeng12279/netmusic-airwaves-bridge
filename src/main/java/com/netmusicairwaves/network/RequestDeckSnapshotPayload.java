package com.netmusicairwaves.network;

import com.netmusicairwaves.NetMusicAirwaves;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestDeckSnapshotPayload(BlockPos deckPos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestDeckSnapshotPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(NetMusicAirwaves.MOD_ID, "request_deck_snapshot"));

    public static final StreamCodec<ByteBuf, RequestDeckSnapshotPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> BlockPos.STREAM_CODEC.encode(buffer, payload.deckPos),
            buffer -> new RequestDeckSnapshotPayload(BlockPos.STREAM_CODEC.decode(buffer))
    );

    public static void handle(RequestDeckSnapshotPayload payload, IPayloadContext context) {
        if (context.flow().isServerbound() && context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> DeckNetwork.handleRequest(payload, player));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
