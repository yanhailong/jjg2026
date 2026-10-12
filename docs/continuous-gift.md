# 赛季连续礼包接入说明

依据《连续礼包活动策划文档_V2》，服务端实现位于 `sim` 模块的 `com.jjg.game.season` 包。前端工程不在当前仓库中，客户端按下列协议制作入口和礼包页面。

## 已实现规则

- 活动时间为服务端配置的半开区间 `[StartTime, EndTime)`，未开始和已结束活动不进入列表。
- 玩家从第 1 个宝箱开始依次购买；同一活动在配置时区的每个自然日最多成功购买一个。漏购保留进度，不允许一天补买多个。不采用间隔 24 小时的冷却。
- 钻石、金币由购买接口扣除；人民币只通过已验证的支付回调交付，不能用普通购买接口获取现金礼包。
- 买齐全部宝箱即解锁大奖，活动结束前可免费领取一次。
- 首次进入本期活动清除红点，次日不恢复；新活动 ID 或新开始时间产生独立状态和红点。
- 每分钟扫描到期数据，向离线和在线玩家补发已支付且明确未交付的宝箱，以及已解锁但未领取的大奖。未购买宝箱不补发。
- 支付预占、价格和奖励快照持久化。正常延迟到账即使跨活动结束或配置移除，也能按下单快照通过邮件交付；跨日到账占用实际成功购买日的名额。

文档 2.3 中“每个宝箱每天只能购买一次”与总则“每天限购 1 个”有表述差异；实现采用总则的活动级每日限购。

## 正式配置接入（2026-10-12）

运行时只读取策划提交的 ActivityConfig、GameFunction、ConsecutivePack，并关联现有 ShopRechargeList、Item。原 ContinuousGiftActivity/Chest/Grand 样例已不再读取，不需要恢复它们的生成类或修改策划表。

- ActivityConfig：type=27；open 为活动开关；open_type=2；time_start/time_end 控制绝对时间窗口；value 明确列出本期礼包行 ID；condition 为参与条件。
- GameFunction：functionType=27 对应功能入口，当前 ID=6001；isOpen 控制总开关，condition 同样参与服务端校验；通过 ActivityOpenListener 接入现有功能列表。showCondition 仍由现有功能列表逻辑检查。
- ConsecutivePack：type=1 为每日礼包，PackQuantity 为购买顺序（必须从 1 连续排列）；type=2 为唯一终极大奖；RewardGroupCount 必须在本期一致；getitem 为奖励。Price 是 ShopRechargeList ID，不是金额。
- ShopRechargeList：type=1 按 Price 金额换算为分并读取渠道 SKU；type=2 支持单一钻石或金币 Redeem 成本。广告、免费和其他道具兑换目前拒绝作为付费连续礼包出售。

当前实际配置有 8 期（57062–57069），每期 5 个每日礼包和 1 个大奖。首期从 2026/10/12 00:00:00 至 2026/10/17 23:59:59；后续期次严格按各自 value 引用，不自行推断奖励组。商品 1001 的金额为 0.99（服务端 99 分）。活动与入口的参与条件均为 simAllLevel(6)。

时间支持 yyyy/MM/dd HH:mm:ss 和 yyyy-MM-dd HH:mm:ss，使用服务器时区，各节点须保持一致。结束时间沿用半开区间规则，到达配置 endTime 即结束；不额外延长一秒。仅支持本次提交的限时方式，其他开服/循环模式会拒绝配置并记录原因。

配置缺少礼包、大奖、商品、道具，混用奖励组，天数不连续或窗口不足时，整期拒绝开放。进入和购买不额外要求个人赛季 ID，按这三张表的开关、时段、条件控制；个人切季不会重置当前绝对时间期次。

表中没有宝箱图标、大奖标题等资源字段，相应旧展示字段留空，客户端从自己的资源配置展示；新增 nameId、functionId 返回真实多语言 ID 与入口 ID，旧 name 字段返回名称 ID 字符串。

进行中的期次不要修改购买序列、奖励或结束时间，新一期使用新的活动 ID 或开始时间。已有支付预占和大奖快照用于迟到回调与到期补发。

## 客户端协议

消息类型沿用 `MessageTypeDef.SEASON`，下表为低位命令号，完整值由 SeasonConstant.MsgBean 定义。

| 请求 | 低位命令号 | 参数 |
| --- | --- | --- |
| ReqContinuousGiftList | 0x2B | activityId=0 查询入口列表；具体 ID 表示进入该活动并清除红点 |
| ReqContinuousGiftBuy | 0x2C | activityId、dayIndex；仅钻石/金币购买 |
| ReqContinuousGiftClaim | 0x2D | activityId；领取大奖 |
| ResContinuousGift | 0x2E | 统一响应；action=1 列表/进入，2 购买，3 领奖 |

所有时间戳均为毫秒。`activities` 包含 serverTime、startTime、endTime、nextPurchaseTime、redDot、宝箱列表、价格、奖励预览、渠道商品 ID 和大奖信息。使用 endTime-serverTime 展示倒计时。入口列表查询不要传具体活动 ID，否则会清除红点。

宝箱 `status`：0 前序未购买，1 可购买，2 已购买，3 等待次日，4 支付处理中，5 消费结果待核对。大奖 `grandStatus`：0 未解锁，1 可领取，2 已领取。

支付成功但奖励交付未完成时，购买响应仍表示该宝箱已经购买，`rewardPending=true`，`rewards` 为空。客户端不能再次扣款，提示奖励处理中；明确失败的交付到期补发，结果不确定的交付按下节对账。

现金礼包沿用 `ReqGenerateOrder`：rechargeType=20，productId=`activityId_dayIndex`，payType 沿用现有平台定义。客户端使用礼包详情的 channelProductId 发起支付。内部 productId 会替换成服务器预占 token，不能由客户端构造或复用别人的 token。重复预下单返回同一张订单；取消平台支付后可重试这张订单。支付到账后通过列表接口刷新进度。

## 持久化与故障处理

Mongo 集合 `continuousGift` 的 `_id` 为 `playerId:activityId:startTime`，保存已购天数、最近购买时间、已读状态、大奖状态、奖励快照、预占和交付记录。所有业务操作和补发均使用 `continuous-gift:playerId` Redis 锁。邮件业务键为 `continuous-gift:记录ID:day-N` 或 `:grand`，复用现有幂等邮件服务。

建议在部署数据库中建立索引：

```javascript
db.continuousGift.createIndex({settled: 1, endTime: 1, _id: 1})
```

交付状态包括 PENDING（明确待发）、DELIVERING（背包结果待核对）、BAG（已到背包）、MAIL（已补发邮件）；DEBITING 表示消费结果待核对。PENDING 会在到期扫描时重试邮件，失败保留记录，下轮继续处理。

现有背包扣款/发奖接口与 Mongo 进度之间没有跨存储事务，也没有业务幂等参数。为避免进程在扣款/发奖后崩溃引起双扣或双发，先落库意图；异常、空响应或宕机产生的 DEBITING / DELIVERING **不会自动重试**。需按 `记录ID:day-N` 或 `记录ID:grand` 查询道具流水后处理：

- DEBITING：确认未扣款才能删除该意图；已扣款则补齐已购天数和购买时间并转为 PENDING。
- DELIVERING：确认已完整发放改为 BAG；确认未发放改为 PENDING；部分发放先对齐剩余奖励。
- 不要仅因请求超时而删除记录、恢复购买资格或重新发放全部奖励。

这是现有背包基础设施下明确保留的对账边界，不能声称所有进程崩溃场景均可自动恢复。正常重复请求、重复回调和邮件任务重试有独立保护。

## 验证

2026-10-12 同步最新 dev 后，16、17、18 分别为 ACTIVE_PASS、SHOP_MINI、GOLD_TREASURE。连续礼包使用 20，小额抽奖使用 19；保留现有业务编号。礼包已适配 PreGenerateOrderData，并补齐协议入口、确定性订单键及独立渠道 SKU 存储，两个礼包均参与回归。


新增 ContinuousGiftServiceTest、ContinuousGiftConfigTest、ContinuousGiftOrderTest，覆盖顺序/每日限购、午夜切日、活动结束、参与条件、余额不足、大奖领取、防重复、红点、支付伪造、跨日/过期回调、订单重试、补发及异常保留；配置表使用实际 BaseCfgContainer 加载验证。

验证使用实际 Excel 解析器加载正式表，测试覆盖 8 期映射、价格引用、开关/条件、缺失引用、错误序列和支付类型不冲突。正式配置的五次现金支付及重复回调后仅发放五个礼包和一个大奖。2026-10-12 隔离四模块编译通过：连续礼包 29 项、小额抽奖 21 项，共 50 项测试通过，0 失败。

正常构建命令：

```powershell
mvn -o -pl sim -am test '-Dtest=ContinuousGift*Test' '-Dsurefire.failIfNoSpecifiedTests=false'
```

当前工作区既有的已删除配置类导致 core 全量编译失败，另有旧 SimOperationDashboardServiceTest 引用不存在字段。因此本次在临时副本中恢复 HEAD 的缺失配置依赖、仅编译上述新测试进行验证，未恢复工作区的删除。Java 25 运行仓库旧 Mockito 依赖时，临时验证使用 `-Dnet.bytebuddy.experimental=true`；项目目标仍是 Java 21。

尚未连接真实 Redis、Mongo 或支付沙盒做端到端验收；上线前需配置真实商品、数值和资源并完成客户端联调。

## 同步最新 dev 后的兼容调整

已合并 origin/dev 的 5206786ff。最新分支使用 GOLD_TREASURE(18)，连续礼包最终充值类型为 20，小额抽奖保留 19。预下单返回值已适配新的 PreGenerateOrderData，现金明确标记 MONEY，继续独立存储渠道 SKU。之前的连续礼包 18 编号不再使用。

## 提交前验证（2026-10-12）

提交前已再次合并 origin/dev（21bf8c692）。直接从暂存区导出待提交源码和配置，common、sampledata、core、sim 编译成功，两个礼包共 50 项测试通过。仅筛选礼包测试，未运行完整仓库测试；验证没有依赖工作区中其他未提交的配置生成改动。
