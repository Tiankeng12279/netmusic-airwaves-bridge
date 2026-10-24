package com.netmusicairwaves.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The playlist disc player: functionally identical to the plain disc player (CD
 * playlist, cabled transmitter, redstone control) with a metal front face that
 * shows the live spectrum and an analyzer-style GUI. The plain player stays
 * GUI-less. Its model is additionally driven by how many discs are stored.
 */
public class SpectrumMonitorBlock extends RadioModulatorBlock {
    public static final MapCodec<SpectrumMonitorBlock> CODEC = simpleCodec(SpectrumMonitorBlock::new);

    /** Disc storage level for the model: 0 / 1-4 / 5-8 / 9-12 / 13-16 discs. */
    public enum FillLevel implements net.minecraft.util.StringRepresentable {
        EMPTY,
        QUARTER,
        HALF,
        THREE_QUARTERS,
        FULL;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<FillLevel> FILL = EnumProperty.create("fill", FillLevel.class);

    public SpectrumMonitorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public SpectrumMonitorBlock() {
        this(BlockBehaviour.Properties.of()
                .sound(SoundType.METAL)
                .strength(1.5F)
                .noOcclusion());
    }

    @Override
    protected MapCodec<? extends RadioModulatorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FILL);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // The spectrum deck keeps the monitor's full-cube silhouette so the face fills the block.
        return Shapes.block();
    }

    @Override
    protected void onEmptyHandActivate(ServerPlayer player, RadioModulatorBlockEntity deck, BlockPos pos) {
        player.openMenu(deck, pos);
    }
}
