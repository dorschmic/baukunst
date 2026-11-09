package de.dorschmic.baukunst.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.dorschmic.baukunst.shapes.ShapedBlocks;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

/**
 * Item models, loot tables, tool tags and stonecutter recipes for the generated slabs and stairs.
 */
public class ShapedBlockDataProvider implements DataProvider {
	/** Vanilla block tags whose membership is copied from the source block. */
	private static final List<String> COPIED_TAGS = List.of(
			"mineable/pickaxe", "mineable/axe", "mineable/shovel", "mineable/hoe",
			"needs_stone_tool", "needs_iron_tool", "needs_diamond_tool");

	private final PackOutput output;
	private final PackOutput.PathProvider items;
	private final PackOutput.PathProvider loot;
	private final PackOutput.PathProvider recipes;

	public ShapedBlockDataProvider(FabricPackOutput output) {
		this.output = output;
		this.items = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
		this.loot = output.createPathProvider(PackOutput.Target.DATA_PACK, "loot_table/blocks");
		this.recipes = output.createPathProvider(PackOutput.Target.DATA_PACK, "recipe");
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cache) {
		List<CompletableFuture<?>> writes = new ArrayList<>();
		Map<String, Set<String>> tags = new LinkedHashMap<>();
		Map<String, Set<String>> resolved = new LinkedHashMap<>();

		for (ShapedBlocks.Shaped shaped : ShapedBlocks.all()) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(shaped.block());
			Identifier sourceId = BuiltInRegistries.BLOCK.getKey(shaped.source());
			boolean slab = shaped.kind() == ShapedBlocks.Kind.SLAB;

			JsonObject item = new JsonObject();
			item.add("model", itemModel(sourceId, slab ? "slab" : "stairs"));
			writes.add(DataProvider.saveStable(cache, item, items.json(id)));

			writes.add(DataProvider.saveStable(cache, slab ? slabLoot(id) : simpleLoot(id), loot.json(id)));

			JsonObject recipe = new JsonObject();
			recipe.addProperty("type", "minecraft:stonecutting");
			recipe.addProperty("ingredient", sourceId.toString());
			JsonObject result = new JsonObject();
			result.addProperty("count", slab ? 2 : 1);
			result.addProperty("id", id.toString());
			recipe.add("result", result);
			writes.add(DataProvider.saveStable(cache, recipe, recipes.json(id.withSuffix("_from_stonecutting"))));

			for (String tag : COPIED_TAGS) {
				if (resolved.computeIfAbsent(tag, t -> resolveTag("minecraft", t, new HashSet<>())).contains(sourceId.toString())) {
					tags.computeIfAbsent("block/" + tag, t -> new TreeSet<>()).add(id.toString());
				}
			}
			String kindTag = slab ? "slabs" : "stairs";
			tags.computeIfAbsent("block/" + kindTag, t -> new TreeSet<>()).add(id.toString());
			tags.computeIfAbsent("item/" + kindTag, t -> new TreeSet<>()).add(id.toString());
		}

		for (Map.Entry<String, Set<String>> tag : tags.entrySet()) {
			JsonObject json = new JsonObject();
			JsonArray values = new JsonArray();
			tag.getValue().forEach(values::add);
			json.add("values", values);
			writes.add(DataProvider.saveStable(cache, json, output.getOutputFolder(PackOutput.Target.DATA_PACK)
					.resolve("minecraft/tags/" + tag.getKey() + ".json")));
		}
		return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
	}

	private static JsonObject itemModel(Identifier sourceId, String shape) {
		JsonObject model = new JsonObject();
		model.addProperty("type", "baukunst:shaped_block");
		String modelId = sourceId.withPrefix("block/").toString();
		JsonObject vanilla = readJson("assets/" + sourceId.getNamespace() + "/items/" + sourceId.getPath() + ".json");
		if (vanilla != null && vanilla.getAsJsonObject("model") != null
				&& "minecraft:model".equals(vanilla.getAsJsonObject("model").get("type").getAsString())) {
			JsonObject m = vanilla.getAsJsonObject("model");
			modelId = m.get("model").getAsString();
			if (m.has("tints")) {
				model.add("tints", m.get("tints"));
			}
		}
		model.addProperty("model", modelId);
		model.addProperty("shape", shape);
		return model;
	}

	private static JsonObject simpleLoot(Identifier id) {
		JsonObject entry = new JsonObject();
		entry.addProperty("type", "minecraft:item");
		entry.addProperty("name", id.toString());
		JsonObject condition = new JsonObject();
		condition.addProperty("type", "minecraft:survives_explosion");
		return lootTable(id, entry, condition);
	}

	private static JsonObject slabLoot(Identifier id) {
		JsonObject entry = new JsonObject();
		entry.addProperty("type", "minecraft:item");
		entry.addProperty("name", id.toString());
		JsonArray modifiers = new JsonArray();
		JsonObject setCount = new JsonObject();
		setCount.addProperty("type", "minecraft:set_count");
		JsonObject match = new JsonObject();
		match.addProperty("type", "minecraft:match_block");
		match.addProperty("blocks", id.toString());
		JsonObject state = new JsonObject();
		state.addProperty("type", "double");
		match.add("state", state);
		setCount.add("condition", match);
		setCount.addProperty("count", 2);
		modifiers.add(setCount);
		JsonObject decay = new JsonObject();
		decay.addProperty("type", "minecraft:explosion_decay");
		modifiers.add(decay);
		entry.add("modifier", modifiers);
		return lootTable(id, entry, null);
	}

	private static JsonObject lootTable(Identifier id, JsonObject entry, JsonObject condition) {
		JsonObject pool = new JsonObject();
		if (condition != null) {
			pool.add("condition", condition);
		}
		JsonArray entries = new JsonArray();
		entries.add(entry);
		pool.add("entries", entries);
		pool.addProperty("rolls", 1);
		JsonArray pools = new JsonArray();
		pools.add(pool);
		JsonObject table = new JsonObject();
		table.addProperty("type", "minecraft:block");
		table.add("pools", pools);
		table.addProperty("random_sequence", id.withPrefix("blocks/").toString());
		return table;
	}

	/** Reads a vanilla block tag from the game jar, following nested #tag references. */
	private static Set<String> resolveTag(String namespace, String path, Set<String> visiting) {
		Set<String> result = new HashSet<>();
		if (!visiting.add(namespace + ":" + path)) {
			return result;
		}
		JsonObject json = readJson("data/" + namespace + "/tags/block/" + path + ".json");
		if (json == null) {
			return result;
		}
		for (JsonElement value : json.getAsJsonArray("values")) {
			String entry = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
			if (entry.startsWith("#")) {
				Identifier tag = Identifier.parse(entry.substring(1));
				result.addAll(resolveTag(tag.getNamespace(), tag.getPath(), visiting));
			} else {
				result.add(Identifier.parse(entry).toString());
			}
		}
		return result;
	}

	private static JsonObject readJson(String path) {
		try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				return null;
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public String getName() {
		return "Baukunst slabs and stairs";
	}
}
