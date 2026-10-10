package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.StarterPackCfg;

/**
 * StarterPack.xlsx配置管理容器
 *
 * @excelName StarterPack.xlsx
 * @sheetName StarterPack
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class StarterPackCfgContainer extends BaseCfgContainer<StarterPackCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public StarterPackCfgContainer getNewContainer(){
    return new StarterPackCfgContainer();
  }

  public StarterPackCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("StarterPack.xlsx");
    return excelNameList;
  }

  @Override
  protected StarterPackCfg createNewBean() {
    return new StarterPackCfg();
  }
}
