package com.jjg.game.season.data;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.*;

/** 独立持久化，离线补发不依赖玩家上线或切季，也不会被玩家缓存覆盖。 */
@Document("continuousGift")
public class ContinuousGiftData {
    @Id public String id;
    public long playerId;
    public int activityId;
    public long startTime;
    public long endTime;
    public int totalDays;
    public String zoneId;
    public boolean viewed;
    public List<Integer> purchasedDays = new ArrayList<>();
    public long lastPurchaseTime;
    public boolean grandClaimed;
    public Map<Integer, Long> grandRewards = new LinkedHashMap<>();
    public Map<String, Delivery> deliveries = new LinkedHashMap<>();
    public Reservation reservation;
    public boolean settled;

    public static class Reservation {
        public String token;
        public int day;
        public long createdAt;
        public long price;
        public String channelProductId;
        public Map<Integer, Long> rewards = new LinkedHashMap<>();
    }

    public static class Delivery {
        /** PENDING 尚未投递；DELIVERING 结果待核对；BAG 已到背包；MAIL 已邮件补发。 */
        public String status = "PENDING";
        public String orderId;
        public Map<Integer, Long> rewards = new LinkedHashMap<>();
    }
}
