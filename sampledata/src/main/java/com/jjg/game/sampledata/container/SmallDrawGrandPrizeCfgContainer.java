package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.SmallDrawGrandPrizeCfg;

/**
 * SmallDrawGrandPrize.xlsx配置管理容器
 *
 * @excelName SmallDrawGrandPrize.xlsx
 * @sheetName SmallDrawGrandPrize
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class SmallDrawGrandPrizeCfgContainer extends BaseCfgContainer<SmallDrawGrandPrizeCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public SmallDrawGrandPrizeCfgContainer getNewContainer(){
    return new SmallDrawGrandPrizeCfgContainer();
  }

  public SmallDrawGrandPrizeCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("SmallDrawGrandPrize.xlsx");
    return excelNameList;
  }

  @Override
  protected SmallDrawGrandPrizeCfg createNewBean() {
    return new SmallDrawGrandPrizeCfg();
  }
}
