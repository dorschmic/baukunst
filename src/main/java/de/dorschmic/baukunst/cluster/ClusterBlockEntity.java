package de.dorschmic.baukunst.cluster;

import de.dorschmic.baukunst.orientation.ContentOrientation;
import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Up to four small vanilla blocks (flowers, lanterns, chains …), one per quarter of the block space.
 * Quarter index = x + 2·z (each 0 or 1).
 */
public class ClusterBlockEntity extends BlockEntity implements RenderDataBlockEntity {
	private final @Nullable BlockState[] slots = new BlockState[4];

	public ClusterBlockEntity(BlockPos pos, BlockState state) {
		super(Clusters.BLOCK_ENTITY, pos, state);
	}

	public @Nullable BlockState get(int slot) {
		return slots[slot];
	}

	public int count() {
		int n = 0;
		for (BlockState s : slots) {
			if (s != null) {
				n++;
			}
		}
		return n;
	}

	public void set(int slot, @Nullable BlockState content) {
		slots[slot] = content;
		setChanged();
		if (level != null) {
			int light = 0;
			for (BlockState s : slots) {
				if (s != null) {
					light = Math.max(light, s.getLightEmission());
				}
			}
			BlockState state = getBlockState();
			if (!level.isClientSide() && state.getValue(ClusterBlock.LIGHT) != light) {
				level.setBlock(worldPosition, state.setValue(ClusterBlock.LIGHT, light), Block.UPDATE_ALL);
			}
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("orientation", ContentOrientation.of(getBlockState()).encode());
		for (int i = 0; i < 4; i++) {
			if (slots[i] != null) {
				output.store("slot" + i, BlockState.CODEC, slots[i]);
			}
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		for (int i = 0; i < 4; i++) {
			slots[i] = input.read("slot" + i, BlockState.CODEC).orElse(null);
		}
		// Contents stored while the block was turned differently (e.g. pasted rotated by WorldEdit): turn them along.
		ContentOrientation stored = input.getInt("orientation").map(ContentOrientation::decode)
				.orElse(ContentOrientation.of(getBlockState()));
		remap(stored, ContentOrientation.of(getBlockState()));
		if (level != null && level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	@Override
	public void setBlockState(BlockState state) {
		ContentOrientation before = ContentOrientation.of(getBlockState());
		super.setBlockState(state);
		ContentOrientation after = ContentOrientation.of(state);
		if (!before.equals(after)) {
			remap(before, after);
			setChanged();
		}
	}

	private void remap(ContentOrientation from, ContentOrientation to) {
		ContentOrientation.remap(slots, from, to, (x, y, z) -> y == 0 ? x | (z << 1) : -1, from::undo, to::apply);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public Object getRenderData() {
		return slots.clone();
	}
}
