package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName Chapterlevel.xlsx
 * @sheetName chapterLevel
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class ChapterLevelCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "Chapterlevel.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "chapterLevel";

  /** 敌方战力 */
  protected long battleValue;
  /** 奖励道具 */
  protected Map<Integer,Long> clearanceRewards;
  /** 单次消耗道具 */
  protected Map<Integer,Long> consumableItems;
  /** 奖励是否在章节地图上展示 */
  protected int display;
  /** 失败提升战力列表 */
  protected Map<Integer,Long> failList;
  /** 等级条件 */
  protected int levelLimit;
  /** 关卡类型 */
  protected int levelType;
  /** 关卡名称多语言ID */
  protected int nameId;
  /** 单格点亮次数 */
  protected int numOperations;
  /** 单次点亮获得道具 */
  protected Map<Integer,Long> rewardProps;
  /** 剧情脚本id */
  protected int scriptId;
  /** 剧情跳过简介多语言ID */
  protected int skipTextId;
  /** 拼图行_列 */
  protected List<Integer> specification;
  /** 关卡开始简介多语言ID */
  protected int startTextId;

  /** 返回敌方战力 */
  public long getBattleValue() {
    return battleValue;
  }

  /** 返回奖励道具 */
  public Map<Integer,Long> getClearanceRewards() {
    return clearanceRewards;
  }

  /** 返回单次消耗道具 */
  public Map<Integer,Long> getConsumableItems() {
    return consumableItems;
  }

  /** 返回奖励是否在章节地图上展示 */
  public int getDisplay() {
    return display;
  }

  /** 返回失败提升战力列表 */
  public Map<Integer,Long> getFailList() {
    return failList;
  }

  /** 返回等级条件 */
  public int getLevelLimit() {
    return levelLimit;
  }

  /** 返回关卡类型 */
  public int getLevelType() {
    return levelType;
  }

  /** 返回关卡名称多语言ID */
  public int getNameId() {
    return nameId;
  }

  /** 返回单格点亮次数 */
  public int getNumOperations() {
    return numOperations;
  }

  /** 返回单次点亮获得道具 */
  public Map<Integer,Long> getRewardProps() {
    return rewardProps;
  }

  /** 返回剧情脚本id */
  public int getScriptId() {
    return scriptId;
  }

  /** 返回剧情跳过简介多语言ID */
  public int getSkipTextId() {
    return skipTextId;
  }

  /** 返回拼图行_列 */
  public List<Integer> getSpecification() {
    return specification;
  }

  /** 返回关卡开始简介多语言ID */
  public int getStartTextId() {
    return startTextId;
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
