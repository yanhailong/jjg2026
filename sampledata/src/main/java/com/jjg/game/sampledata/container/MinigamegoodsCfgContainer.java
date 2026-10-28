package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.MinigamegoodsCfg;

/**
 * Minigamegoods.xlsx配置管理容器
 *
 * @excelName Minigamegoods.xlsx
 * @sheetName Minigamegoods
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigamegoodsCfgContainer extends BaseCfgContainer<MinigamegoodsCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public MinigamegoodsCfgContainer getNewContainer(){
    return new MinigamegoodsCfgContainer();
  }

  public MinigamegoodsCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("Minigamegoods.xlsx");
    return excelNameList;
  }

  @Override
  protected MinigamegoodsCfg createNewBean() {
    return new MinigamegoodsCfg();
  }
}
