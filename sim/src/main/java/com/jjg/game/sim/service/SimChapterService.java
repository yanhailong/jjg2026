package com.jjg.game.sim.service;

import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.ItemOperationResult;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.ChapterCfg;
import com.jjg.game.sampledata.bean.ChapterLevelCfg;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.dao.SimChapterDao;
import com.jjg.game.sim.data.SimChapterData;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.pb.res.ResChapterInfo;
import com.jjg.game.sim.pb.res.ResChapterLightGird;
import com.jjg.game.sim.pb.res.ResPassChapterLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SimChapterService {
    private static final Logger log = LoggerFactory.getLogger(SimChapterService.class);

    private final PlayerPackService playerPackService;
    private final SimChapterDao simChapterDao;
    private final SimConfigCacheService simConfigCacheService;

    public SimChapterService(PlayerPackService playerPackService, SimChapterDao simChapterDao, SimConfigCacheService simConfigCacheService) {
        this.playerPackService = playerPackService;
        this.simChapterDao = simChapterDao;
        this.simConfigCacheService = simConfigCacheService;
    }

    /**
     * 获取章节信息
     *
     * @param ctx
     * @return
     */
    public ResChapterInfo getResChapterInfo(SimPlayerContext ctx) {
        ResChapterInfo res = new ResChapterInfo(Code.SUCCESS);

        if (ctx.getSimChapterData() == null) {
            Optional<SimChapterData> optional = simChapterDao.findById(ctx.playerId());
            if (optional.isPresent()) {
                ctx.setSimChapterData(optional.get());
            } else {
                ChapterCfg chapterCfg = GameDataManager.getChapterCfg(1);
                if (chapterCfg == null) {
                    log.warn("获取第一章配置为空 playerId={}", ctx.playerId());
                    res.code = Code.SAMPLE_ERROR;
                    return res;
                }
                SimChapterData simChapterData = new SimChapterData();
                simChapterData.setPlayerId(ctx.playerId());
                simChapterData.setLevelId(0);
                ctx.setSimChapterData(simChapterData);
                simChapterDao.save(simChapterData);
            }
        }

        res.levelId = ctx.getSimChapterData().getLevelId();
        int chapterLevelId = res.levelId == 0 ? simConfigCacheService.getNextChapterLevelId(0) : res.levelId;
        res.chapterId = simConfigCacheService.getLevelIdByChapterId(chapterLevelId);
        res.girdIndex = ctx.getSimChapterData().getGirdIndex();
        res.girdLightCount = ctx.getSimChapterData().getGirdLightCount();
        return res;
    }

    /**
     * 通关关卡
     *
     * @param ctx
     * @return
     */
    public ResPassChapterLevel passChapterLevel(SimPlayerContext ctx, int levelId) {
        ResPassChapterLevel res = new ResPassChapterLevel(Code.SUCCESS);
        if (ctx.getSimChapterData() == null) {
            res.code = Code.NOT_FOUND;
            log.warn("通关时未找到玩家的关卡信息 playerId={}", ctx.playerId());
            return res;
        }

        if (levelId <= ctx.getSimChapterData().getLevelId()) {
            res.code = Code.REPEAT_OP;
            log.warn("重复通关 playerId={},reqLevelId={},dataLevelId={}", ctx.playerId(), levelId, ctx.getSimChapterData().getLevelId());
            return res;
        }

        //根据配置计算出来的下一关卡id
        int nextChapterLevelId = simConfigCacheService.getNextChapterLevelId(ctx.getSimChapterData().getLevelId());
        if (levelId != nextChapterLevelId) {
            res.code = Code.REPEAT_OP;
            log.warn("通关时发现关卡id错误 playerId={},reqLevelId={},dataLevelId={},nextChapterLevelId={}", ctx.playerId(), levelId, ctx.getSimChapterData().getLevelId(), nextChapterLevelId);
            return res;
        }

        ChapterLevelCfg chapterLevelCfg = GameDataManager.getChapterLevelCfg(levelId);
        if (chapterLevelCfg == null) {
            log.warn("通关时获取关卡配置为空 playerId={},levelId={}", ctx.playerId(), levelId);
            res.code = Code.SAMPLE_ERROR;
            return res;
        }

        if (ctx.getSimBaseData().getAllLevel() < chapterLevelCfg.getLevelLimit()) {
            log.warn("通关时等级不足 playerId={},levelId={}，cfgLevel={},dataLevel={}", ctx.playerId(), levelId, chapterLevelCfg.getLevelLimit(), ctx.getSimBaseData().getAllLevel());
            res.code = Code.LEVEL_NOT_ENOUGH;
            return res;
        }

        if (chapterLevelCfg.getLevelType() == 2) {
            int allGirdLen = chapterLevelCfg.getSpecification().get(0) * chapterLevelCfg.getSpecification().get(1);
            if (ctx.getSimChapterData().getGirdIndex() != allGirdLen - 1
                    || ctx.getSimChapterData().getGirdLightCount() < chapterLevelCfg.getNumOperations()) {
                res.code = Code.FORBID;
                return res;
            }
            if (playerPackService.getItemCount(ctx.playerId(), SimConstant.Item.ID_EMPLOYEE_EXP)
                    < chapterLevelCfg.getBattleValue()) {
                res.code = Code.FORBID;
                return res;
            }
        } else if (chapterLevelCfg.getLevelType() != 1) {
            res.code = Code.SAMPLE_ERROR;
            return res;
        }

        if (chapterLevelCfg.getClearanceRewards() != null && !chapterLevelCfg.getClearanceRewards().isEmpty()) {
            playerPackService.addItems(ctx.playerId(), chapterLevelCfg.getClearanceRewards(), AddType.SIM_PASS_CHAPTER, true);
        }

        ctx.getSimChapterData().setLevelId(levelId);
        ctx.getSimChapterData().setGirdIndex(0);
        ctx.getSimChapterData().setGirdLightCount(0);
        return res;
    }

    /**
     * 点亮格子
     *
     * @param ctx
     * @param levelId
     * @return
     */
    public ResChapterLightGird lightGird(SimPlayerContext ctx, int levelId, int girdIndex) {
        ResChapterLightGird res = new ResChapterLightGird(Code.SUCCESS);
        if (ctx.getSimChapterData() == null) {
            res.code = Code.NOT_FOUND;
            log.warn("点亮格子时未找到玩家的关卡信息 playerId={}", ctx.playerId());
            return res;
        }

        if (levelId <= ctx.getSimChapterData().getLevelId()) {
            res.code = Code.REPEAT_OP;
            log.warn("本次点亮格子是重复通关 playerId={},reqLevelId={},dataLevelId={}", ctx.playerId(), levelId, ctx.getSimChapterData().getLevelId());
            return res;
        }

        //根据配置计算出来的下一关卡id
        int nextChapterLevelId = simConfigCacheService.getNextChapterLevelId(ctx.getSimChapterData().getLevelId());
        if (levelId != nextChapterLevelId) {
            res.code = Code.REPEAT_OP;
            log.warn("点亮格子时发现关卡id错误 playerId={},reqLevelId={},dataLevelId={},nextChapterLevelId={}", ctx.playerId(), levelId, ctx.getSimChapterData().getLevelId(), nextChapterLevelId);
            return res;
        }

        ChapterLevelCfg chapterLevelCfg = GameDataManager.getChapterLevelCfg(levelId);
        if (chapterLevelCfg == null) {
            log.warn("点亮格子时获取关卡配置为空 playerId={},levelId={}", ctx.playerId(), levelId);
            res.code = Code.SAMPLE_ERROR;
            return res;
        }

        if (ctx.getSimBaseData().getAllLevel() < chapterLevelCfg.getLevelLimit()) {
            log.warn("点亮格子时等级不足 playerId={},levelId={}，simLevel={}", ctx.playerId(), levelId, ctx.getSimBaseData().getAllLevel());
            res.code = Code.LEVEL_NOT_ENOUGH;
            return res;
        }

        if (chapterLevelCfg.getLevelType() != 2) {
            res.code = Code.PARAM_ERROR;
            return res;
        }

        int allGirdLen = chapterLevelCfg.getSpecification().get(0) * chapterLevelCfg.getSpecification().get(1);
        if (girdIndex < 0 || girdIndex >= allGirdLen) {
            log.warn("点亮格子时格子下标错误 playerId={},levelId={},girdIndex={},allGirdLen={}", ctx.playerId(), levelId, girdIndex, allGirdLen);
            res.code = Code.PARAM_ERROR;
            return res;
        }

        int expectedGirdIndex = ctx.getSimChapterData().getGirdIndex();
        int girdLightCount = ctx.getSimChapterData().getGirdLightCount();
        if (girdLightCount >= chapterLevelCfg.getNumOperations()) {
            expectedGirdIndex = ctx.getSimChapterData().getGirdIndex() + 1;
            girdLightCount = 0;
        }
        if (girdIndex != expectedGirdIndex) {
            log.warn("点亮格子时格子顺序错误 playerId={},levelId={},girdIndex={},expectedGirdIndex={}", ctx.playerId(), levelId, girdIndex, expectedGirdIndex);
            res.code = Code.PARAM_ERROR;
            return res;
        }

        if (chapterLevelCfg.getConsumableItems() != null && !chapterLevelCfg.getConsumableItems().isEmpty()) {
            CommonResult<ItemOperationResult> removeResult = playerPackService.removeItem(ctx.getPlayer(), ItemUtils.buildItems(chapterLevelCfg.getConsumableItems()), AddType.SIM_CHAPTER_LIGHT_GIRD);
            if (!removeResult.success()) {
                log.warn("点亮格子时扣除道具失败 playerId={},levelId={},girdIndex={},code={}", ctx.playerId(), levelId, girdIndex, removeResult.code);
                res.code = removeResult.code;
                return res;
            }
        }
        if (chapterLevelCfg.getRewardProps() != null && !chapterLevelCfg.getRewardProps().isEmpty()) {
            playerPackService.addItems(ctx.playerId(), chapterLevelCfg.getRewardProps(), AddType.SIM_CHAPTER_LIGHT_GIRD, true);
        }

        ctx.getSimChapterData().setGirdIndex(girdIndex);
        ctx.getSimChapterData().setGirdLightCount(girdLightCount + 1);

        return res;
    }
}
