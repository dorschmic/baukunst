package de.dorschmic.baukunst.client.shapes;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Item model type {@code baukunst:shaped_block}: takes a full-cube block model and cuts it
 * into a slab or stair shape, so slab/stair items need no hand-made models.
 */
public final class ShapedItemModel implements ItemModel {
	private final List<ItemTintSource> tints;
	private final ItemQuads quads;
	private final Supplier<Vector3fc[]> extents;
	private final ModelRenderProperties properties;
	private final Matrix4fc transformation;

	private ShapedItemModel(List<ItemTintSource> tints, List<BakedQuad> quads, ModelRenderProperties properties, Matrix4fc transformation) {
		this.tints = tints;
		this.quads = ItemQuads.split(quads);
		this.extents = Suppliers.memoize(() -> CuboidItemModelWrapper.computeExtents(quads));
		this.properties = properties;
		this.transformation = transformation;
	}

	@Override
	public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext displayContext,
			@Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
		output.appendModelIdentityElement(this);
		ItemStackRenderState.LayerRenderState layer = output.newLayer();
		if (item.hasFoil()) {
			layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
			output.setAnimated();
			output.appendModelIdentityElement(ItemStackRenderState.FoilType.STANDARD);
		}
		if (!tints.isEmpty()) {
			IntList tintLayers = layer.tintLayers();
			for (ItemTintSource tintSource : tints) {
				int tint = tintSource.calculate(item, level, owner == null ? null : owner.asLivingEntity());
				tintLayers.add(tint);
				output.appendModelIdentityElement(tint);
			}
		}
		layer.setExtents(extents);
		layer.setLocalTransform(transformation);
		properties.applyToLayer(layer, displayContext);
		layer.setQuads(quads);
	}

	public enum Shape {
		SLAB(List.of(new AABB(0, 0, 0, 1, 0.5, 1))),
		// Same look as the vanilla stairs item.
		STAIRS(List.of(new AABB(0, 0, 0, 1, 0.5, 1), new AABB(0.5, 0.5, 0, 1, 1, 1)));

		final List<AABB> boxes;

		Shape(List<AABB> boxes) {
			this.boxes = boxes;
		}

		static final Codec<Shape> CODEC = Codec.STRING.xmap(s -> Shape.valueOf(s.toUpperCase()), s -> s.name().toLowerCase());
	}

	public record Unbaked(Identifier model, Shape shape, List<ItemTintSource> tints) implements ItemModel.Unbaked {
		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Identifier.CODEC.fieldOf("model").forGetter(Unbaked::model),
				Shape.CODEC.fieldOf("shape").forGetter(Unbaked::shape),
				ItemTintSources.CODEC.listOf().optionalFieldOf("tints", List.of()).forGetter(Unbaked::tints)
		).apply(i, Unbaked::new));

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			resolver.markDependency(model);
		}

		@Override
		public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
			ModelBaker baker = context.blockModelBaker();
			ResolvedModel resolved = baker.getModel(model);
			TextureSlots slots = resolved.getTopTextureSlots();
			QuadCollection source = resolved.bakeTopGeometry(slots, baker, BlockModelRotation.IDENTITY);
			ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, resolved, slots);

			List<BakedQuad> out = new ArrayList<>();
			MutableMesh mesh = Renderer.get().mutableMesh();
			QuadEmitter emitter = mesh.emitter();
			for (AABB box : shape.boxes) {
				for (BakedQuad quad : source.getAll()) {
					emitter.fromBakedQuad(quad);
					if (QuadClipper.clip(emitter, box)) {
						out.add(emitter.toBakedQuad(quad.materialInfo().sprite()));
					}
					emitter.clear();
				}
			}
			return new ShapedItemModel(tints, out, properties, transformation);
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
