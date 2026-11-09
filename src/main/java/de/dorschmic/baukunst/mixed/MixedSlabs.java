package de.dorschmic.baukunst.mixed;

import de.dorschmic.baukunst.Baukunst;
import de.dorschmic.baukunst.slab.VerticalSlabs;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * Two different slabs in one block space: clicking the free half of a slab with a different slab
 * combines both into a {@link MixedSlabBlock} – on top of each other or side by side (vertical slabs).
 * Breaking removes only the half you look at; the other one stays as a normal slab.
 */
public final class MixedSlabs {
	public static MixedSlabBlock BLOCK;
	public static BlockEntityType<MixedSlabBlockEntity> BLOCK_ENTITY;

	private MixedSlabs() {
	}

	public static void init() {
		Identifier id = Baukunst.id("mixed_slab");
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, id, new MixedSlabBlock(BlockBehaviour.Properties.of()
				.setId(ResourceKey.create(Registries.BLOCK, id))
				.strength(2.0F)
				.noLootTable()
				.dynamicShape()));
		BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id,
				new BlockEntityType<>(MixedSlabBlockEntity::new, Set.of(BLOCK)));

		UseBlockCallback.EVENT.register(MixedSlabs::onUse);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				!(blockEntity instanceof MixedSlabBlockEntity mixed) || breakHalf(level, player, pos, mixed));
	}

	public static boolean isSlab(Block block) {
		return block instanceof SlabBlock && block.defaultBlockState().hasProperty(VerticalSlabs.VERTICAL);
	}

	/** State of a single slab occupying the half of the block on {@code side}. */
	public static BlockState halfState(Block slab, Direction side) {
		BlockState state = slab.defaultBlockState();
		return switch (side) {
			case UP -> state.setValue(SlabBlock.TYPE, SlabType.TOP);
			case DOWN -> state.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
			default -> state.setValue(SlabBlock.TYPE, SlabType.BOTTOM).setValue(VerticalSlabs.VERTICAL, side);
		};
	}

	/** Side of the block a single slab occupies, or null for a double slab / no slab. */
	public static @Nullable Direction sideOf(BlockState state) {
		if (!isSlab(state.getBlock()) || state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
			return null;
		}
		Direction vertical = VerticalSlabs.facing(state);
		if (vertical != null) {
			return vertical;
		}
		return state.getValue(SlabBlock.TYPE) == SlabType.TOP ? Direction.UP : Direction.DOWN;
	}

	/** Whether a click on a slab occupying {@code side} aims at its free half (like vanilla merging). */
	private static boolean aimsAtFreeHalf(Direction side, BlockHitResult hit) {
		Direction face = hit.getDirection();
		if (face == side.getOpposite()) {
			return true; // the open inner face of the slab
		}
		Vec3 offset = hit.getLocation().subtract(Vec3.atCenterOf(hit.getBlockPos()));
		double along = offset.x * side.getStepX() + offset.y * side.getStepY() + offset.z * side.getStepZ();
		return face.getAxis() != side.getAxis() && along < 0;
	}

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isSpectator() || !(stack.getItem() instanceof BlockItem item) || !isSlab(item.getBlock())) {
			return InteractionResult.PASS;
		}
		Block adding = item.getBlock();

		BlockPos pos = hit.getBlockPos();
		BlockState existing = level.getBlockState(pos);
		Direction side = sideOf(existing);
		if (side == null || !aimsAtFreeHalf(side, hit)) {
			// Clicked a neighbour: the slab in front of the clicked face may have its free half towards us.
			pos = pos.relative(hit.getDirection());
			existing = level.getBlockState(pos);
			side = sideOf(existing);
			if (side != hit.getDirection()) {
				return InteractionResult.PASS;
			}
		}
		if (existing.is(adding)) {
			return InteractionResult.PASS; // same slab: vanilla makes a normal double slab
		}
		if (!player.mayBuild() || !level.mayInteract(player, pos)) {
			return InteractionResult.PASS;
		}
		BlockState newHalf = halfState(adding, side.getOpposite());
		if (!level.isUnobstructed(newHalf, pos, CollisionContext.of(player))) {
			return InteractionResult.FAIL;
		}

		boolean opaque = existing.canOcclude() && newHalf.canOcclude();
		level.setBlock(pos, BLOCK.defaultBlockState().setValue(MixedSlabBlock.FACING, side).setValue(MixedSlabBlock.OPAQUE, opaque),
				Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof MixedSlabBlockEntity mixed) {
			mixed.set(existing.getBlock(), adding);
		}
		SoundType sound = newHalf.getSoundType();
		level.playSound(player, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		if (!player.isCreative()) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	/** The slab state of the half the player is looking at. */
	public static BlockState targetedHalf(Player player, BlockPos pos, MixedSlabBlockEntity mixed) {
		return targetsFirst(player, pos, mixed) ? mixed.firstState() : mixed.secondState();
	}

	private static boolean targetsFirst(Player player, BlockPos pos, MixedSlabBlockEntity mixed) {
		Direction side = mixed.getBlockState().getValue(MixedSlabBlock.FACING);
		HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
		if (!(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(pos)) {
			return true;
		}
		// Step a tiny bit into the block so a click on the boundary face picks the half behind it.
		Vec3 inside = blockHit.getLocation().add(player.getLookAngle().scale(0.001));
		Vec3 offset = inside.subtract(Vec3.atCenterOf(pos));
		double along = offset.x * side.getStepX() + offset.y * side.getStepY() + offset.z * side.getStepZ();
		return along >= 0;
	}

	/** @return true if vanilla should break the whole block */
	private static boolean breakHalf(Level level, Player player, BlockPos pos, MixedSlabBlockEntity mixed) {
		boolean first = targetsFirst(player, pos, mixed);
		BlockState removed = first ? mixed.firstState() : mixed.secondState();
		BlockState remaining = first ? mixed.secondState() : mixed.firstState();
		level.setBlock(pos, remaining, Block.UPDATE_ALL);
		level.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(removed));
		if (!player.isCreative()) {
			ItemStack tool = player.getMainHandItem();
			tool.mineBlock(level, removed, pos, player);
			if (player.hasCorrectToolForDrops(removed)) {
				Block.popResource(level, pos, new ItemStack(removed.getBlock()));
			}
		}
		return false;
	}
}
