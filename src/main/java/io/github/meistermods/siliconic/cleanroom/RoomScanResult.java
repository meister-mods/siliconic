package io.github.meistermods.siliconic.cleanroom;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

@SuppressWarnings({"null", "deprecated"})
public final class RoomScanResult {
  public enum Status {
    NOT_SCANNED,
    SEALED,
    OPENABLE_OPEN,
    LIMIT_REACHED,
    UNLOADED,
    WORLD_OPEN,
    NO_INTERIOR
  }

  public record OpenableStats(int total, int open) {
    public OpenableStats {
      total = Math.max(0, total);
      open = Math.max(0, Math.min(open, total));
    }
  }

  private final Status status;
  private final int volume;
  private final int scannedPositions;
  private final Map<ResourceLocation, Integer> surfaceMaterials;
  private final Map<ResourceLocation, OpenableStats> openables;

  public RoomScanResult(
      Status status,
      int volume,
      int scannedPositions,
      Map<ResourceLocation, Integer> surfaceMaterials,
      Map<ResourceLocation, OpenableStats> openables) {
    this.status = status;
    this.volume = Math.max(0, volume);
    this.scannedPositions = Math.max(0, scannedPositions);
    this.surfaceMaterials = immutableCopy(surfaceMaterials);
    this.openables = immutableCopy(openables);
  }

  public static RoomScanResult notScanned() {
    return new RoomScanResult(Status.NOT_SCANNED, 0, 0, Map.of(), Map.of());
  }

  private static <T> Map<ResourceLocation, T> immutableCopy(Map<ResourceLocation, T> source) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(source));
  }

  public Status status() {
    return status;
  }

  public boolean isSealed() {
    return status == Status.SEALED;
  }

  public int volume() {
    return volume;
  }

  public int scannedPositions() {
    return scannedPositions;
  }

  public Map<ResourceLocation, Integer> surfaceMaterials() {
    return surfaceMaterials;
  }

  public Map<ResourceLocation, OpenableStats> openables() {
    return openables;
  }

  public int openableCount() {
    return openables.values().stream().mapToInt(OpenableStats::total).sum();
  }

  public int openOpenableCount() {
    return openables.values().stream().mapToInt(OpenableStats::open).sum();
  }

  public CompoundTag save() {
    CompoundTag tag = new CompoundTag();
    tag.putString("Status", status.name());
    tag.putInt("Volume", volume);
    tag.putInt("ScannedPositions", scannedPositions);
    ListTag surfaces = new ListTag();
    surfaceMaterials.forEach(
        (id, faces) -> {
          CompoundTag entry = new CompoundTag();
          entry.putString("Block", id.toString());
          entry.putInt("Faces", faces);
          surfaces.add(entry);
        });
    tag.put("SurfaceMaterials", surfaces);
    ListTag closures = new ListTag();
    openables.forEach(
        (id, stats) -> {
          CompoundTag entry = new CompoundTag();
          entry.putString("Block", id.toString());
          entry.putInt("Total", stats.total());
          entry.putInt("Open", stats.open());
          closures.add(entry);
        });
    tag.put("Openables", closures);
    return tag;
  }

  public static RoomScanResult load(CompoundTag tag) {
    Status status;
    try {
      status = Status.valueOf(tag.getString("Status"));
    } catch (IllegalArgumentException ignored) {
      status = Status.NOT_SCANNED;
    }
    Map<ResourceLocation, Integer> surfaces = new LinkedHashMap<>();
    ListTag surfaceList = tag.getList("SurfaceMaterials", Tag.TAG_COMPOUND);
    for (int i = 0; i < surfaceList.size(); i++) {
      CompoundTag entry = surfaceList.getCompound(i);
      ResourceLocation id = ResourceLocation.tryParse(entry.getString("Block"));
      if (id != null) surfaces.put(id, Math.max(0, entry.getInt("Faces")));
    }
    Map<ResourceLocation, OpenableStats> openables = new LinkedHashMap<>();
    ListTag openableList = tag.getList("Openables", Tag.TAG_COMPOUND);
    for (int i = 0; i < openableList.size(); i++) {
      CompoundTag entry = openableList.getCompound(i);
      ResourceLocation id = ResourceLocation.tryParse(entry.getString("Block"));
      if (id != null)
        openables.put(
            id, new OpenableStats(entry.getInt("Total"), entry.getInt("Open")));
    }
    return new RoomScanResult(
        status,
        tag.getInt("Volume"),
        tag.getInt("ScannedPositions"),
        surfaces,
        openables);
  }
}
