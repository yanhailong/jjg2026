package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName MinigameCheaterHuntLevel.xlsx
 * @sheetName MinigameCheaterHuntLevel
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigameCheaterHuntLevelCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "MinigameCheaterHuntLevel.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "MinigameCheaterHuntLevel";

  /** 地图解锁条件 */
  protected Map<Integer,Long> ClearCount;
  /** 通关奖励 */
  protected Map<Integer,Long> ClearReward;
  /** 无效点击扣除倒计时 */
  protected int CostItemCount;
  /** 消耗道具id_数量 */
  protected Map<Integer,Long> CostItemID;
  /** 每日系统重置道具数量 */
  protected Map<Integer,Long> DailyFreeItemCount;
  /** 作弊点抽取个数 */
  protected int DrawCount;
  /** 等级 */
  protected int Level;
  /** 失败奖励 */
  protected Map<Integer,Long> LoseReward;
  /** 地图名称多语言 */
  protected int MapNameLocalization;
  /** 作弊点寻找个数 */
  protected int TargetCount;
  /** 倒计时时长 */
  protected int TimeLimit;
  /** 使用场景 */
  protected List<Integer> UseCase;

  /** 返回地图解锁条件 */
  public Map<Integer,Long> getClearCount() {
    return ClearCount;
  }

  /** 返回通关奖励 */
  public Map<Integer,Long> getClearReward() {
    return ClearReward;
  }

  /** 返回无效点击扣除倒计时 */
  public int getCostItemCount() {
    return CostItemCount;
  }

  /** 返回消耗道具id_数量 */
  public Map<Integer,Long> getCostItemID() {
    return CostItemID;
  }

  /** 返回每日系统重置道具数量 */
  public Map<Integer,Long> getDailyFreeItemCount() {
    return DailyFreeItemCount;
  }

  /** 返回作弊点抽取个数 */
  public int getDrawCount() {
    return DrawCount;
  }

  /** 返回等级 */
  public int getLevel() {
    return Level;
  }

  /** 返回失败奖励 */
  public Map<Integer,Long> getLoseReward() {
    return LoseReward;
  }

  /** 返回地图名称多语言 */
  public int getMapNameLocalization() {
    return MapNameLocalization;
  }

  /** 返回作弊点寻找个数 */
  public int getTargetCount() {
    return TargetCount;
  }

  /** 返回倒计时时长 */
  public int getTimeLimit() {
    return TimeLimit;
  }

  /** 返回使用场景 */
  public List<Integer> getUseCase() {
    return UseCase;
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
