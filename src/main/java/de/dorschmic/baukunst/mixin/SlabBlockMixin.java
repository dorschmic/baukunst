package de.dorschmic.baukunst.mixin;

import de.dorschmic.baukunst.slab.VerticalSlabFacing;
import de.dorschmic.baukunst.slab.VerticalSlabMode;
import de.dorschmic.baukunst.slab.VerticalSlabs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives every slab an extra {@code vertical} block state property so it can be
 * placed against the side of a block (north/east/south/west half) instead of
 * only the bottom or top half.
 */
@Mixin(SlabBlock.class)
public abstract class SlabBlockMixin extends Block {
	private SlabBlockMixin(Properties properties) {
		super(properties);
	}

	@Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
	private void baukunst$addVerticalProperty(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
		builder.add(VerticalSlabs.VERTICAL);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void baukunst$defaultNotVertical(Properties properties, CallbackInfo ci) {
		this.registerDefaultState(this.defaultBlockState().setValue(VerticalSlabs.VERTICAL, VerticalSlabFacing.NONE));
	}

	@Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
	private void baukunst$verticalShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
			CallbackInfoReturnable<VoxelShape> cir) {
		VerticalSlabFacing facing = state.getValue(VerticalSlabs.VERTICAL);
		if (facing != VerticalSlabFacing.NONE && state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE) {
			cir.setReturnValue(VerticalSlabs.shape(facing));
		}
	}

	@Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
	private void baukunst$placeVertically(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
		BlockState result = cir.getReturnValue();
		if (result == null || !result.hasProperty(VerticalSlabs.VERTICAL)) {
			return;
		}
		if (result.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
			// Two halves merged into a full block – it is no longer vertical.
			cir.setReturnValue(result.setValue(VerticalSlabs.VERTICAL, VerticalSlabFacing.NONE));
			return;
		}
		Player player = context.getPlayer();
		if (player == null || !VerticalSlabMode.isEnabled(player.getUUID())) {
			return;
		}
		BlockPos pos = context.getClickedPos();
		Vec3 hit = context.getClickLocation();
		VerticalSlabFacing facing = VerticalSlabs.facingFor(context.getClickedFace(),
				hit.x - pos.getX(), hit.z - pos.getZ(), context.getHorizontalDirection());
		cir.setReturnValue(result.setValue(SlabBlock.TYPE, SlabType.BOTTOM).setValue(VerticalSlabs.VERTICAL, facing));
	}

	@Inject(method = "canBeReplaced", at = @At("HEAD"), cancellable = true)
	private void baukunst$canMergeVertical(BlockState state, BlockPlaceContext context, CallbackInfoReturnable<Boolean> cir) {
		VerticalSlabFacing facing = state.getValue(VerticalSlabs.VERTICAL);
		if (facing == VerticalSlabFacing.NONE || state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
			return;
		}
		if (!context.getItemInHand().is(this.asItem())) {
			cir.setReturnValue(false);
			return;
		}
		if (!context.replacingClickedOnBlock()) {
			cir.setReturnValue(true);
			return;
		}
		Direction half = facing.direction();
		Direction clickedFace = context.getClickedFace();
		if (clickedFace == half.getOpposite()) {
			// Clicked the open inner face of the slab.
			cir.setReturnValue(true);
			return;
		}
		// Clicked another face – merge only if the click was on the empty half.
		BlockPos pos = context.getClickedPos();
		Vec3 offset = context.getClickLocation().subtract(Vec3.atCenterOf(pos));
		double along = offset.x * half.getStepX() + offset.z * half.getStepZ();
		cir.setReturnValue(clickedFace.getAxis() != half.getAxis() && along < 0);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		Direction dir = state.getValue(VerticalSlabs.VERTICAL).direction();
		return dir == null ? state : state.setValue(VerticalSlabs.VERTICAL, VerticalSlabFacing.of(rotation.rotate(dir)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		Direction dir = state.getValue(VerticalSlabs.VERTICAL).direction();
		return dir == null ? state : state.setValue(VerticalSlabs.VERTICAL, VerticalSlabFacing.of(mirror.mirror(dir)));
	}
}
