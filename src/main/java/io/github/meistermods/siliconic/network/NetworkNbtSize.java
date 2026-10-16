package io.github.meistermods.siliconic.network;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/** Accounts for the Forge 1.20.1 NBT reader without serializing or allocating another tag tree. */
public final class NetworkNbtSize {
  public static final long LIMIT = 2_097_152;

  /** Returns exact reader accounting, or LIMIT + 1 when the tag cannot round trip losslessly. */
  public static long accountedSize(@Nullable CompoundTag tag) {
    if (tag == null) return 0;
    Account account = new Account();
    // The unnamed root has its type byte, an empty modified-UTF name, and Forge's extra four bytes.
    if (!account.add(7) || !account.visit(tag, 0)) return LIMIT + 1;
    return account.used;
  }

  public static boolean fits(@Nullable CompoundTag tag) {
    return accountedSize(tag) <= LIMIT;
  }

  private static final class Account {
    private long used;

    private boolean add(long bytes) {
      used += bytes;
      return used <= LIMIT;
    }

    private boolean utf(String value) {
      int bytes = 0;
      for (int index = 0; index < value.length(); index++) {
        char character = value.charAt(index);
        bytes += character >= 1 && character <= 127 ? 1 : character <= 2047 ? 2 : 3;
        if (bytes > 65_535) return false;
      }
      return add(2L + bytes);
    }

    private boolean visit(Tag tag, int depth) {
      return switch (tag.getId()) {
        case Tag.TAG_BYTE -> add(9);
        case Tag.TAG_SHORT -> add(10);
        case Tag.TAG_INT, Tag.TAG_FLOAT -> add(12);
        case Tag.TAG_LONG, Tag.TAG_DOUBLE -> add(16);
        case Tag.TAG_BYTE_ARRAY -> add(24L + ((ByteArrayTag) tag).size());
        case Tag.TAG_INT_ARRAY -> add(24L + 4L * ((IntArrayTag) tag).size());
        case Tag.TAG_LONG_ARRAY -> add(24L + 8L * ((LongArrayTag) tag).size());
        case Tag.TAG_STRING -> add(36) && utf(tag.getAsString());
        case Tag.TAG_LIST -> visitList((ListTag) tag, depth);
        case Tag.TAG_COMPOUND -> visitCompound((CompoundTag) tag, depth);
        default -> false;
      };
    }

    private boolean visitList(ListTag list, int depth) {
      if (depth > Tag.MAX_DEPTH || !add(37L + 4L * list.size())) return false;
      for (Tag value : list) if (!visit(value, depth + 1)) return false;
      return true;
    }

    private boolean visitCompound(CompoundTag compound, int depth) {
      if (depth > Tag.MAX_DEPTH || !add(48)) return false;
      for (String key : compound.getAllKeys()) {
        // Tag type, modified-UTF key, key object, Forge entry overhead, value, and map entry.
        if (!add(2)
            || !utf(key)
            || !add(32L + 2L * key.length())
            || !visit(compound.get(key), depth + 1)
            || !add(36)) return false;
      }
      return add(2);
    }
  }

  private NetworkNbtSize() {}
}
