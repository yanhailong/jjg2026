package com.jjg.game.sim.data;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class SimChapterData extends AbstractData {
    @Id
    private long playerId;
    //已通关关卡id，0表示尚未通关
    private int levelId;
    //点亮格子时的格子id
    private int girdIndex;
    //该格子已经点击次数
    private int girdLightCount;
    //是否完成拼图
    private boolean finishPuzzle;

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public int getLevelId() {
        return levelId;
    }

    public void setLevelId(int levelId) {
        this.levelId = levelId;
    }

    public int getGirdIndex() {
        return girdIndex;
    }

    public void setGirdIndex(int girdIndex) {
        this.girdIndex = girdIndex;
    }

    public int getGirdLightCount() {
        return girdLightCount;
    }

    public void setGirdLightCount(int girdLightCount) {
        this.girdLightCount = girdLightCount;
    }

    public boolean isFinishPuzzle() {
        return finishPuzzle;
    }

    public void setFinishPuzzle(boolean finishPuzzle) {
        this.finishPuzzle = finishPuzzle;
    }
}
