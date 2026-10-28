package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName MinigameSpeedyDelivery.xlsx
 * @sheetName MinigameSpeedyDelivery
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigameSpeedyDeliveryCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "MinigameSpeedyDelivery.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "MinigameSpeedyDelivery";

  /** 背包容量 */
  protected int BackpackCapacity;
  /** 每局消耗的开局道具数量 */
  protected Map<Integer,Long> ConsumeCount;
  /** 玩家空背包撞车扣时（s） */
  protected int CrashTimeLoss;
  /** 单局时长（s） */
  protected int Duration;
  /** 每日赠送道具数量 */
  protected Map<Integer,Long> GrantCount;
  /** 玩家基础速度（px/s） */
  protected int MoveSpeed;
  /** 仓库货物补货间隔时间（s） */
  protected int RespawnInterval;
  /** 结算奖励 */
  protected Map<Integer,Map<Integer,Integer>> Reward;
  /** 仓库货物总数 */
  protected int TotalShelves;

  /** 返回背包容量 */
  public int getBackpackCapacity() {
    return BackpackCapacity;
  }

  /** 返回每局消耗的开局道具数量 */
  public Map<Integer,Long> getConsumeCount() {
    return ConsumeCount;
  }

  /** 返回玩家空背包撞车扣时（s） */
  public int getCrashTimeLoss() {
    return CrashTimeLoss;
  }

  /** 返回单局时长（s） */
  public int getDuration() {
    return Duration;
  }

  /** 返回每日赠送道具数量 */
  public Map<Integer,Long> getGrantCount() {
    return GrantCount;
  }

  /** 返回玩家基础速度（px/s） */
  public int getMoveSpeed() {
    return MoveSpeed;
  }

  /** 返回仓库货物补货间隔时间（s） */
  public int getRespawnInterval() {
    return RespawnInterval;
  }

  /** 返回结算奖励 */
  public Map<Integer,Map<Integer,Integer>> getReward() {
    return Reward;
  }

  /** 返回仓库货物总数 */
  public int getTotalShelves() {
    return TotalShelves;
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
