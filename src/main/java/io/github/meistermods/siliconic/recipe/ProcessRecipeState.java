package io.github.meistermods.siliconic.recipe;

import com.google.common.collect.MapMaker;
import io.github.meistermods.siliconic.reprocessing.ReprocessingProcess;
import io.netty.buffer.Unpooled;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;

/** Remembers the definition that paid for unfinished work across recipe and world reloads. */
public final class ProcessRecipeState {
  private static final String SAVE_KEY = "ProcessRecipeSignature";
  private static final byte[] UNKNOWN = new byte[0];
  private static final Map<Recipe<?>, byte[]> SIGNATURES = new MapMaker().weakKeys().makeMap();

  @Nullable private Recipe<?> recipe;
  private byte[] signature = UNKNOWN;

  /** A new object with the same definition can continue existing work; a changed one cannot. */
  public boolean select(Recipe<?> selected) {
    if (recipe == selected) return true;
    byte[] next = SIGNATURES.computeIfAbsent(selected, ProcessRecipeState::signatureOf);
    boolean compatible = signature.length == 0 || MessageDigest.isEqual(signature, next);
    recipe = selected;
    signature = next;
    return compatible;
  }

  public void save(CompoundTag tag, boolean unfinished) {
    if (unfinished && signature.length != 0) tag.putByteArray(SAVE_KEY, signature.clone());
    else tag.remove(SAVE_KEY);
  }

  public void load(CompoundTag tag) {
    byte[] saved = tag.getByteArray(SAVE_KEY);
    signature = saved.length == 32 ? saved.clone() : UNKNOWN;
    recipe = null;
  }

  private static byte[] signatureOf(Recipe<?> recipe) {
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      buffer.writeResourceLocation(recipe.getId());
      if (recipe instanceof MachineProcess process) {
        buffer.writeBoolean(true);
        new MachineProcess.Serializer().toNetwork(buffer, process);
      } else if (recipe instanceof ReprocessingProcess process) {
        buffer.writeBoolean(false);
        new ReprocessingProcess.Serializer().toNetwork(buffer, process);
      } else {
        throw new IllegalArgumentException("Unsupported process recipe: " + recipe.getId());
      }
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(buffer.nioBuffer());
      return digest.digest();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    } finally {
      buffer.release();
    }
  }
}
