package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.MinigameSpeedySkilCfg;

/**
 * MinigameSpeedySkil.xlsx配置管理容器
 *
 * @excelName MinigameSpeedySkil.xlsx
 * @sheetName MinigameSpeedySkil
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigameSpeedySkilCfgContainer extends BaseCfgContainer<MinigameSpeedySkilCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public MinigameSpeedySkilCfgContainer getNewContainer(){
    return new MinigameSpeedySkilCfgContainer();
  }

  public MinigameSpeedySkilCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("MinigameSpeedySkil.xlsx");
    return excelNameList;
  }

  @Override
  protected MinigameSpeedySkilCfg createNewBean() {
    return new MinigameSpeedySkilCfg();
  }
}
