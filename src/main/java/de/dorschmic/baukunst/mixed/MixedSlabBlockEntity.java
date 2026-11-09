package de.dorschmic.baukunst.mixed;

import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The two slabs of a mixed slab block. {@code first} sits on the side the block state's {@code facing}
 * points to, {@code second} on the opposite side – so rotating the state (WorldEdit, structures) is all
 * it takes to turn the block.
 */
public class MixedSlabBlockEntity extends BlockEntity implements RenderDataBlockEntity {
	private Block first = Blocks.OAK_SLAB;
	private Block second = Blocks.STONE_SLAB;

	public MixedSlabBlockEntity(BlockPos pos, BlockState state) {
		super(MixedSlabs.BLOCK_ENTITY, pos, state);
	}

	public Block first() {
		return first;
	}

	public Block second() {
		return second;
	}

	public void set(Block first, Block second) {
		this.first = first;
		this.second = second;
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	/** Slab state of the first half (on the facing side). */
	public BlockState firstState() {
		Direction side = getBlockState().getValue(MixedSlabBlock.FACING);
		return MixedSlabs.halfState(first, side);
	}

	public BlockState secondState() {
		Direction side = getBlockState().getValue(MixedSlabBlock.FACING).getOpposite();
		return MixedSlabs.halfState(second, side);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("first", BuiltInRegistries.BLOCK.getKey(first).toString());
		output.putString("second", BuiltInRegistries.BLOCK.getKey(second).toString());
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		first = read(input, "first", first);
		second = read(input, "second", second);
		if (level != null && level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	private static Block read(ValueInput input, String key, Block fallback) {
		return input.getString(key).map(Identifier::tryParse).flatMap(BuiltInRegistries.BLOCK::getOptional)
				.filter(MixedSlabs::isSlab).orElse(fallback);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public Object getRenderData() {
		return new Block[] {first, second};
	}
}
