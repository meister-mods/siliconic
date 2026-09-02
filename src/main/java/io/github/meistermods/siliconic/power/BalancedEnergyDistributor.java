package io.github.meistermods.siliconic.power;

/** Pure fair-share allocation used by power networks and exercised by automated game tests. */
public final class BalancedEnergyDistributor {
  public static int[] allocate(int[] demands, int budget, int start) {
    int[] allocations = new int[demands.length];
    if (demands.length == 0 || budget <= 0) return allocations;

    boolean[] active = new boolean[demands.length];
    int activeCount = 0;
    long totalDemand = 0;
    for (int index = 0; index < demands.length; index++) {
      int demand = Math.max(0, demands[index]);
      active[index] = demand > 0;
      if (active[index]) activeCount++;
      totalDemand += demand;
    }

    int remaining = (int) Math.min(Math.min((long) budget, totalDemand), Integer.MAX_VALUE);
    int normalizedStart = Math.floorMod(start, demands.length);
    while (remaining > 0 && activeCount > 0) {
      int share = remaining / activeCount;
      if (share == 0) {
        for (int offset = 0; offset < demands.length && remaining > 0; offset++) {
          int index = (normalizedStart + offset) % demands.length;
          if (!active[index]) continue;
          allocations[index]++;
          remaining--;
        }
        break;
      }

      boolean cappedReceiver = false;
      for (int index = 0; index < demands.length; index++) {
        if (!active[index]) continue;
        int unmetDemand = Math.max(0, demands[index]) - allocations[index];
        if (unmetDemand > share) continue;
        allocations[index] += unmetDemand;
        remaining -= unmetDemand;
        active[index] = false;
        activeCount--;
        cappedReceiver = true;
      }
      if (cappedReceiver) continue;

      for (int index = 0; index < demands.length; index++)
        if (active[index]) {
          allocations[index] += share;
          remaining -= share;
        }
      for (int offset = 0; offset < demands.length && remaining > 0; offset++) {
        int index = (normalizedStart + offset) % demands.length;
        if (!active[index]) continue;
        allocations[index]++;
        remaining--;
      }
    }
    return allocations;
  }

  private BalancedEnergyDistributor() {}
}
