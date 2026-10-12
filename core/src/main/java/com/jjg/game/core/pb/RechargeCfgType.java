package com.jjg.game.core.pb;

public enum RechargeCfgType {
    /**
     * 货币付费
     */
    MONEY(1),

    /**
     * 道具兑换
     */
    ITEM_EXCHANGE(2),

    /**
     * 广告
     */
    ADS(3),

    /**
     * 免费
     */
    FREE(4),

    ;

    private final int type;

    RechargeCfgType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public static RechargeCfgType valueOf(int type) {
        switch (type) {
            case 1 -> {
                return MONEY;
            }
            case 2 -> {
                return ITEM_EXCHANGE;
            }
            case 3 -> {
                return ADS;
            }
            case 4 -> {
                return FREE;
            }
            default -> {
                return MONEY;
            }
        }
    }
}
