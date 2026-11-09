package de.dorschmic.baukunst.mini;

import de.dorschmic.baukunst.Baukunst;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Sneak-placing mini blocks snaps them into a 2×2×2 grid, up to 8 per block space. */
public final class MiniGrid {
	public static MiniGridBlock BLOCK;
	public static BlockEntityType<MiniGridBlockEntity> BLOCK_ENTITY;

	private MiniGrid() {
	}

	public static void init() {
		var id = Baukunst.id("mini_grid");
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, id, new MiniGridBlock(BlockBehaviour.Properties.of()
				.setId(ResourceKey.create(Registries.BLOCK, id))
				.strength(1.0F)
				.sound(SoundType.STONE)
				.noOcclusion()
				.dynamicShape()
				.noLootTable()
				.pushReaction(PushReaction.IMMOVEABLE)));
		BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id,
				new BlockEntityType<>(MiniGridBlockEntity::new, Set.of(BLOCK)));

		// Breaking a grid removes only the mini block you are looking at.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(blockEntity instanceof MiniGridBlockEntity grid) || Integer.bitCount(grid.mask()) <= 1) {
				return true;
			}
			int slot = targetedSlot(player, pos, grid);
			Block source = grid.get(slot);
			grid.set(slot, null);
			if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
				de.dorschmic.baukunst.BlockEntityResync.schedule(serverLevel, pos);
			}
			MiniBlock mini = source == null ? null : MiniBlocks.forSource(source);
			if (mini != null && !player.isCreative()) {
				Block.popResource(level, pos, new ItemStack(mini));
			}
			if (source != null) {
				SoundType sound = source.defaultBlockState().getSoundType();
				level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, sound.getVolume(), sound.getPitch());
			}
			return false;
		});
	}

	/** Called by the mini block item when the player sneaks. */
	public static InteractionResult place(UseOnContext context, MiniBlock mini) {
		Level level = context.getLevel();
		Direction face = context.getClickedFace();
		// Step a quarter block out of the clicked surface: that is the centre of the neighbouring grid cell.
		Vec3 p = context.getClickLocation().add(face.getStepX() * 0.25, face.getStepY() * 0.25, face.getStepZ() * 0.25);
		BlockPos pos = BlockPos.containing(p);
		int slot = slotAt(p, pos);

		BlockState existing = level.getBlockState(pos);
		MiniGridBlockEntity grid;
		if (existing.is(BLOCK) && level.getBlockEntity(pos) instanceof MiniGridBlockEntity g) {
			if (g.get(slot) != null) {
				return InteractionResult.FAIL;
			}
			grid = g;
		} else if (existing.canBeReplaced()) {
			if (!level.setBlock(pos, BLOCK.defaultBlockState(), Block.UPDATE_ALL)
					|| !(level.getBlockEntity(pos) instanceof MiniGridBlockEntity g)) {
				return InteractionResult.FAIL;
			}
			grid = g;
		} else {
			return InteractionResult.FAIL;
		}
		grid.set(slot, mini.source());

		SoundType sound = mini.source().defaultBlockState().getSoundType();
		level.playSound(context.getPlayer(), pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F,
				sound.getPitch() * 0.8F);
		Player player = context.getPlayer();
		if (player == null || !player.isCreative()) {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	private static int slotAt(Vec3 p, BlockPos pos) {
		int x = Math.min(1, Math.max(0, (int) Math.floor((p.x - pos.getX()) * 2)));
		int y = Math.min(1, Math.max(0, (int) Math.floor((p.y - pos.getY()) * 2)));
		int z = Math.min(1, Math.max(0, (int) Math.floor((p.z - pos.getZ()) * 2)));
		return MiniGridBlockEntity.slotIndex(x, y, z);
	}

	/** The occupied grid cell the player is looking at. */
	private static int targetedSlot(Player player, BlockPos pos, MiniGridBlockEntity grid) {
		HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
		if (hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos)) {
			// Step a little further along the view ray: that is inside the cell that was hit, even when the
			// ray hits exactly on an edge between two cells.
			Vec3 inside = blockHit.getLocation().add(player.getLookAngle().scale(0.01));
			int slot = slotAt(inside, pos);
			if (grid.get(slot) != null) {
				return slot;
			}
		}
		for (int i = 0; i < 8; i++) {
			if (grid.get(i) != null) {
				return i;
			}
		}
		return 0;
	}
}
