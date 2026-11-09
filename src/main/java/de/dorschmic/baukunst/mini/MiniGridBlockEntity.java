package de.dorschmic.baukunst.mini;

import java.util.Arrays;
import de.dorschmic.baukunst.orientation.ContentOrientation;
import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Holds up to 8 mini blocks in a 2×2×2 grid.
 * Slot index = x + 2·y + 4·z (each 0 or 1). A slot stores the full-size source block.
 */
public class MiniGridBlockEntity extends BlockEntity implements RenderDataBlockEntity {
	private final @Nullable Block[] slots = new Block[8];

	public MiniGridBlockEntity(BlockPos pos, BlockState state) {
		super(MiniGrid.BLOCK_ENTITY, pos, state);
	}

	public static int slotIndex(int x, int y, int z) {
		return x | (y << 1) | (z << 2);
	}

	public @Nullable Block get(int slot) {
		return slots[slot];
	}

	public boolean isEmpty() {
		return Arrays.stream(slots).allMatch(b -> b == null);
	}

	/** Bit mask of occupied slots (used for the outline shape). */
	public int mask() {
		int mask = 0;
		for (int i = 0; i < 8; i++) {
			if (slots[i] != null) {
				mask |= 1 << i;
			}
		}
		return mask;
	}

	public void set(int slot, @Nullable Block source) {
		slots[slot] = source;
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("orientation", ContentOrientation.of(getBlockState()).encode());
		for (int i = 0; i < 8; i++) {
			if (slots[i] != null) {
				output.putString("slot" + i, BuiltInRegistries.BLOCK.getKey(slots[i]).toString());
			}
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		for (int i = 0; i < 8; i++) {
			slots[i] = input.getString("slot" + i)
					.map(Identifier::tryParse)
					.flatMap(BuiltInRegistries.BLOCK::getOptional)
					.orElse(null);
		}
		// Contents stored while the block was turned differently (e.g. pasted rotated by WorldEdit): turn them along.
		ContentOrientation stored = input.getInt("orientation").map(ContentOrientation::decode)
				.orElse(ContentOrientation.of(getBlockState()));
		remap(stored, ContentOrientation.of(getBlockState()));
		if (level != null && level.isClientSide()) {
			// New contents arrived from the server: rebuild the chunk mesh.
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
		ContentOrientation.remap(slots, from, to, MiniGridBlockEntity::slotIndex, b -> b, b -> b);
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
