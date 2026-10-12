package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.SmallDrawDailyGiftCfg;

/**
 * SmallDrawDailyGift.xlsx配置管理容器
 *
 * @excelName SmallDrawDailyGift.xlsx
 * @sheetName SmallDrawDailyGift
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class SmallDrawDailyGiftCfgContainer extends BaseCfgContainer<SmallDrawDailyGiftCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public SmallDrawDailyGiftCfgContainer getNewContainer(){
    return new SmallDrawDailyGiftCfgContainer();
  }

  public SmallDrawDailyGiftCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("SmallDrawDailyGift.xlsx");
    return excelNameList;
  }

  @Override
  protected SmallDrawDailyGiftCfg createNewBean() {
    return new SmallDrawDailyGiftCfg();
  }
}
