package com.jjg.game.sim.constant;

/**
 * 建筑产出
 *
 * @author 11
 * @date 2026/6/5
 */
public enum BuildingOutputType {
    //金币
    GOLD(1,1990000),
    //能量
    POWER(2,1024001),
    //服务能力
    SERVICE(3,1024004),
    //游戏上限
    GAME_LIMIT(4,0),
    //知名度
    AWARENESS(5,1024002),
    //曝光度
    EXPOSURE(6,1024003),
    //联盟奖杯
    LEAGUE_TROPHY(7,0),
    //宣传牌
    SIGN(8,0),
    //风云榜
    TOP_RANK(9,0),
    //装饰
    DECORATION(10,0),
    //管理属性(它是一个集合体，包括了SERVICE，AWARENESS，EXPOSURE)，酒水服务类型雇员属性
    MANAGE_ARRT(11,0),
    //场景等级经验
    CASINO_LEVEL_EXP(12,1022505),
    //看广告增加金币收益
    WATCH_ADS_ADD_GOLD(13,0),
    //对战博弈类型雇员属性
    BATTLE(14,0),
    //下注策略类型雇员属性
    BET(15,0),
    //电子娱乐类型雇员属性
    ENTERTAINMENT(16,0),
    //对战博弈类型雇员属性万分比加成
    BATTLE_PROP(114,0),
    //下注策略类型雇员属性万分比加成
    BET_PROP(115,0),
    //电子娱乐类型雇员属性万分比加成
    ENTERTAINMENT_PROP(116,0),
    //酒水服务类型雇员属性万分比加成
    MANAGE_ARRT_PROP(111,0),
    ;

    private final int code;
    private final int itemId;

    BuildingOutputType(int code,int itemId) {
        this.code = code;
        this.itemId = itemId;
    }

    public int getCode() {
        return code;
    }

    public int getItemId() {
        return itemId;
    }

    public static BuildingOutputType fromCode(int code) {
        switch (code) {
            case 1 -> {
                return GOLD;
            }
            case 2 -> {
                return POWER;
            }
            case 3 -> {
                return SERVICE;
            }
            case 4 -> {
                return GAME_LIMIT;
            }
            case 5 -> {
                return AWARENESS;
            }
            case 6 -> {
                return EXPOSURE;
            }
            case 7 -> {
                return LEAGUE_TROPHY;
            }
            case 8 -> {
                return SIGN;
            }
            case 9 -> {
                return TOP_RANK;
            }
            case 10 -> {
                return DECORATION;
            }
            case 11 -> {
                return MANAGE_ARRT;
            }
            case 12 -> {
                return CASINO_LEVEL_EXP;
            }
            case 13 -> {
                return WATCH_ADS_ADD_GOLD;
            }
            case 14 -> {
                return BATTLE;
            }
            case 15 -> {
                return BET;
            }
            case 16 -> {
                return ENTERTAINMENT;
            }
            case 111 -> {
                return MANAGE_ARRT_PROP;
            }
            case 114 -> {
                return BATTLE_PROP;
            }
            case 115 -> {
                return BET_PROP;
            }
            case 116 -> {
                return ENTERTAINMENT_PROP;
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * 加成归类: 管理区三属性(SERVICE/AWARENESS/EXPOSURE)统一归入集合体 MANAGE_ARRT, 其余为自身。
     * 用于按产出类型查找对应的加成值。
     */
    public BuildingOutputType bonusGroup() {
        return switch (this) {
            case SERVICE, AWARENESS, EXPOSURE -> MANAGE_ARRT;
            default -> this;
        };
    }

    /** 雇员固定属性及其万分比类型归并到同一属性，建筑产出类型返回 null。 */
    public BuildingOutputType employeeAttribute() {
        return switch (this) {
            case MANAGE_ARRT, MANAGE_ARRT_PROP -> MANAGE_ARRT;
            case BATTLE, BATTLE_PROP -> BATTLE;
            case BET, BET_PROP -> BET;
            case ENTERTAINMENT, ENTERTAINMENT_PROP -> ENTERTAINMENT;
            default -> null;
        };
    }
}
