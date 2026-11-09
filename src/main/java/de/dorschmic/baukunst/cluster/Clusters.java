package de.dorschmic.baukunst.cluster;

import de.dorschmic.baukunst.Baukunst;
import de.dorschmic.baukunst.BlockEntityResync;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Small vanilla blocks (flowers, lanterns, chains, candles, torches …) can share one block space:
 * a single one stands in the middle as usual; placing a second one onto it packs both into quarters,
 * up to four – like leaf litter or pink petals.
 */
public final class Clusters {
	public static ClusterBlock BLOCK;
	public static BlockEntityType<ClusterBlockEntity> BLOCK_ENTITY;

	private static final Set<Block> ELIGIBLE = new HashSet<>();
	/** Blocks that already stack up to 4 by themselves in vanilla. */
	private static final List<IntegerProperty> VANILLA_STACKING = List.of(BlockStateProperties.CANDLES,
			BlockStateProperties.PICKLES, BlockStateProperties.FLOWER_AMOUNT, BlockStateProperties.SEGMENT_AMOUNT,
			BlockStateProperties.EGGS);

	private static final Set<String> EXCLUDED_PROPERTIES = Set.of("age", "thickness", "flower_amount", "segment_amount");

	private Clusters() {
	}

	public static boolean isEligible(Block block) {
		return ELIGIBLE.contains(block);
	}

	public static Set<Block> eligible() {
		return Set.copyOf(ELIGIBLE);
	}

	public static void init() {
		Identifier id = Baukunst.id("cluster");
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, id, new ClusterBlock(BlockBehaviour.Properties.of()
				.setId(ResourceKey.create(Registries.BLOCK, id))
				.strength(0.0F)
				.sound(SoundType.WOOD)
				.noOcclusion()
				.dynamicShape()
				.noLootTable()
				.lightLevel(s -> s.getValue(ClusterBlock.LIGHT))
				.pushReaction(PushReaction.POPPED)));
		BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id,
				new BlockEntityType<>(ClusterBlockEntity::new, Set.of(BLOCK)));

		for (Block block : BuiltInRegistries.BLOCK) {
			if (computeEligible(block)) {
				ELIGIBLE.add(block);
			}
		}
		Baukunst.LOGGER.info("{} kleine Blöcke lassen sich zu viert kombinieren: {}", ELIGIBLE.size(),
				ELIGIBLE.stream().map(b -> BuiltInRegistries.BLOCK.getKey(b).getPath()).sorted().toList());

		UseBlockCallback.EVENT.register(Clusters::onUse);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				!(blockEntity instanceof ClusterBlockEntity cluster) || breakOne(level, player, pos, cluster));
	}

	private static boolean computeEligible(Block block) {
		if (!"minecraft".equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())) {
			return false;
		}
		if (block instanceof EntityBlock || block instanceof ButtonBlock || block instanceof LeverBlock
				|| block instanceof RedstoneTorchBlock || block instanceof TripWireHookBlock || block instanceof FlowerPotBlock
				|| block instanceof DiodeBlock || block instanceof BaseRailBlock
				|| block instanceof LadderBlock
				// connect to their neighbours – would look broken inside a quarter
				|| block instanceof CrossCollisionBlock || block instanceof WallBlock) {
			return false;
		}
		if (!(block.asItem() instanceof BlockItem item) || item.getBlock() != block) {
			return false;
		}
		BlockState state = block.defaultBlockState();
		for (var property : state.getProperties()) {
			// growing plants, multi-part dripstone, and blocks that already fill quarters themselves
			if (EXCLUDED_PROPERTIES.contains(property.getName())) {
				return false;
			}
		}
		if (state.getRenderShape() != RenderShape.MODEL || state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
				|| state.hasProperty(BlockStateProperties.BED_PART)) {
			return false;
		}
		try {
			var shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
			if (shape.isEmpty()) {
				return false;
			}
			AABB box = shape.bounds();
			// Must fit into a quarter of the block (8×8 pixels).
			return box.getXsize() <= 0.5 + 1.0E-4 && box.getZsize() <= 0.5 + 1.0E-4;
		} catch (RuntimeException e) {
			return false;
		}
	}

	public static ItemStack dropFor(BlockState content) {
		int count = 1;
		for (IntegerProperty p : VANILLA_STACKING) {
			if (content.hasProperty(p)) {
				count = content.getValue(p);
			}
		}
		return new ItemStack(content.getBlock(), count);
	}

	public static int quarterAt(Vec3 hit, BlockPos pos) {
		int x = hit.x - pos.getX() >= 0.5 ? 1 : 0;
		int z = hit.z - pos.getZ() >= 0.5 ? 1 : 0;
		return x | (z << 1);
	}

	private static InteractionResult onUse(Player player, Level level, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
		if (player.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof BlockItem item) || !isEligible(item.getBlock())) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState clicked = level.getBlockState(pos);
		Block adding = item.getBlock();
		int quarter = quarterAt(hit.getLocation(), pos);
		BlockState addingState = placementState(adding, level, pos);
		if (addingState == null) {
			return InteractionResult.PASS;
		}

		if (clicked.is(BLOCK) && level.getBlockEntity(pos) instanceof ClusterBlockEntity cluster) {
			int slot = freeSlotNear(cluster, quarter);
			if (slot < 0) {
				return InteractionResult.PASS;
			}
			cluster.set(slot, addingState);
		} else if (isEligible(clicked.getBlock())) {
			if (clicked.is(adding) && VANILLA_STACKING.stream().anyMatch(clicked::hasProperty)) {
				return InteractionResult.PASS; // candles, sea pickles … keep their own stacking
			}
			if (!player.mayBuild()) {
				return InteractionResult.PASS;
			}
			// Second small block: pack both into quarters, the old one diagonally opposite the new one.
			level.setBlock(pos, BLOCK.defaultBlockState(), Block.UPDATE_ALL);
			if (!(level.getBlockEntity(pos) instanceof ClusterBlockEntity cluster)) {
				return InteractionResult.FAIL;
			}
			cluster.set(3 - quarter, clicked);
			cluster.set(quarter, addingState);
		} else {
			return InteractionResult.PASS;
		}

		SoundType sound = adding.defaultBlockState().getSoundType();
		level.playSound(player, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		if (!player.isCreative()) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	/** The state the small block would have in this space, or null if it cannot stand/hang there. */
	private static @org.jspecify.annotations.Nullable BlockState placementState(Block block, Level level, BlockPos pos) {
		BlockState state = block.defaultBlockState();
		if (state.canSurvive(level, pos)) {
			return state;
		}
		if (state.hasProperty(BlockStateProperties.HANGING)) {
			BlockState hanging = state.setValue(BlockStateProperties.HANGING, true);
			if (hanging.canSurvive(level, pos)) {
				return hanging;
			}
		}
		return null;
	}

	private static int freeSlotNear(ClusterBlockEntity cluster, int preferred) {
		if (cluster.get(preferred) == null) {
			return preferred;
		}
		// Neighbouring quarters first, the diagonal one last.
		for (int slot : new int[] {preferred ^ 1, preferred ^ 2, preferred ^ 3}) {
			if (cluster.get(slot) == null) {
				return slot;
			}
		}
		return -1;
	}

	/** @return true if vanilla should break the whole block */
	private static boolean breakOne(Level level, Player player, BlockPos pos, ClusterBlockEntity cluster) {
		if (cluster.count() <= 1) {
			return true;
		}
		int slot = targetedSlot(player, pos, cluster);
		BlockState removed = cluster.get(slot);
		cluster.set(slot, null);
		if (level instanceof ServerLevel serverLevel) {
			BlockEntityResync.schedule(serverLevel, pos);
		}
		if (removed != null) {
			if (!player.isCreative()) {
				Block.popResource(level, pos, dropFor(removed));
			}
			SoundType sound = removed.getSoundType();
			level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, sound.getVolume(), sound.getPitch());
		}
		if (cluster.count() == 1) {
			// Only one left: it goes back to the middle as a normal block.
			for (int i = 0; i < 4; i++) {
				BlockState last = cluster.get(i);
				if (last != null && last.canSurvive(level, pos)) {
					level.setBlock(pos, last, Block.UPDATE_ALL);
				}
			}
		}
		return false;
	}

	private static int targetedSlot(Player player, BlockPos pos, ClusterBlockEntity cluster) {
		HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
		if (hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos)) {
			// A little further along the view ray, so a hit on an edge counts for the part behind it.
			int slot = quarterAt(blockHit.getLocation().add(player.getLookAngle().scale(0.01)), pos);
			if (cluster.get(slot) != null) {
				return slot;
			}
		}
		for (int i = 0; i < 4; i++) {
			if (cluster.get(i) != null) {
				return i;
			}
		}
		return 0;
	}
}
