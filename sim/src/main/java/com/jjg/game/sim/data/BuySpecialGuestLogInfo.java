package com.jjg.game.sim.data;

import java.util.Set;

public class BuySpecialGuestLogInfo {
    private int poolId;
    private int cfgId;
    private Set<String> guestIds;
    public int getPoolId() {
        return poolId;
    }

    public void setPoolId(int poolId) {
        this.poolId = poolId;
    }

    public int getCfgId() {
        return cfgId;
    }

    public void setCfgId(int cfgId) {
        this.cfgId = cfgId;
    }

    public Set<String> getGuestIds() {
        return guestIds;
    }

    public void setGuestIds(Set<String> guestIds) {
        this.guestIds = guestIds;
    }
}
