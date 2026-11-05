package com.jjg.game.sim.data;

public class TokenShopData {
    //商店类型
    private int type;
    //多语言
    private int langId;
    //货币id
    private int itemId;

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getLangId() {
        return langId;
    }

    public void setLangId(int langId) {
        this.langId = langId;
    }

    public int getItemId() {
        return itemId;
    }

    public void setItemId(int itemId) {
        this.itemId = itemId;
    }
}
