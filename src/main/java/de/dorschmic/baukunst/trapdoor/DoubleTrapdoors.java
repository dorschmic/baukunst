package de.dorschmic.baukunst.trapdoor;

import de.dorschmic.baukunst.Baukunst;
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
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Two trapdoors on opposite sides of one block space (great for half-timbered walls): clicking the inner
 * face of a single trapdoor with another trapdoor puts the new one on the opposite side of the same block.
 * They cannot be opened. Breaking removes only the one you look at.
 */
public final class DoubleTrapdoors {
	public static DoubleTrapdoorBlock BLOCK;
	public static BlockEntityType<DoubleTrapdoorBlockEntity> BLOCK_ENTITY;

	private DoubleTrapdoors() {
	}

	public static void init() {
		Identifier id = Baukunst.id("double_trapdoor");
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, id, new DoubleTrapdoorBlock(BlockBehaviour.Properties.of()
				.setId(ResourceKey.create(Registries.BLOCK, id))
				.strength(3.0F)
				.sound(SoundType.WOOD)
				.noOcclusion()
				.noLootTable()
				.dynamicShape()));
		BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id,
				new BlockEntityType<>(DoubleTrapdoorBlockEntity::new, Set.of(BLOCK)));

		UseBlockCallback.EVENT.register(DoubleTrapdoors::onUse);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				!(blockEntity instanceof DoubleTrapdoorBlockEntity doors) || breakOne(level, player, pos, doors));
	}

	public static boolean isTrapdoor(BlockState state) {
		return state.getBlock() instanceof TrapDoorBlock;
	}

	/** The side of the block space a trapdoor covers, read from its shape. */
	public static Direction sideOf(BlockState trapdoor) {
		AABB box = trapdoor.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds();
		if (box.getYsize() < 0.5) {
			return box.minY < 0.01 ? Direction.DOWN : Direction.UP;
		}
		if (box.getZsize() < 0.5) {
			return box.minZ < 0.01 ? Direction.NORTH : Direction.SOUTH;
		}
		return box.minX < 0.01 ? Direction.WEST : Direction.EAST;
	}

	/**
	 * A closed or open trapdoor state covering {@code side}, keeping the rest (facing, half) from
	 * {@code like} where that fits.
	 */
	public static BlockState panelState(BlockState like, Direction side) {
		BlockState base = like.setValue(TrapDoorBlock.POWERED, false).setValue(TrapDoorBlock.WATERLOGGED, false);
		if (side.getAxis() == Direction.Axis.Y) {
			return base.setValue(TrapDoorBlock.OPEN, false).setValue(TrapDoorBlock.HALF, side == Direction.UP ? Half.TOP : Half.BOTTOM);
		}
		for (Direction facing : new Direction[] {side.getOpposite(), side}) {
			BlockState open = base.setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.FACING, facing);
			if (sideOf(open) == side) {
				return open;
			}
		}
		throw new IllegalStateException("no trapdoor state covers " + side);
	}

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isSpectator() || !(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof TrapDoorBlock)) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState existing = level.getBlockState(pos);
		Direction face = hit.getDirection();
		Direction side;
		if (isTrapdoor(existing) && face == sideOf(existing).getOpposite()) {
			side = sideOf(existing); // clicked the inner face of the trapdoor
		} else {
			// Clicked a neighbour: the trapdoor in front of the clicked face may have its free side towards us.
			pos = pos.relative(face);
			existing = level.getBlockState(pos);
			if (!isTrapdoor(existing) || sideOf(existing) != face) {
				return InteractionResult.PASS;
			}
			side = face;
		}
		if (!player.mayBuild() || !level.mayInteract(player, pos)) {
			return InteractionResult.PASS;
		}
		Direction newSide = side.getOpposite();
		BlockState like = item.getBlock().defaultBlockState();
		if (newSide.getAxis() == Direction.Axis.Y) {
			like = like.setValue(TrapDoorBlock.FACING, player.getDirection().getOpposite()); // like vanilla
		} else {
			like = like.setValue(TrapDoorBlock.HALF, existing.getValue(TrapDoorBlock.HALF));
		}
		BlockState newPanel = panelState(like, newSide);
		if (!level.isUnobstructed(newPanel, pos, CollisionContext.of(player))) {
			return InteractionResult.FAIL;
		}
		BlockState oldPanel = panelState(existing, side);
		level.setBlock(pos, BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof DoubleTrapdoorBlockEntity doors) {
			doors.set(oldPanel, newPanel);
		}
		SoundType sound = newPanel.getSoundType();
		level.playSound(player, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		if (!player.isCreative()) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	/** Index (0 or 1) of the trapdoor the player is looking at. */
	public static int targetedPanel(Player player, BlockPos pos, DoubleTrapdoorBlockEntity doors) {
		Direction side = sideOf(doors.get(0));
		HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
		if (!(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(pos)) {
			return 0;
		}
		Vec3 offset = blockHit.getLocation().add(player.getLookAngle().scale(0.01)).subtract(Vec3.atCenterOf(pos));
		double along = offset.x * side.getStepX() + offset.y * side.getStepY() + offset.z * side.getStepZ();
		return along >= 0 ? 0 : 1;
	}

	/** @return true if vanilla should break the whole block */
	private static boolean breakOne(Level level, Player player, BlockPos pos, DoubleTrapdoorBlockEntity doors) {
		int target = targetedPanel(player, pos, doors);
		BlockState removed = doors.get(target);
		BlockState remaining = doors.get(1 - target);
		level.setBlock(pos, remaining, Block.UPDATE_ALL);
		level.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(removed));
		if (!player.isCreative()) {
			player.getMainHandItem().mineBlock(level, removed, pos, player);
			if (player.hasCorrectToolForDrops(removed)) {
				Block.popResource(level, pos, new ItemStack(removed.getBlock()));
			}
		}
		return false;
	}

}
