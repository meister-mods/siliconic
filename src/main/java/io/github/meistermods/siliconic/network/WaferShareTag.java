package io.github.meistermods.siliconic.network;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.jetbrains.annotations.Nullable;

/** Lossless, bounded item synchronization; persistent and client-side item tags stay unchanged. */
public final class WaferShareTag {
  public static final String MARKER = "siliconic:compressed_wafer";
  public static final int VERSION = 1;
  private static final String PAYLOAD = "Payload";
  private static final int MIN_ACCOUNTED_BYTES = 8_192;
  private static final int MAX_BYTES = (int) NetworkNbtSize.LIMIT;

  @Nullable
  public static CompoundTag encode(@Nullable CompoundTag source) {
    // This exact two-key shape is reserved for the wire format. Forge can pass a previous
    // getShareTag result back through setTag and getShareTag, so envelopes must be idempotent.
    if (source == null || isEnvelopeShape(source)) return source;
    long accounted = NetworkNbtSize.accountedSize(source);
    if (accounted < MIN_ACCOUNTED_BYTES || accounted > NetworkNbtSize.LIMIT) return source;
    try {
      ByteArrayOutputStream compressed = new ByteArrayOutputStream();
      CountingOutputStream counted =
          new CountingOutputStream(new BufferedOutputStream(new GZIPOutputStream(compressed)));
      try (DataOutputStream output = new DataOutputStream(counted)) {
        output.writeByte(Tag.TAG_COMPOUND);
        output.writeUTF("");
        writeCanonical(source, output);
      }
      byte[] payload = compressed.toByteArray();
      // The allowance exceeds all fixed NBT envelope bytes, avoiding expansion for opaque data.
      if (payload.length + 128 >= counted.count) return source;
      CompoundTag envelope = new CompoundTag();
      envelope.putInt(MARKER, VERSION);
      envelope.putByteArray(PAYLOAD, payload);
      return NetworkNbtSize.fits(envelope) ? envelope : source;
    } catch (IOException exception) {
      // Unsupported exact-equality cases and ordinary raw tags retain vanilla behavior.
      return source;
    }
  }

  @Nullable
  public static CompoundTag decode(@Nullable CompoundTag received) {
    // Full tags sent with writeItemStack(..., false), including arbitrary extra custom keys,
    // remain full tags. Only the exact reserved wire shape is interpreted as compression.
    if (received == null || !isEnvelopeShape(received)) return received;
    if (!received.contains(MARKER, Tag.TAG_INT)
        || received.getInt(MARKER) != VERSION
        || !received.contains(PAYLOAD, Tag.TAG_BYTE_ARRAY))
      throw new IllegalArgumentException("Invalid compressed wafer format");
    byte[] payload = received.getByteArray(PAYLOAD);
    if (payload.length == 0 || payload.length > MAX_BYTES)
      throw new IllegalArgumentException("Invalid compressed wafer length");
    ByteArrayInputStream compressedInput = new ByteArrayInputStream(payload);
    // The wire encoder emits exactly one gzip member. The JDK reader recursively accepts
    // concatenated empty members; the bundled single-member reader leaves trailing bytes here.
    try (DataInputStream input =
        new DataInputStream(
            new BoundedInputStream(
                new GzipCompressorInputStream(compressedInput, false), MAX_BYTES))) {
      CompoundTag decoded = NbtIo.read(input, new NbtAccounter(NetworkNbtSize.LIMIT));
      if (decoded == null || input.read() != -1 || compressedInput.available() != 0)
        throw new IOException("Trailing or empty wafer NBT");
      return decoded;
    } catch (IOException exception) {
      throw new IllegalArgumentException("Invalid compressed wafer payload", exception);
    }
  }

  private static boolean isEnvelopeShape(CompoundTag tag) {
    return tag.size() == 2 && tag.contains(MARKER) && tag.contains(PAYLOAD);
  }

  private static void writeCanonical(Tag tag, DataOutputStream output) throws IOException {
    if (tag instanceof CompoundTag compound) {
      // CompoundTag equality ignores key order; compressed share tags must do the same for
      // Forge's strict NBT ingredients and persisted recipe signatures.
      String[] keys = compound.getAllKeys().toArray(String[]::new);
      Arrays.sort(keys);
      for (String key : keys) {
        Tag value = compound.get(key);
        output.writeByte(value.getId());
        output.writeUTF(key);
        writeCanonical(value, output);
      }
      output.writeByte(Tag.TAG_END);
    } else if (tag instanceof ListTag list) {
      // Vanilla normalizes an empty list's stored element type when writing it. Do so without
      // mutating the original list, which may have come from a legacy typed-empty encoding.
      output.writeByte(list.isEmpty() ? Tag.TAG_END : list.get(0).getId());
      output.writeInt(list.size());
      for (Tag value : list) writeCanonical(value, output);
    } else if ((tag instanceof FloatTag floatValue && Float.isNaN(floatValue.getAsFloat()))
        || (tag instanceof DoubleTag doubleValue && Double.isNaN(doubleValue.getAsDouble()))) {
      // Separate NaN tags are not equal in vanilla, even with identical bits. Encoding them as
      // equal byte arrays would change strict ingredient behavior; leave such custom tags raw.
      throw new IOException("NaN tags require vanilla synchronization");
    } else {
      tag.write(output);
    }
  }

  private static final class CountingOutputStream extends FilterOutputStream {
    private int count;

    private CountingOutputStream(OutputStream output) {
      super(output);
    }

    @Override
    public void write(int value) throws IOException {
      out.write(value);
      count++;
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
      out.write(bytes, offset, length);
      count += length;
    }
  }

  private static final class BoundedInputStream extends FilterInputStream {
    private int remaining;

    private BoundedInputStream(InputStream source, int remaining) {
      super(source);
      this.remaining = remaining;
    }

    @Override
    public int read() throws IOException {
      if (remaining <= 0) throw new IOException("Decoded wafer byte limit exceeded");
      int value = in.read();
      if (value >= 0) remaining--;
      return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      if (length == 0) return 0;
      if (remaining <= 0) throw new IOException("Decoded wafer byte limit exceeded");
      int count = in.read(bytes, offset, Math.min(length, remaining));
      if (count > 0) remaining -= count;
      return count;
    }

    @Override
    public long skip(long count) throws IOException {
      long skipped = in.skip(Math.min(Math.max(0, count), remaining));
      remaining -= (int) skipped;
      return skipped;
    }
  }

  private WaferShareTag() {}
}
