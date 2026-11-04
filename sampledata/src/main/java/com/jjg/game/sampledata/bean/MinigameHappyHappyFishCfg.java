package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName MinigameHappyHappyFish.xlsx
 * @sheetName MinigameHappyHappyFish
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigameHappyHappyFishCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "MinigameHappyHappyFish.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "MinigameHappyHappyFish";

  /** 每局消耗的开局道具数量 */
  protected Map<Integer,Long> ConsumeCount;
  /** 单局游戏时长(s) */
  protected int Duration;
  /** 沙漏道具增加游戏时长(s) */
  protected int ExtraTime;
  /** 每日系统重置道具数量 */
  protected Map<Integer,Long> GrantCount;
  /** 同屏最大鱼数量 */
  protected int MaxCount;
  /** 加速器对回收速度加成系数 */
  protected int Multiplier;
  /** 钩爪回收时间速度（px/s） */
  protected int ReturnSpeed;
  /** 结算奖励 */
  protected Map<Integer,Map<Integer,Integer>> Reward;
  /** 钩爪射出时间速度（px/s） */
  protected int ShootSpeed;

  /** 返回每局消耗的开局道具数量 */
  public Map<Integer,Long> getConsumeCount() {
    return ConsumeCount;
  }

  /** 返回单局游戏时长(s) */
  public int getDuration() {
    return Duration;
  }

  /** 返回沙漏道具增加游戏时长(s) */
  public int getExtraTime() {
    return ExtraTime;
  }

  /** 返回每日系统重置道具数量 */
  public Map<Integer,Long> getGrantCount() {
    return GrantCount;
  }

  /** 返回同屏最大鱼数量 */
  public int getMaxCount() {
    return MaxCount;
  }

  /** 返回加速器对回收速度加成系数 */
  public int getMultiplier() {
    return Multiplier;
  }

  /** 返回钩爪回收时间速度（px/s） */
  public int getReturnSpeed() {
    return ReturnSpeed;
  }

  /** 返回结算奖励 */
  public Map<Integer,Map<Integer,Integer>> getReward() {
    return Reward;
  }

  /** 返回钩爪射出时间速度（px/s） */
  public int getShootSpeed() {
    return ShootSpeed;
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
