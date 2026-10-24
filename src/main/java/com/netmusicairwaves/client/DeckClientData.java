package com.netmusicairwaves.client;

import com.netmusicairwaves.network.DeckSnapshot;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DeckClientData {
    private static final Map<BlockPos, DeckSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private DeckClientData() {
    }

    public static void accept(DeckSnapshot snapshot) {
        SNAPSHOTS.put(snapshot.pos(), snapshot);
    }

    @Nullable
    public static DeckSnapshot get(BlockPos pos) {
        return SNAPSHOTS.get(pos);
    }

    public static void clear() {
        SNAPSHOTS.clear();
    }
}
