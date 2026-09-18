# 轻记：极速记账录入模块（v1）

> 目标：让已有分类的消费在 **3 秒内完成记录**。所有入口最终写入同一种 `transaction_record`，区别只保存在 `entry_source`（建议客户端本地字段）和审计事件中，避免三套数据逻辑逐渐分叉。

## 0. 页面与状态设计

首页顶部放置 AI 输入框，下面依次为「一键常用」和「本月概览」。底部中间的“记一笔”凸起按钮始终可见，打开半屏数字键盘。三种入口的优先级为：**AI 输入 > 快捷模板 > 手动键盘**。

```text
┌────────────────────────────┐
│  今天花了什么？说一句就行  🎙 │  ← AI 输入框（首页首屏）
├────────────────────────────┤
│  一键常用                    │
│ [ +15 早餐 ] [ +20 奶茶 ]    │
│ [ +38 午餐 ] [ +12 地铁 ]    │  ← 4–6 个模板
├────────────────────────────┤
│  本月支出 / 剩余预算 / 最近账单 │
└────────────────────────────┘
              ⊕ 记一笔            ← 半屏手动键盘
```

### 通用写入约定

| 字段 | 录入规则 |
| --- | --- |
| `ledger_id` | 当前选中的账本，所有入口必填。 |
| `paid_by` | AI 从句子识别；模板使用模板默认值；手动键盘使用最近一次值（家庭账本默认“共同”）。 |
| `transaction_participant` | 记录“谁一起消费”，可与 `paid_by` 不同。例如自己付款、自己和老婆参与。 |
| `occurred_at` | AI 解析相对日期；模板和手动键盘默认现在。 |
| `entry_source` | 建议本地同步实体增加 `ai` / `template` / `manual`，用于分析入口成功率和排序模板；不影响服务端核心账单表。 |

## 1. AI 一句话智能记账（首选）

### User Flow

1. 用户在首页输入框键入文字，或长按麦克风语音转写；输入框示例为“今天中午和老婆吃海底捞花了258元”。
2. 点击发送（或语音转写结束后自动发送），客户端立即展示 **解析中** 的小行内状态，输入框不可重复提交，但首页其他区域仍可操作。
3. API 返回结构化草稿：`¥258 · 餐饮 · 海底捞 · 今天 · 我、老婆`。客户端将草稿显示为单行确认卡。
4. **高置信度（≥ 0.85）**：用户点击“确认”即保存；同时提供“改一下”进入预填半屏表单。**低置信度、缺金额或分类**：不允许自动保存，直接打开预填手动键盘，并只高亮缺失/不确定字段。
5. 保存后立即在本地账单列表插入流水、Toast 显示“已记入家庭账本”，后台同步；用户可在 5 秒内点“撤销”。

**重要边界**：即使置信度高，v1 也不应在无任何可见反馈的情况下静默记账。首页确认卡只占一行，既不打断极速流程，也避免模型把“258”误认成金额或日期时产生不可见错误。

### 解析 API 契约

`POST /v1/ledgers/{ledgerId}/entry-drafts:parse`

```json
{
  "text": "今天中午和老婆吃海底捞花了258元",
  "locale": "zh-CN",
  "timezone": "Asia/Shanghai",
  "referenceTime": "2026-09-11T12:30:00+08:00",
  "members": [
    {"id": "u_me", "aliases": ["我", "自己"]},
    {"id": "u_partner", "aliases": ["老婆", "爱人", "太太"]}
  ],
  "categories": [
    {"id": "c_food", "name": "餐饮", "aliases": ["吃饭", "火锅", "外卖"]}
  ]
}
```

```json
{
  "amountCents": 25800,
  "entryType": "expense",
  "categoryId": "c_food",
  "merchant": "海底捞",
  "occurredAt": "2026-09-11T12:00:00+08:00",
  "paidByUserId": "u_me",
  "participantUserIds": ["u_me", "u_partner"],
  "confidence": 0.96,
  "needsConfirmation": false,
  "unresolvedFields": []
}
```

服务端只向模型传递**当前账本**的成员别名和分类白名单；模型只能返回给定 ID，不能自行创建成员、分类或调用工具。模型响应必须经过 schema 校验、金额范围校验和成员归属校验。原始句子最多保留在短期审计日志中，不应写入公开账单备注。

### Kotlin 伪代码 / 调用逻辑

```kotlin
@Serializable
data class ParsedEntryDraft(
    val amountCents: Long?,
    val entryType: String?,
    val categoryId: String?,
    val merchant: String?,
    val occurredAt: Instant?,
    val paidByUserId: String?,
    val participantUserIds: List<String>,
    val confidence: Float,
    val unresolvedFields: List<String>,
)

suspend fun captureWithAi(text: String, context: ParseContext) {
    val draft = api.parseEntryDraft(context.ledgerId, ParseRequest(text, context))
    val isComplete = draft.amountCents != null && draft.categoryId != null && draft.entryType != null
    if (isComplete && draft.confidence >= 0.85f && draft.unresolvedFields.isEmpty()) {
        uiState = QuickEntryState.ReadyToConfirm(draft) // 用户仍可一击确认
    } else {
        uiState = QuickEntryState.EditRequired(draft) // 打开预填手动键盘
    }
}

suspend fun confirm(draft: ParsedEntryDraft) {
    require(draft.amountCents != null && draft.amountCents > 0)
    val record = draft.toTransaction(source = EntrySource.AI)
    localStore.insert(record, syncState = SyncState.PENDING) // 先本地可见
    syncQueue.enqueue(record.id) // 后台上传，失败可重试
}
```

## 2. 快捷模板点击

### 模板生成与 User Flow

1. 客户端按最近 30 天流水聚合 `分类 + 金额档位 + 付款人`；同一组合至少出现 3 次才可推荐，且永不推荐金额异常的流水。
2. 首页显示 4 个、最多 6 个按钮；优先显示用户手动置顶模板，再显示推荐模板。文案为“+15 早餐”“+20 奶茶”，而非技术字段。
3. 点击按钮后**直接本地保存，无二次确认**；轻触反馈、Toast“已记：早餐 ¥15”，并显示“撤销”。长按模板才进入编辑。
4. 用户撤销、编辑或删除后，记录该模板的负反馈，降低排序；每周重新计算推荐，用户可在“我的 > 常用模板”固定、编辑、隐藏。

### Compose 组件设计

```kotlin
@Immutable
data class QuickTemplate(
    val id: String,
    val label: String,       // “早餐”
    val amountCents: Long,   // 1500
    val categoryId: String,
    val paidByUserId: String,
    val icon: String,
)

@Composable
fun QuickTemplateRow(templates: List<QuickTemplate>, onUse: (QuickTemplate) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(templates.take(6), key = { it.id }) { template ->
            AssistChip(
                onClick = { onUse(template) },
                label = { Text("${template.icon} +${template.amountCents / 100} ${template.label}") },
            )
        }
    }
}
```

`onUse` 不经过网络请求：调用 `localStore.insert(template.toTransaction(now()))` 后即可返回成功，网络同步是独立后台任务。这个限制是保证“点击即记”的关键。

## 3. 极简手动键盘

### User Flow（最多两步选择）

1. 点击底部大号“+”，从底部弹出占屏约 72% 的 `ModalBottomSheet`；金额默认 `0`，数字键盘立即获得输入焦点；上方同屏展示 8 个最常用支出分类。
2. 用户输入金额，并点击分类：系统**立刻保存**并关闭半屏。金额输入不计为额外点击，所以仅一次“分类”选择即可完成。
3. 如果用户先点分类，数字键盘保留，等待金额；金额有效后自动保存。长按分类或点击“更多”才展开全部分类、日期、付款人和备注等高级字段。
4. 默认值为“支出 / 今天 / 最近付款人”，避免任何默认弹窗；特殊情况（收入、历史日期、成员变更）才进入高级编辑。

### Compose 半屏组件代码

```kotlin
@Composable
fun ManualQuickEntrySheet(
    categories: List<CategoryUi>,
    onSave: (amountCents: Long, categoryId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf("0") }
    val cents = ((amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) * 100.toBigDecimal()).toLong()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(text = "¥${amount.ifBlank { "0" }}", style = MaterialTheme.typography.displayMedium)
        LazyVerticalGrid(columns = GridCells.Fixed(4)) {
            items(categories.take(8), key = { it.id }) { category ->
                FilledTonalButton(
                    onClick = { if (cents > 0) onSave(cents, category.id) },
                ) { Text("${category.icon}\n${category.name}") }
            }
        }
        NumberPad(value = amount, onValueChange = { amount = it })
    }
}
```

生产实现应以“分”为内部状态保存（例如 `amountCents: Long`），键盘输入只负责格式化显示，避免 `Double` 带来的金额精度问题。`onSave` 与模板相同：先写本地数据库、立刻关闭半屏、后台同步。

## 4. 前端状态、可访问性与埋点

- AI 状态：`Idle → Parsing → ReadyToConfirm | EditRequired → Saving → Saved | Error`。错误提示放在输入框下方，并允许“一键改用手动输入”。
- 离线状态：AI 输入显示“联网后可解析”；模板和手动键盘仍然完全可用并产生待同步流水。
- 可访问性：每个分类图标提供文本标签；语音按钮有“按住说话”描述；保存成功必须提供触感/语音读出之外的文字 Toast。
- 最小埋点：`ai_parse_requested/succeeded/edited/confirmed`、`template_used/undone`、`manual_opened/saved`，以及从打开到保存的耗时。不要记录完整原始语音和账单文本到产品分析平台。
