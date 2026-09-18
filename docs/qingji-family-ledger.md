# 轻记：家庭共享与账本模块（v1）

## 1. 账本模型与切换

每个新用户创建后，系统自动建立一个仅自己可见的 **“个人账本”**，并写入自己为 `owner` 的成员记录。用户可以在“我的 > 当前账本”创建一个 **“家庭账本”**；家庭账本不会移动、混合或删除个人账本的流水。

```text
我的
└─ 当前账本：个人账本 ▼
   ├─ 个人账本（仅我）
   ├─ 我们的小家（2 人）
   └─ + 创建家庭账本
```

### 多账本隔离规则

- App 全局保存 `activeLedgerId`，首页、账单、快捷模板、预算和 AI 上下文的每次读取都强制带上该 ID。
- 服务端通过 `ledger_member` 校验用户是否属于目标账本；禁止用单个流水 ID 直接读取或写入流水。
- 个人账本不可邀请其他人；创建家庭账本时自动写入创建者的 `owner` 成员关系，并复制常用分类。账本切换只改变视图，不复制历史流水。
- 账本名和成员数在首页顶部可点击切换；切换后显示当前账本的骨架屏，避免用户误以为数据被清空。

## 2. 家庭邀请：微信分享与二维码

### 邀请创建流程（邀请方）

1. 在“我的 > 当前账本 > 成员”点击 **邀请家人**；个人账本先提示“创建家庭账本”。
2. 选择成员默认角色：`成员`（可记账、编辑自己记录）或 `只读`（仅查看）；v1 不提供可编辑他人账单的权限。
3. 客户端请求 `POST /v1/ledgers/{ledgerId}/invitations`；服务端确认当前用户为 owner，生成随机、不含账本信息的 `invite_token`，默认 **24 小时有效、一次性使用**。
4. 分享面板显示两种等价载体：**微信分享**（Universal Link / 小程序链接）和 **保存二维码**（内容为 HTTPS 邀请链接）。页面同步展示到期时间和“撤销邀请”。
5. 邀请方在成员列表看到“等待加入 · 还剩 23 小时”，可重新分享或撤销；不得显示被邀请人的手机号等隐私信息。

```text
成员管理 → 邀请家人 → 生成一次性邀请
                                  ├─ 微信：轻记邀请你加入「我们的小家」
                                  └─ 二维码：https://qj.app/i/<opaque-token>
```

### 被邀请方加入流程

1. 家人从微信或相册扫码打开邀请链接。如果没有登录，先进行手机号/微信登录，然后**回到同一邀请上下文**。
2. App 调用 `GET /v1/invitations/{token}`，只展示账本名称、邀请人昵称和角色，例如“阿明邀请你加入「我们的小家」”。
3. 用户点击 **加入家庭账本**。服务端在一个事务中验证 token 的状态/过期时间、写入 `ledger_member`、把邀请状态改为 `accepted` 并记录 `accepted_by`；重复点击必须幂等。
4. 成功页提供“去记一笔”；客户端把新账本设为 active，拉取流水和分类。邀请方通过实时事件或下次同步看到成员头像进入列表。
5. token 失效、撤销、或用户已是成员时，显示明确状态和“返回账本列表”，而不是泛化网络错误。

### 安全与接口边界

```http
POST /v1/ledgers/{ledgerId}/invitations
→ 201 { token, inviteUrl, expiresAt, qrCodePayload }

GET /v1/invitations/{token}
→ 200 { ledgerName, inviterDisplayName, intendedRole, expiresAt }

POST /v1/invitations/{token}/accept
→ 200 { ledgerId, memberId }
```

- Token 用至少 128 位熵的随机值生成，只存 hash；链接中不放账本 ID、成员 ID 或手机号。
- `accept` 以 `token + 当前登录用户` 为幂等键；全流程用 HTTPS，二维码只编码邀请 URL。
- owner 可以撤销未接受邀请；成员离开账本后其历史流水保留头像快照和昵称快照，避免历史列表出现“未知用户”。

## 3. 家庭流水列表：成员清晰可辨，但不喧宾夺主

### 列表行组件

一行流水固定采用「分类图标 + 商户/备注 + 日期/付款信息 + 金额」结构。成员身份放在第二行、金额左侧：**小头像 + “小明记 · 我付款”**。共同消费增加“共同”胶囊，不使用整行背景色区分，以保护可读性和无障碍对比度。

```text
┌────────────────────────────────┐
│ 🍲  海底捞                  -¥258 │
│     [小明头像] 小明记 · 我付款 ·  今天 │
│     [共同]  我、小红参与              │
├────────────────────────────────┤
│ 🥛  早餐                     -¥15 │
│     [我的头像] 我记 · 我付款 · 昨天    │
└────────────────────────────────┘
```

| 视觉元素 | 规则 |
| --- | --- |
| 记账人头像 | 24dp 圆形头像，紧靠“某某记”；加载失败显示用户名首字母和稳定背景色。 |
| 付款人与记账人 | 相同则压缩为“阿明记”；不同时显示“阿明记 · 小红付款”，避免把垫付误解为个人开销。 |
| 共同参与 | 仅当参与人超过 1 位时显示“共同”胶囊和至多两个头像；更多人数写“等 3 人”。 |
| 成员筛选 | 账单顶部 Filter Chip：`全部`、`我记的`、`我付款的`、每位家庭成员；筛选状态常驻并支持一键清除。 |
| 成员色彩 | 只用于头像环和图例，不能作为区分成员的唯一方式；文字名称必须同时出现。 |

### Compose 列表行伪代码

```kotlin
@Composable
fun FamilyTransactionRow(item: FamilyTransaction, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.merchantOrNote) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(item.createdBy, size = 24.dp)
                Text(if (item.createdBy.id == item.paidBy.id) "${item.createdBy.name}记" else "${item.createdBy.name}记 · ${item.paidBy.name}付款")
                Text(" · ${item.relativeDate}")
            }
        },
        leadingContent = { CategoryIcon(item.category) },
        trailingContent = { Text(item.signedAmount, fontWeight = FontWeight.SemiBold) },
        modifier = Modifier.clickable(onClick = onClick),
    )
    if (item.participants.size > 1) ParticipantSummary(item.participants)
}
```

## 4. 可选轻量垫付与月底平账

### 记账方式

在 AI 确认卡和手动键盘的“更多”区域放置开关 **“我为家庭垫付”**，默认关闭。打开时默认按参与人数均分：例如海底捞 ¥258，付款人为我、参与人为我和老婆，则写入两条 `transaction_share`：我应承担 ¥129、老婆应承担 ¥129。金额不能整除时，将 1 分依次分配给付款人优先的成员，保证分摊总和恒等于账单金额。

这不是复杂 AA 分账：v1 只支持均分和“我承担全部”两个选项，不支持百分比、自定义金额、跨账本或多币种。非垫付账单不参与平账计算。

### 月底净额算法

对选中月份的每条已同步、未删除垫付流水计算：付款人对每个参与者有 `owed_cents` 的应收；再将两人之间的双向金额抵消，保留净额。

```kotlin
// payer 实付全额，participant 应承担 owedCents；payer 本人的份额不会形成欠款。
for (record in monthAdvanceRecords) {
    for (share in record.shares.filter { it.userId != record.paidByUserId }) {
        receivable[record.paidByUserId, share.userId] += share.owedCents
    }
}
val net = receivable[a, b] - receivable[b, a]
// net > 0: b 应给 a net；net < 0: a 应给 b -net
```

首页家庭账本的预算卡底部显示一行“本月平账：小红应给我 ¥129”，点击进入只读的垫付明细和“标记已结清”按钮。**标记结清只记录 `settled_at` 和操作者，不自动生成一笔收入/支出**，防止污染日常收支分析。

## 5. 验收标准

1. 新用户必定拥有且只能由自己访问一个个人账本；创建家庭账本后两者的流水、分类、预算完全隔离。
2. 从“邀请家人”到微信/二维码分享不超过两步；被邀请人登录后一次点击即可加入。
3. 家庭账单每一行在不依赖颜色的情况下都能读出记账人、付款人和共同参与人。
4. 夫妻双方相互垫付后，月底只展示抵消后的一个净额，并可追溯到构成该净额的账单。
