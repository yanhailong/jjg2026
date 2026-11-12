package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.MinigameFishSkilCfg;

/**
 * MinigameFishSkil.xlsx配置管理容器
 *
 * @excelName MinigameFishSkil.xlsx
 * @sheetName MinigameFishSkil
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigameFishSkilCfgContainer extends BaseCfgContainer<MinigameFishSkilCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public MinigameFishSkilCfgContainer getNewContainer(){
    return new MinigameFishSkilCfgContainer();
  }

  public MinigameFishSkilCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("MinigameFishSkil.xlsx");
    return excelNameList;
  }

  @Override
  protected MinigameFishSkilCfg createNewBean() {
    return new MinigameFishSkilCfg();
  }
}
