package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName RandomEventDetails.xlsx
 * @sheetName RandomEventDetails
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class RandomEventDetailsCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "RandomEventDetails.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "RandomEventDetails";

  /** A结算奖励 */
  protected Map<Integer,Long> A_reward;
  /** B结算奖励 */
  protected Map<Integer,Long> B_reward;
  /** 事件人物头顶图标 */
  protected String Icon;
  /** 事件选择按钮_多语言ID_A|B */
  protected List<Integer> answer_AB;
  /** 事件类型 */
  protected int areaId;
  /** 事件人物形象 */
  protected String character;
  /** 事件人物形象 */
  protected String character_2d;
  /** 其他参数：老千初始刷新数量_老千需求数量 */
  protected List<Integer> parameter1;
  /** 其他参数：投资倒计时（秒） */
  protected List<Integer> parameter2;
  /** 按钮跳转功能 A|B */
  protected List<Integer> redirectsID_AB;
  /** 事件结算_多语言ID_A|B */
  protected List<Integer> result_AB;
  /** 事件描述多语言ID */
  protected List<Integer> unlockCondition;

  /** 返回A结算奖励 */
  public Map<Integer,Long> getA_reward() {
    return A_reward;
  }

  /** 返回B结算奖励 */
  public Map<Integer,Long> getB_reward() {
    return B_reward;
  }

  /** 返回事件人物头顶图标 */
  public String getIcon() {
    return Icon;
  }

  /** 返回事件选择按钮_多语言ID_A|B */
  public List<Integer> getAnswer_AB() {
    return answer_AB;
  }

  /** 返回事件类型 */
  public int getAreaId() {
    return areaId;
  }

  /** 返回事件人物形象 */
  public String getCharacter() {
    return character;
  }

  /** 返回事件人物形象 */
  public String getCharacter_2d() {
    return character_2d;
  }

  /** 返回其他参数：老千初始刷新数量_老千需求数量 */
  public List<Integer> getParameter1() {
    return parameter1;
  }

  /** 返回其他参数：投资倒计时（秒） */
  public List<Integer> getParameter2() {
    return parameter2;
  }

  /** 返回按钮跳转功能 A|B */
  public List<Integer> getRedirectsID_AB() {
    return redirectsID_AB;
  }

  /** 返回事件结算_多语言ID_A|B */
  public List<Integer> getResult_AB() {
    return result_AB;
  }

  /** 返回事件描述多语言ID */
  public List<Integer> getUnlockCondition() {
    return unlockCondition;
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
