package com.jjg.game.sim.data;

/** 固定值与万分比加成，汇总后统一计算并取整。 */
public class SimAttributeBonus {
    private long fixed;
    private long percent;

    public void addFixed(long value) {
        fixed += value;
    }

    public void addPercent(long value) {
        percent += value;
    }

    public long fixed() {
        return fixed;
    }

    public long percent() {
        return percent;
    }

    public long apply(long base) {
        return (base + fixed) * (10000 + percent) / 10000;
    }
}
