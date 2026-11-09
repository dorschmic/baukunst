package de.dorschmic.baukunst.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.dorschmic.baukunst.mini.MiniBlock;
import de.dorschmic.baukunst.mini.MiniBlocks;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

/**
 * Generates, for every mini block:
 * <ul>
 *   <li>an item model definition (the source block's item model at half size)</li>
 *   <li>a stonecutter recipe: 1 block → 4 mini blocks</li>
 * </ul>
 */
public class MiniBlockDataProvider implements DataProvider {
	private final PackOutput.PathProvider items;
	private final PackOutput.PathProvider recipes;

	public MiniBlockDataProvider(FabricPackOutput output) {
		this.items = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
		this.recipes = output.createPathProvider(PackOutput.Target.DATA_PACK, "recipe");
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cache) {
		List<CompletableFuture<?>> writes = new ArrayList<>();
		for (MiniBlock mini : MiniBlocks.all()) {
			Identifier miniId = BuiltInRegistries.BLOCK.getKey(mini);
			Identifier sourceId = BuiltInRegistries.BLOCK.getKey(mini.source());

			JsonObject item = new JsonObject();
			item.add("model", miniItemModel(sourceId));
			writes.add(DataProvider.saveStable(cache, item, items.json(miniId)));

			JsonObject recipe = new JsonObject();
			recipe.addProperty("type", "minecraft:stonecutting");
			recipe.addProperty("ingredient", sourceId.toString());
			JsonObject result = new JsonObject();
			result.addProperty("count", 4);
			result.addProperty("id", miniId.toString());
			recipe.add("result", result);
			writes.add(DataProvider.saveStable(cache, recipe,
					recipes.json(miniId.withSuffix("_from_stonecutting"))));
		}
		return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
	}

	/** The vanilla item model of the source block (keeps tints), shrunk to half size. */
	private static JsonObject miniItemModel(Identifier sourceId) {
		JsonObject model = null;
		String path = "assets/" + sourceId.getNamespace() + "/items/" + sourceId.getPath() + ".json";
		try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
			if (in != null) {
				JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
				JsonObject vanilla = json.getAsJsonObject().getAsJsonObject("model");
				if (vanilla != null && "minecraft:model".equals(vanilla.get("type").getAsString())) {
					model = vanilla.deepCopy();
				}
			}
		} catch (Exception ignored) {
			// fall through to default
		}
		if (model == null) {
			model = new JsonObject();
			model.addProperty("type", "minecraft:model");
			model.addProperty("model", sourceId.withPrefix("block/").toString());
		}

		JsonObject transformation = new JsonObject();
		transformation.add("translation", vec(0.25F, 0.25F, 0.25F));
		transformation.add("left_rotation", vec(0, 0, 0, 1));
		transformation.add("scale", vec(0.5F, 0.5F, 0.5F));
		transformation.add("right_rotation", vec(0, 0, 0, 1));
		model.add("transformation", transformation);
		return model;
	}

	private static JsonArray vec(float... values) {
		JsonArray array = new JsonArray();
		for (float v : values) {
			array.add(v);
		}
		return array;
	}

	@Override
	public String getName() {
		return "Baukunst mini blocks";
	}
}
