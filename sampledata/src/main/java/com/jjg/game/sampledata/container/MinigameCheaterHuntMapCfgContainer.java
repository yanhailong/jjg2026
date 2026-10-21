package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.MinigameCheaterHuntMapCfg;

/**
 * MinigameCheaterHuntMap.xlsx配置管理容器
 *
 * @excelName MinigameCheaterHuntMap.xlsx
 * @sheetName MinigameCheaterHuntMap
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigameCheaterHuntMapCfgContainer extends BaseCfgContainer<MinigameCheaterHuntMapCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public MinigameCheaterHuntMapCfgContainer getNewContainer(){
    return new MinigameCheaterHuntMapCfgContainer();
  }

  public MinigameCheaterHuntMapCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("MinigameCheaterHuntMap.xlsx");
    return excelNameList;
  }

  @Override
  protected MinigameCheaterHuntMapCfg createNewBean() {
    return new MinigameCheaterHuntMapCfg();
  }
}
