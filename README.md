# Baukunst

Eine persönliche Minecraft-Mod (Fabric, Minecraft **26.3**) mit kleinen Helfern zum **schön Bauen**.

![Vertikale Stufen](docs/vertical_slabs.png)

## Features

### Vertikale Stufen (Slabs)
Alle Stufen aus Vanilla-Minecraft (101 Stück – Holz, Stein, Ziegel, Kupfer, …) lassen sich auch
**seitlich** platzieren – also in die linke/rechte bzw. vordere/hintere Hälfte eines Blocks statt nur oben oder unten.

- **`V`** drücken schaltet den Modus „Vertikale Stufen“ an/aus (Taste in *Optionen → Steuerung → Baukunst* änderbar).
- **Seite eines Blocks anklicken:** Die Stufe legt sich an diesen Block an.
- **Ober-/Unterseite anklicken:** Die Stufe landet in der Hälfte, auf die du zielst (nahe am Rand zielen).
- Zwei gleiche vertikale Stufen ergeben zusammen einen ganzen Block – wie bei normalen Stufen.
- Hitbox, Kollision, Wasser­logging und Drops funktionieren wie bei normalen Stufen.

> Hinweis: Die Mod muss auf Client **und** Server installiert sein. Entfernt man sie, werden vertikale Stufen wieder zu normalen Stufen.

## Installation
1. [Fabric Loader](https://fabricmc.net/use/) für Minecraft 26.3 installieren.
2. [Fabric API](https://modrinth.com/mod/fabric-api) in den `mods`-Ordner legen.
3. `baukunst-<version>.jar` (aus `build/libs/`) in den `mods`-Ordner legen.

## Entwickeln
Voraussetzung: Java 25.

```bash
./gradlew build              # baut build/libs/baukunst-*.jar
./gradlew runClient          # startet Minecraft mit der Mod
./gradlew runClientGameTest  # automatischer Ingame-Test (inkl. Screenshot)
```

Die Blockstate-Dateien für die Stufen werden aus den Vanilla-Daten erzeugt:

```bash
python3 scripts/generate_slab_blockstates.py
```

Nach einem Minecraft-Update dieses Skript erneut ausführen.

## Lizenz
MIT
