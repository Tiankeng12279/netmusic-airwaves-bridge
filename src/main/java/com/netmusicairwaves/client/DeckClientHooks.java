package com.netmusicairwaves.client;

import com.netmusicairwaves.network.DeckSnapshot;

public final class DeckClientHooks {
    private DeckClientHooks() {
    }

    public static void acceptSnapshot(DeckSnapshot snapshot) {
        DeckClientData.accept(snapshot);
    }
}
