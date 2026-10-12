package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.SmallDrawCfg;

/**
 * SmallDraw.xlsx配置管理容器
 *
 * @excelName SmallDraw.xlsx
 * @sheetName SmallDraw
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class SmallDrawCfgContainer extends BaseCfgContainer<SmallDrawCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public SmallDrawCfgContainer getNewContainer(){
    return new SmallDrawCfgContainer();
  }

  public SmallDrawCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("SmallDraw.xlsx");
    return excelNameList;
  }

  @Override
  protected SmallDrawCfg createNewBean() {
    return new SmallDrawCfg();
  }
}
