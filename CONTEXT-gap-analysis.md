# 爱发电网页端功能缺口分析

> 依据：爱发电网页前端产物 `app.99a09c1d.js`（Vue 2 / webpack，992 KB）反查，
> 对照本工程源码与 `CONTEXT-features.md` 现状。
> 结论以「网页端有、App 该有但还没有」为准。

## 一、产品边界（先立规矩）

这是第三方客户端，**不碰任何资金链路**。所有涉及付钱、收款、退款、账期的能力一律不做，
只负责把用户送到官方页面：

- 不做：下单、支付、购物车、收银台、优惠核销、续费/升级、自动扣款、提现、收款方式绑定、退款。
- 不做：任何形式的订单参数拼装（金额、方案 id、优惠码一律不由 App 传给官方）。
- 只做：识别「用户想买」的意图，打开对应的**官方 URL**，剩下的事交给网页。

这条边界一旦划清，剩下真正要补的缺口其实只有三块：**创作、社区、设置**。

## 二、一句话结论

App 现在是「只读浏览 + 只读式创作者后台」：

- 看动态、看创作者、看数据、看订单都能跑通；
- 但只要涉及**发东西、进社区、改设置**，就全部断在网页上。

交易不是缺口，是设计。真正欠的是**互动**（评论、作品集浏览）、
**社区**（电圈/众筹浏览）和**账号设置**（资料/绑定/地址）。
发动态属于「暂时不做」：优先级低，先不排期。

## 三、分析方法

| 维度 | 数据 |
|---|---|
| 前端接口路径 | 319 个 |
| 前端路由 | 125 条（含硬编码的创作者 slug 与兑换码短链） |
| 前端 i18n 模块 | 203 个（`BlockPayment` 59 条、`PlanEdit` 152 条为最重的两块） |
| App 实际调用接口 | 约 71 个（≈ 22%） |

网页端路由分五类，分类本身就是缺口地图（交易类已划出）：

- 公开浏览：`/` `/explore` `/search` `/p/:postId` `/a/:slug` `/album/:albumId` `/item/:planId` `/project/:id` `/group/:id` `/u/:userId` `/badge/:userId`
- ~~交易~~：`/cart` `/cashier` `/order/create` `/order/check` `/purchase/sku/:skuId` `/redeem/:id` `/bundle/:planId` —— **全部外跳**
- 创作：`/compose` `/edit/article` `/edit/album` `/edit/shop` `/edit/project` `/edit/aff`
- 创作者后台：`/dashboard/*` `/setting/*` `/withdraw` `/apply`
- 账号：`/login` `/reg` `/forgot` `/reset` `/bind` `/setting/account` `/setting/close-account`

## 四、外跳清单（要实现的是「跳得准」，不是「买得成」）

现有 App 已经有商品详情 Sheet（`ProductDetail`）和创作者主页，问题在于**买不到也跳不对**。
建议把这些入口统一收进一个「官方页面」注册表，用 Custom Tabs 打开，失败再降级到浏览器。

| 用户动作 | 外跳目标 |
|---|---|
| 点「发电 / 支持 TA」 | `https://afdian.com/a/{slug}/plan` |
| 点某个具体方案 | `https://afdian.com/item/{plan_id}` |
| 点捆绑包 | `https://afdian.com/bundle/{plan_id}` |
| 商品详情里点「购买」 | `https://afdian.com/item/{plan_id}`（带 SKU 时由网页自行处理） |
| 购物车 | `https://afdian.com/cart` |
| 付费动态「发电解锁」 | 直接开对应方案的 `item/{plan_id}`，不要只回创作者主页 |
| 兑换码链接 `/redeem/{id}` | 原样外跳 |
| 我发电的 →「续费 / 升级 / 取消续费」 | `https://afdian.com/dashboard/sponsoring` |
| 订单 →「取消 / 重新支付 / 改地址 / 确认收货」 | `https://afdian.com/dashboard/purchased` |
| 提现 / 收款方式 / 实名 | `https://afdian.com/withdraw`、`/setting/payment` |

实现上建议在 `AppLinkRegistry` 里加一个 `ExternalOnly` 类别：
明确标注「这个路径只许外跳」，以后新增页面时不会有人顺手把它做成原生下单页。
`item` / `bundle` 目前映射到原生商品详情 Sheet，**浏览可以留**，但购买按钮必须走外跳。

## 五、已覆盖的基线（不要重复建设）

| 领域 | 已实现 |
|---|---|
| 认证 | 密码登录（RSA + AES）、**验证码登录**（手机/邮箱 + 按需图形验证码）、Token Keystore 加密、免责声明、退出登录 |
| 首页 | 7 个可配置模块、快捷入口、提现提醒、首屏缓存 |
| 发现 | 动态流、全站精选、**创作者搜索**（`/api/creator/list?q=`）+ 搜索历史 |
| 动态详情 | HTML 正文渲染、付费态提示、上下篇、分享链接、评论只读 + 点赞 + 楼中楼 |
| 收藏 | 创作者 / 动态 / 众筹三个 tab |
| 个人侧 | 我发电的、订单（5 tab + 详情，只读）、浏览历史 |
| 创作者主页 | 封面、目标、方案、商品、动态、合集、赞助榜 |
| 创作者后台（读） | 数据卡 + 图表、收入明细、每月汇总、感谢页、收到发电（筛选/排序/搜索）、赞助者管理（筛选/排序/详情/拉黑） |
| 后台写操作 | 优惠码全套（增删改查/开关）、商品上下架 / 库存 / 排序、FAQ 增删改、Webhook + OpenToken、屏蔽拒绝名单 |
| 消息 | 会话列表、聊天、发送、FAQ 快捷回复、置顶、全部已读、喜欢/评论通知弹层 |
| 系统 | 主题/配色、存储管理、日志、接口调试、版本拦截 |

> 订单页只读是**正确设计**，不是缺口。可补的只有物流进度这类只读查询。

---

## 六、缺口总览

剔掉交易后，按「不做会卡住核心场景」排序：

| 优先级 | 缺口 | 影响 |
|---|---|---|
| **P1** | 评论发布 / 回复 / 删除 | 只能看不能说，社区互动缺一半 |
| **P1** | 注册 / 找回密码 | 新用户与忘记密码只能去网页 |
| **P1** | 电圈（浏览 / 加入 / 发帖 / 管理） | 社区能力为零 |
| **P1** | 众筹（详情 / 收藏 / 发起） | 收藏页的众筹卡片点不进去 |
| **P1** | 创作者配置写操作（方案、商品、目标、主页装扮） | 改价、加档位、上架商品都得开电脑 |
| **P2** | 账号资料、绑定换绑、注销、收货地址、通知隐私设置 | 设置页只有外观和开关 |
| **P2** | 音视频播放、搜索扩展、举报、分享图、会话删除 | 浏览体验与社区治理缺角 |
| **暂缓** | 发布动态 / 作品集管理 | 已明确优先级不高，先不排期 |
| **外跳** | 一切交易与资金 | 见第四节，只做跳转 |

> 验证码登录已完成（密码 / 验证码双 Tab + 按需图形验证码），从缺口里划掉。

---

## 七、详细清单

### 评论（P1）

- 发布 `POST /api/comment/publish`、回复、删除 `POST /api/comment/delete`。
- 现在只有只读 + 点赞 + 楼中楼，缺「说出来」这一半；`CONTEXT-roadmap.md` 已列。
- 顺带可补：圈主标识（App 目前只标「作者」）、热门评论「查看更多」、删除自己的评论。

### 发布动态与作品集（暂缓）

> 已明确优先级不高，先不排期。清单保留，等要做时按图索骥。

**发布动态**（`/compose`、`/edit/article`）

- 正文编辑器、多图上传、音频、视频（含云转码提示）、文章模式（5 万字符上限）。
- 查看权限：所有人可见 / 所有赞助者 / 所有会员（仅普通方案）/ 自选方案（一条动态最多绑 5 种方案）/ 售卖方案。
- 封面图、文字预览（付费前可见，最多 140 字）、加入作品集、发布。
- 草稿 `保存为草稿`、定时发布 `FeedTiming`。
- 附件 `UploadCloudFile`（受发布范围限制）。
- 接口：`POST /api/post/publish`、`/api/upload/common-pic`、`/api/upload/audio`、`/api/upload/video`、`/api/upload/get-sign`。

**动态管理**

- 编辑 / 删除 `POST /api/post/delete`
- 置顶 / 取消置顶 `POST /api/post/top`、`/untop`、`/top-group`、`/untop-group`
- 投票：`GET /api/post/get-vote`、`POST /api/post/cast-vote`、`/edit-vote`
  （单选 / 多选、截止日期、附加图片；发布后不可改）

**作品集（合集）**

- 创建 / 编辑 / 删封面 `POST /api/creator/edit-album`、`/del-album`、`/show-album`
- 动态与排序：`POST /api/creator/edit-album-post`、`/rem-album-post`
- 前台浏览：`GET /api/user/get-album-catalog`、`/get-album-info`、`/get-album-rec-list`
- App 现状：`album` 链接直接打开其中一条动态（`AppLinkRegistry` 里有 TODO），没有合集页。

> 若哪天要动这块，建议先做**只读合集页**（浏览收益远大于编辑），编辑仍留网页。

### 账号入口（P1，验证码登录已完成）

| 能力 | 接口 | 现状 |
|---|---|---|
| 密码登录 | `POST /api/passport/login` | ✅ 已有 |
| 验证码登录（手机/邮箱） | `POST /api/passport/send-ql-code`、`/quick-login` | ✅ 本次实现 |
| 图形验证码（按需） | 发码返回 `ec=201` + `data.captcha_img` | ✅ 本次实现 |
| 注册 | `POST /api/passport/register` | 无 |
| 找回密码 | `POST /api/passport/send-forget-code`、`/check-forget-code`、`/reset-password` | 无 |
| 微信 / QQ / Google 登录 | `/api/oauth/mp-code`、`/wx-code`、`/qq-code`、`/google-code` | 无 |
| 设置初始密码（验证码登录后） | — | 无（可留网页） |

> 实现要点：登录页用「密码 / 验证码」双 Tab；发码成功原地 60 秒倒计时；
> 服务端要求人机校验时，在验证码框下方就地展开图形验证码（点图片换一张），不弹二级弹窗。

### 电圈（P1）

爱发电的「社群」形态，和动态是两套东西：**所有人（含非赞助者）都能发帖**，付费圈靠绑定的方案进圈。

- 电圈主页 `/group/:groupId`：帖子流、回复、成员、圈主标识、我的帖子、发新帖。
- 加入 / 退出：`POST /api/user/join-group`、`/quit-group`、`/join-guild`、`/quit-guild`。
  —— 付费圈加入会触发发电，这里**必须外跳**；免费圈可直接原生加入。
- 我的电圈：`GET /api/my/joined-group`、`/api/user/get-group-list`、`/get-group`、`/get-group-feed`。
- 设置 `/setting/group`：免费圈 / 付费圈各限 1 个、绑定方案、封面、简介、开关：`/api/creator/edit-group`、`/open-group-switch`、`/get-group-status`、`/get-group-selected-plans`。
- 分享卡 `GroupShareCard`（邀请加入、扫码发电进入、成员数、帖子数）。
- 发现页入口：`Explore` 里的「我加入的电圈」。

### 众筹（P1）

App 只有收藏页的众筹卡片（`CrowdfundingCard`），点进去没有页面。

- 众筹详情 `/project/:id`：进度、目标金额、支持人数、剩余天数、额外目标、回报档位、预计发货时间。
- 收藏 / 取消收藏：`POST /api/user/mark-crowdfunding`、`/unmark-crowdfunding`。
- 列表：`GET /api/user/get-crowdfundings`、`/get-crowdfunding-info`、`/get-crowdfunding-plans`。
- 页面上的「支持」按钮 → **外跳** `project/{id}` 官方页（支持即下单）。
- 发起 / 编辑（创作者，配置性质）：`/api/creator/edit-crowdfunding`、`/delete-crowdfunding`、`/get-crowdfundings`、`/get-crowdfunding-basic-info`、`/get-crowdfunding-terms`、`/agree-crowdfunding-protocol`。
- 审核状态：草稿 / 待审核 / 通过 / 拒绝（`ProjectPreview`）。
- 收藏页的众筹卡片现在点不动，是最容易补的一个洞。

### 创作者配置写操作（P1）

App 的创作者后台是「看板」，网页是「控制台」。这里只列**配置类**能力，不含任何资金动作。

**赞助方案（`/setting/plan`）**

- 新建 / 编辑 / 删除 / 隐藏 / 置顶 / 置底 / 排序：`POST /api/creator/edit-plan`、`/delete-plan`、`/hide-plan`、`/show-plan`、`/edit-plan-rank`。
- 自选金额赞助开关与文案：`POST /api/creator/custom-plan-switch`。
- 付费周期：月 / 季 / 年（含季年折扣）、永久方案、独立方案（保存后不可改类型）。
- 库存与限购：型号 SKU、型号库存、商品限购 / 型号限购（每天 / 每周 / 每月 / 所有时间）、`POST /api/creator/edit-sku-stock`、`/edit-bundle-stock`、`/set-can-buy-hide`。
- 定时：定时上架 / 下架 / 开售 / 停售（`SheetPlanTiming`）。
- 自动回复与随机回复（兑换码 / 激活码场景）：`POST /api/creator/edit-auto-random-reply`。
- 关联动态、作品集、兑换链接（生成动作本身不涉及资金，可做）。

> 注意：`edit-plan` 一个接口同时承载方案与商品，字段含 `independent`、`permanent`、`sku`、
> `shipping_template_id`、`ticket_round_list`、`crowdfunding_id`、`purchase_limit_info`、
> `time_limit_*`、`timing_*`。拆开做会反复改同一个表单模型，建议一次建模到位。

**商品与电铺（配置）**

- 新建 / 编辑 / 复制商品与捆绑包：`POST /api/creator/edit-plan`、`ModalBundlePick`、`ModalBundleType`。
- 运费模板：`/api/creator/shipping-template`、`/shipping-template-list`、`/edit-shipping-template`、`/delete-shipping-template`、`/delete-area-shipping-template`。
- 电铺满额包邮：`/api/creator/free-shipping-set`、`/del-free-shipping-set`。
- 电铺开关与商品展示数：`/api/creator/show-shop`、`/show-product-count-set`。

**目标**

- `/api/creator/get-goals`、`/all-goals`、`/edit-goal`、`/delete-goal`、`/hide-goal`、`/show-goal`、`/goal-setting-product`、`/goal-setting-sponsor`。
- 三种目标：累计金额、发电人数、每月金额；额外目标（`ProjectGoalEdit`）。

**创作设置（主页装扮）**

- `/api/my/edit-basic`、`/api/creator/edit-basic`：昵称头像、正在创作、创作介绍、分类、创作规模、封面图。
- 主页设置：展示作品集、开电铺 tab、商品展示个数、展示发电榜。
- 隐私：`/api/creator/privacy-public-income`、`/privacy-public-sponsor`。
- 版权保护：禁止复制动态文字、禁止保存图片、水印（不启用 / 角标 / 全屏）。
- 品牌色：`/api/creator/edit-theme-color`、`/select-theme-color`。

**其他后台**

- 会员徽章：`/api/badge/edit-creator-badge`、`/get-creator-badge-list`、`/get-user-badge-list`、`/get-user-badge-detail`。
- 合作者（团队，最多 5 人）：`/api/creator/add-team-member`、`/edit-team-member`、`/delete-team-member`、`/get-team-members`、`/get-team-member-info`。
- Discord：`/api/creator/connect-discord`、`/api/user/connect-discord`、`/disconnect-discord`、`/get-discord-info`。
- 新手待办（`Todo`：传封面图、发公开动态、填收款方式…）：无接口，纯前端。
- 群发私信：`/api/my/send-msg-batch*` —— **按既有需求永久排除**。

### 账号与设置（P2）

App 设置页目前只有外观/配色、几个交互开关（过渡动画、手势返回、浏览历史、数据卡单位）、
存储管理、日志、接口调试与开源许可——没有任何**账号资料**相关的入口。

| 能力 | 接口 |
|---|---|
| 编辑资料（头像/昵称/性别/生日/邮寄地址） | `POST /api/my/edit-basic`、`/api/upload/avatar` |
| 绑定 / 换绑手机、邮箱（180 天限一次） | `/api/account/send-verify-code`、`/change-account-no`、`/send-change-account-no-code-*`、`/check-change-account-no-code`、`/has-verify-old-account` |
| 绑定 / 解绑微信、QQ、Google | `/api/account/bind`、`/unbind-wechat`、`/unbind-qq`、`/unbind-google` |
| 修改密码 | `POST /api/account/change-password` |
| 注销账号 / 撤销注销 | `/api/account/submit-cancellation`、`/check-cancellation`、`/cancel-cancellation` |
| 收货地址管理 | `/api/user/get-address`、`/edit-address`、`/set-default-address`、`/get-latest-address`；省市区街道级联 `/api/ship/provinces|cities|areas|streets` |
| 通知设置 | `/api/my/notify-config`、`/update-notify` |
| 隐私设置 | `/api/creator/privacy-public-income`、`/privacy-public-sponsor` |
| 语言切换 | 前端 i18n，无接口 |

> 实名认证涉及身份证与银行卡，归到「外跳」而不是自建表单。

### 浏览与互动体验（P2）

| 缺口 | 说明 |
|---|---|
| 音视频播放 | 正文里的 `<audio>` / `<video>` 目前没有播放器；网页支持云转码 |
| 搜索范围 | 现在只搜创作者（`/api/creator/list?q=`）。网页搜索页还有「我的发电记录」与众筹；创作者主页内另有独立的「搜动态或商品」 |
| 分类与频道 | 网页有 `/cartoon` `/music`、`category-list`、`category-creator`、`welcome/banners`、人气 / 推荐排序 |
| 首页动态筛选 | 「只看正在赞助的创作者」「不显示的创作者」：`/api/post/update-user-feed-setting`、`/get-blocked-creators` |
| 举报 | 动态 / 用户 / 评论：`/api/user/report-prepare`、`/report` |
| 分享图 | 网页可「保存为图片」生成分享卡（创作者、电铺、方案、电圈、感谢页）；App 只有系统分享链接 |
| 评论治理 | 删除自己的评论、圈主标识（App 只有「作者」标识） |
| 会话删除 | `POST /api/message/delete-dialog` |
| 工单 / 反馈 | `/api/message/work-order-options`、`/create-work-order` |
| 徽章展示 | 评论区、个人主页的会员徽章（`Avatar` 的徽章角标） |
| 用户主页 | `/u/:userId`（别人看到的「TA 发电的」） |
| 相关电圈 | 动态详情底部「相关电圈」 |
| 物流进度 | `GET /api/order/query-express-progress`（只读，可原生补，不涉及支付） |

### 明确不做 / 一律外跳

| 类别 | 内容 |
|---|---|
| 交易 | 下单、支付、购物车、收银台、优惠核销、捆绑包购买、票务购买、兑换码领取 |
| 订阅生命周期 | 续费、升级、取消续费、自动扣款、赞助关系解除 |
| 资金 | 提现、收款方式绑定、账单明细、实名认证（银行卡/身份证） |
| 平台侧 | 群发私信（既有需求已排除）、OAuth2 授权页、平台审核后台 |
| 端专属 | 小程序 / H5 专属支付兜底（`mini-checkout`、`wxjsurl`） |

> 原则：**App 不生成任何交易参数，只打开官方 URL。** 这样既不承担支付合规，也不会因为官方改参数而失效。

---

## 八、建议实施批次

按「一次能交付一个完整闭环」切分：

**已完成**
- 密码 / 验证码双 Tab 登录，含按需图形验证码与 60 秒重发倒计时。

**第一批：能互动**
1. 评论发布 / 回复 / 删除
2. 作品集浏览页（把 `album` 链接的 TODO 收掉）
3. 发动态暂缓，不从这一批走

**第二批：能进来**
4. 注册 / 找回密码
5. 账号资料、绑定换绑、收货地址

**第三批：能社交**
6. 电圈浏览 / 免费圈加入 / 发帖 / 我的电圈
7. 众筹详情与收藏（支持按钮外跳）

**第四批：创作者能自理**
8. 赞助方案编辑（价格 / 档位 / 库存 / 上下架，先不做 SKU 与票务）
9. 商品新建 / 编辑 + 运费模板
10. 目标设置、创作设置、会员徽章

**第五批：体验补齐**
11. 音视频播放、搜索扩展、举报、分享图

**随时可做（成本低、收益直接）**
- 把「发电 / 购买 / 续费 / 提现」统一切到官方 URL 的外跳注册表；
- 付费动态的「发电解锁」直接开对应 `item/{plan_id}`，不再只回创作者主页。

---

## 九、附录：接口分组

**需要自建（非资金）**

- 互动：`comment/{publish,delete}`
- 创作（暂缓）：`post/{publish,delete,top,untop,top-group,untop-group,get-vote,cast-vote,edit-vote,update-user-feed-setting,get-blocked-creators}`、album 系列、`upload/{common-pic,audio,video,avatar,get-sign}`
- 账号（验证码登录已完成）：`passport/{register,send-forget-code,check-forget-code,reset-password}`、`account/{bind,change-account-no,change-password,send-verify-code,submit-cancellation,cancel-cancellation,unbind-*}`、`oauth/{mp,wx,qq,google}-code`、`user/{get-address,edit-address,set-default-address}`、`ship/*`
- 社区：`user/{get-group,get-group-list,get-group-feed,join-group,quit-group,join-guild,quit-guild}`、`my/joined-group`、`user/{get-crowdfunding-info,get-crowdfundings,mark-crowdfunding,unmark-crowdfunding}`
- 创作者配置：`creator/{edit-plan,delete-plan,hide-plan,show-plan,edit-plan-rank,edit-sku-stock,edit-bundle-stock,edit-auto-random-reply,custom-plan-switch,set-can-buy-hide,edit-goal,delete-goal,hide-goal,show-goal,goal-setting-*,edit-basic,edit-theme-color,select-theme-color,privacy-public-*,edit-group,open-group-switch,edit-crowdfunding,shipping-template*,free-shipping-set,show-shop,show-product-count-set,make-redeem,redeem-history,submit-certification,check-certification,add-team-member,edit-team-member,delete-team-member,connect-discord}`、`badge/*`、`my/{notify-config,update-notify}`
- 互动：`user/{report,report-prepare}`、`message/{delete-dialog,create-work-order,work-order-options}`
- 只读增强：`order/query-express-progress`、`order/express-company-list`

**一律外跳（不实现）**

- `cart/*`、`order/{prepare,create-order,check,cancel-order,sign,update-address,update-remark,transfer-pay-url,tickets,ticket-checkin}`、`paypal/*`
- `user/{redeem-info,use-redeem}`、`my/{apply-withdraw,withdraw-history,change-withdraw-type,bill-history}`、`creator/{withdraw-method,change-default-withdraw-method}`、`user/{idcard,idcard-validate,edit-idcard,all-idcards,delete-idcard}`、`my/{get-real-name,edit-real-name}`、`suiyinzi/*`、`bang/*`
- `my/{send-msg-batch,send-msg-batch-progress,check-send-msg-batch}`
- `oauth2/*`、`mini-checkout` 相关

---

## 十、备注

- 网页端大量后台页共用同一批接口（`creator/all-plans` 同时供商品、方案、筛选档位使用），
  App 侧已按这个思路抽出 `ManagementService` / `ShopService`，新增功能优先复用而不是新写一套。
- 「配置」与「交易」要分清：改价格、改库存是配置，可以做；下单付款是交易，一律外跳。
  这两件事在 `edit-plan` 里挨得很近，别顺手把购买按钮写进表单。
- 票务、捆绑包、限购、定时上下架是 `PlanEdit` 里相互耦合的四块，
  拆开做会反复改同一个表单模型，建议一次建模到位。
