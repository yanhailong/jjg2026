package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName MinigameGodofGamblersRubbing.xlsx
 * @sheetName MinigameGodofGamblersRubbing
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigameGodofGamblersRubbingCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "MinigameGodofGamblersRubbing.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "MinigameGodofGamblersRubbing";

  /** 再次搓牌消耗道具 */
  protected Map<Integer,Long> ItemCost;
  /** 每日系统重置道具数量 */
  protected Map<Integer,Long> ItemGift;
  /** 名称多语言 */
  protected int Name;
  /** 最多搓牌次数 */
  protected int RubCount;
  /** 获胜奖励 */
  protected Map<Integer,Map<Integer,Integer>> WinReward;

  /** 返回再次搓牌消耗道具 */
  public Map<Integer,Long> getItemCost() {
    return ItemCost;
  }

  /** 返回每日系统重置道具数量 */
  public Map<Integer,Long> getItemGift() {
    return ItemGift;
  }

  /** 返回名称多语言 */
  public int getName() {
    return Name;
  }

  /** 返回最多搓牌次数 */
  public int getRubCount() {
    return RubCount;
  }

  /** 返回获胜奖励 */
  public Map<Integer,Map<Integer,Integer>> getWinReward() {
    return WinReward;
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
