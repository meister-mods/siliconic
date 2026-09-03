package io.github.meistermods.siliconic.network;

public final class MenuDataSync {
  public static int low(int value) {
    return value & 0xffff;
  }

  public static int high(int value) {
    return (value >>> 16) & 0xffff;
  }

  public static int combine(int low, int high) {
    return (low & 0xffff) | ((high & 0xffff) << 16);
  }

  /** Scales a non-negative value without overflowing and clamps it to the target range. */
  public static int scale(int value, int maximum, int targetMaximum) {
    if (value <= 0 || maximum <= 0 || targetMaximum <= 0) return 0;
    return (int) Math.min(targetMaximum, (long) value * targetMaximum / maximum);
  }

  private MenuDataSync() {}
}
