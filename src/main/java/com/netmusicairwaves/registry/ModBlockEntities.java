package com.netmusicairwaves.registry;

import com.netmusicairwaves.NetMusicAirwaves;
import com.netmusicairwaves.content.RadioModulatorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NetMusicAirwaves.MOD_ID);

    // The radio deck and the spectrum deck share one block entity type.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadioModulatorBlockEntity>> RADIO_MODULATOR =
            BLOCK_ENTITIES.register("netmusic_radio_modulator", () -> BlockEntityType.Builder
                    .of(RadioModulatorBlockEntity::new, ModBlocks.RADIO_MODULATOR.get(), ModBlocks.SPECTRUM_MONITOR.get())
                    .build(null));

    private ModBlockEntities() {
    }
}
