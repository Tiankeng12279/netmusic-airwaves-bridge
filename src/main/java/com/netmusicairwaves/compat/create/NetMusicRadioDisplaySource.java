package com.netmusicairwaves.compat.create;

import com.netmusicairwaves.content.RadioModulatorBlockEntity;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public final class NetMusicRadioDisplaySource extends DisplaySource {
    public static final NetMusicRadioDisplaySource INSTANCE = new NetMusicRadioDisplaySource();

    private NetMusicRadioDisplaySource() {
    }

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (context.getSourceBlockEntity() instanceof RadioModulatorBlockEntity blockEntity) {
            return blockEntity.getDisplayText();
        }
        return EMPTY;
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }
}
