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

  private MenuDataSync() {}
}
