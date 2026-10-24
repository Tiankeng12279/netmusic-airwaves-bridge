package com.netmusicairwaves.content;

import com.airwaves.cable.Link;
import com.airwaves.item.CableItem;
import com.airwaves.logic.Cables;
import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import com.mojang.serialization.MapCodec;
import com.netmusicairwaves.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RadioModulatorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<RadioModulatorBlock> CODEC = simpleCodec(RadioModulatorBlock::new);
    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);

    /** Model state: no disc / disc inserted / playing / paused. */
    public enum PlayState implements net.minecraft.util.StringRepresentable {
        NONE,
        IDLE,
        PLAYING,
        PAUSED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<PlayState> PLAY_STATE = EnumProperty.create("play_state", PlayState.class);

    public RadioModulatorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PLAY_STATE, PlayState.NONE));
    }

    public RadioModulatorBlock() {
        this(BlockBehaviour.Properties.of()
                .sound(SoundType.WOOD)
                .strength(0.8F)
                .noOcclusion());
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PLAY_STATE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadioModulatorBlockEntity(pos, state);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (hand == InteractionHand.OFF_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof CableItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return stack.isEmpty() || ItemMusicCD.getSongInfo(stack) != null
                    ? ItemInteractionResult.SUCCESS
                    : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof RadioModulatorBlockEntity blockEntity)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.isEmpty()) {
            if (player instanceof ServerPlayer serverPlayer) {
                onEmptyHandActivate(serverPlayer, blockEntity, pos);
            }
            return ItemInteractionResult.sidedSuccess(false);
        }
        if (ItemMusicCD.getSongInfo(stack) != null) {
            blockEntity.insertCd(stack, player);
            return ItemInteractionResult.sidedSuccess(false);
        }
        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof RadioModulatorBlockEntity blockEntity
                && player instanceof ServerPlayer serverPlayer) {
            onEmptyHandActivate(serverPlayer, blockEntity, pos);
        }
        return InteractionResult.sidedSuccess(false);
    }

    /**
     * The plain deck has no GUI: an empty-hand click hands back a disc, and a
     * sneaking empty-hand click toggles carousel (shift-eject) mode.
     * The spectrum deck overrides this to open its analyzer GUI.
     */
    protected void onEmptyHandActivate(ServerPlayer player, RadioModulatorBlockEntity deck, BlockPos pos) {
        if (player.isShiftKeyDown()) {
            deck.toggleCarousel(player);
        } else {
            deck.ejectCurrentOrFirst(player);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RadioModulatorBlockEntity blockEntity) {
            blockEntity.onRedstoneChanged(level.hasNeighborSignal(pos));
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof RadioModulatorBlockEntity blockEntity) {
            return blockEntity.getAnalogSignal();
        }
        return 0;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof RadioModulatorBlockEntity blockEntity) {
            if (!level.isClientSide) {
                Cables.broken(level, pos, new ArrayList<Link>(blockEntity.links().list()));
            }
            blockEntity.onBlockRemoved();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof RadioModulatorBlockEntity blockEntity) {
            for (int i = 0; i < RadioModulatorBlockEntity.SLOT_COUNT; i++) {
                ItemStack cd = blockEntity.getCd(i);
                if (!cd.isEmpty()) {
                    drops.add(cd.copy());
                }
            }
        }
        return drops;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> entityType) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(entityType, ModBlockEntities.RADIO_MODULATOR.get(),
                (tickLevel, tickPos, tickState, blockEntity) ->
                        RadioModulatorBlockEntity.tick(tickLevel, tickPos, tickState, blockEntity));
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> entityType, BlockEntityType<E> expectedType, BlockEntityTicker<? super E> ticker) {
        return expectedType == entityType ? (BlockEntityTicker<A>) ticker : null;
    }
}
