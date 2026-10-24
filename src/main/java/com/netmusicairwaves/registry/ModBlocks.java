package com.netmusicairwaves.registry;

import com.netmusicairwaves.NetMusicAirwaves;
import com.netmusicairwaves.content.RadioModulatorBlock;
import com.netmusicairwaves.content.SpectrumMonitorBlock;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NetMusicAirwaves.MOD_ID);

    public static final DeferredBlock<RadioModulatorBlock> RADIO_MODULATOR =
            BLOCKS.register("netmusic_radio_modulator", () -> new RadioModulatorBlock());

    public static final DeferredBlock<SpectrumMonitorBlock> SPECTRUM_MONITOR =
            BLOCKS.register("netmusic_spectrum_monitor", () -> new SpectrumMonitorBlock());

    private ModBlocks() {
    }
}
