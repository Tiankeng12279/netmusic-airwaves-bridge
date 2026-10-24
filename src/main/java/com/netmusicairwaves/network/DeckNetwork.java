package com.netmusicairwaves.network;

import com.netmusicairwaves.content.RadioModulatorBlockEntity;
import com.netmusicairwaves.menu.DeckMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class DeckNetwork {
    private static final double MAX_INTERACTION_DISTANCE_SQR = 64.0D;
    private static final double MAX_READ_DISTANCE_SQR = 32.0D * 32.0D;

    private DeckNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("4").optional();
        registrar.playToClient(DeckSnapshot.TYPE, DeckSnapshot.CODEC, DeckSnapshot::handle);
        registrar.playToServer(RequestDeckSnapshotPayload.TYPE, RequestDeckSnapshotPayload.CODEC,
                RequestDeckSnapshotPayload::handle);
        registrar.playToServer(DeckActionPayload.TYPE, DeckActionPayload.CODEC, DeckActionPayload::handle);
    }

    public static void requestSnapshot(BlockPos deckPos) {
        PacketDistributor.sendToServer(new RequestDeckSnapshotPayload(deckPos));
    }

    public static void sendAction(BlockPos deckPos, int action, int index) {
        PacketDistributor.sendToServer(new DeckActionPayload(deckPos, action, index));
    }

    public static void sendSnapshot(ServerPlayer player, DeckSnapshot snapshot) {
        PacketDistributor.sendToPlayer(player, snapshot);
    }

    public static void broadcastSnapshot(ServerLevel level, DeckSnapshot snapshot) {
        BlockPos pos = snapshot.pos();
        PacketDistributor.sendToPlayersNear(level, null, pos.getX() + 0.5D, pos.getY() + 0.5D,
                pos.getZ() + 0.5D, 32.0D, snapshot);
    }

    static void handleRequest(RequestDeckSnapshotPayload payload, ServerPlayer player) {
        if (!(player.level().getBlockEntity(payload.deckPos()) instanceof RadioModulatorBlockEntity deck)) {
            return;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(payload.deckPos())) > MAX_READ_DISTANCE_SQR) {
            return;
        }
        sendSnapshot(player, deck.snapshot());
    }

    static void handleAction(DeckActionPayload payload, ServerPlayer player) {
        if (payload.action() == DeckActionPayload.ACTION_SET_PAGE) {
            if (player.containerMenu instanceof DeckMenu menu
                    && menu.getPos().equals(payload.deckPos())) {
                menu.setPage(payload.index());
            }
            return;
        }
        if (!(player.level().getBlockEntity(payload.deckPos()) instanceof RadioModulatorBlockEntity deck)) {
            return;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(payload.deckPos())) > MAX_INTERACTION_DISTANCE_SQR) {
            return;
        }
        deck.handleAction(payload.action(), payload.index(), player);
        sendSnapshot(player, deck.snapshot());
    }
}
