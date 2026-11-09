#!/usr/bin/env python3
"""Generate blockstate overrides that add vertical variants to every vanilla slab.

Reads the vanilla slab blockstates from a Minecraft client jar and writes
overrides to src/main/resources/assets/minecraft/blockstates/. The vertical
variants reuse the vanilla bottom-slab model, rotated onto its side (uvlock
keeps the textures aligned), so no extra models are needed.

Usage:
    python3 scripts/generate_slab_blockstates.py [path/to/minecraft-client.jar]

Without an argument the jar from the Gradle/Loom cache is used
(run ./gradlew build once first).
"""
import glob
import json
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/minecraft/blockstates")

# Rotating the bottom-slab model by x=90 moves it to the south half;
# the y rotation then turns it to the wanted side.
VERTICAL_Y = {"south": 0, "west": 90, "north": 180, "east": 270}


def find_jar():
    if len(sys.argv) > 1:
        return sys.argv[1]
    props = dict(
        line.strip().split("=", 1)
        for line in open(os.path.join(ROOT, "gradle.properties"))
        if "=" in line and not line.startswith("#")
    )
    version = props["minecraft_version"]
    hits = glob.glob(os.path.expanduser(f"~/.gradle/caches/fabric-loom/{version}/minecraft-client.jar"))
    if not hits:
        sys.exit("Minecraft client jar not found – run ./gradlew build first or pass the jar path.")
    return hits[0]


def main():
    jar = zipfile.ZipFile(find_jar())
    prefix = "assets/minecraft/blockstates/"
    names = sorted(n for n in jar.namelist() if n.startswith(prefix) and n.endswith("_slab.json"))
    os.makedirs(OUT, exist_ok=True)
    for old in glob.glob(os.path.join(OUT, "*_slab.json")):
        os.remove(old)

    for name in names:
        data = json.loads(jar.read(name))
        variants = data.get("variants", {})
        if set(variants) != {"type=bottom", "type=top", "type=double"}:
            print(f"skip {name}: unexpected format", file=sys.stderr)
            continue
        out = {}
        # "down" and "up" both mean a normal horizontal slab (see VerticalSlabs.VERTICAL).
        for vertical in ["down", "up", "north", "east", "south", "west"]:
            for slab_type in ["bottom", "top", "double"]:
                key = f"type={slab_type},vertical={vertical}"
                if vertical in ("down", "up") or slab_type == "double":
                    out[key] = variants[f"type={slab_type}"]
                else:
                    base = variants["type=bottom"]
                    out[key] = {"model": base["model"], "uvlock": True, "x": 90}
                    if VERTICAL_Y[vertical]:
                        out[key]["y"] = VERTICAL_Y[vertical]
        with open(os.path.join(OUT, os.path.basename(name)), "w") as f:
            json.dump({"variants": out}, f, indent=2)
            f.write("\n")
    print(f"wrote {len(names)} slab blockstates to {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
