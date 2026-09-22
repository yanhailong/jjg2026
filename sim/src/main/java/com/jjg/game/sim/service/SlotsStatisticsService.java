package com.jjg.game.sim.service;

import com.jjg.game.common.rpc.ClusterRpcReference;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.pb.KVInfo;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.GlobalConfigCfg;
import com.jjg.game.sim.bridge.ToSimBridge;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.dao.SlotsStatisticsDao;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.data.SlotsStatisticsData;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import com.jjg.game.sim.pb.res.ResSlotsStatistics;
import com.jjg.game.sim.pb.struct.SlotsStatisticsInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class SlotsStatisticsService {
    @Autowired
    private SlotsStatisticsDao slotsStatisticsDao;
    @Autowired
    private SimPlayerContextRegistry contexts;
    @Autowired
    private SimNodeService simNodeService;
    @ClusterRpcReference
    private ToSimBridge toSimBridge;

    public void load(SimPlayerContext ctx) {
        ctx.setSlotsStatisticsData(slotsStatisticsDao.findById(ctx.playerId()).orElse(null));
    }

    /**
     * 仅由离线保存队列调用，不参加周期全量保存。
     */
    public void saveOnExit(SimPlayerContext ctx) {
        SlotsStatisticsData data = ctx.getSlotsStatisticsData();
        if (data != null && !data.isClientReq()) {
            data.setPlayerId(ctx.playerId());
            slotsStatisticsDao.save(data);
        }
    }

    public ResSlotsStatistics request(SimPlayerContext ctx) {
        ResSlotsStatistics res = new ResSlotsStatistics(Code.SUCCESS);
        SlotsStatisticsData data = ctx.getSlotsStatisticsData();
        if (data == null) {
            return res;
        }

        GlobalConfigCfg globalConfigCfg = GameDataManager.getGlobalConfigCfg(SimConstant.Global.SLOTS_STATISTICS_SHOW);
        if (globalConfigCfg == null || data.getSpinCount() < globalConfigCfg.getIntValue()) {
            return res;
        }

        SlotsStatisticsInfo info = new SlotsStatisticsInfo();
        info.spinCount = data.getSpinCount();
        if (data.getBigWinCountMap() != null) {
            info.bigWinCounts = new ArrayList<>(data.getBigWinCountMap().size());
            data.getBigWinCountMap().forEach((type, count) -> info.bigWinCounts.add(new KVInfo(type, count)));
        }
        info.freeCount = data.getFreeCount();
        info.specialCount = data.getSpecialCount();
        info.jackpoolCount = data.getJackpoolCount();
        info.allWinCoin = data.getAllWinCoin();
        info.allBetCoin = data.getAllBetCoin();
        info.allSpendPower = data.getAllSpendPower();
        info.allSpendPowerGetCoin = data.getAllSpendPowerGetCoin();

        //总盈利
        long slotsRealWin = data.getAllSpendPowerGetCoin() + data.getAllBetCoin() - data.getAllBetCoin();
        if (slotsRealWin > ctx.getSimBaseData().getSlotsAthWinCoin()) {
            info.ath = true;
            ctx.getSimBaseData().setSlotsAthWinCoin(slotsRealWin);
        }

        //删除成功后再标记，删除失败时仍可重试或离线保存。
        slotsStatisticsDao.deleteById(ctx.playerId());
        data.setClientReq(true);
        res.statistics = info;
        return res;
    }

    public void clear(SimPlayerContext ctx) {
        slotsStatisticsDao.deleteById(ctx.playerId());
        ctx.setSlotsStatisticsData(null);
    }

    /**
     * 选场可能来自 slots 节点，清理必须在 SIM 数据所属节点执行。
     */
    public void clear(long playerId) {
        SimPlayerContext ctx = contexts.getContext(playerId);
        if (ctx != null) {
            clear(ctx);
            return;
        }
    }
}
