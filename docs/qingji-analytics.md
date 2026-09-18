# 轻记：轻量数据统计模块（v1）

## 1. 统计目标与入口

统计只回答“钱花哪了”，不引入资产、负债、收益率或复杂报表。入口放在**首页的本月支出卡片和分类 Top 3**：点击后进入“本月去向”页。该页顶部只有账本切换与月份切换，内容顺序固定为：分类占比、成员开销（仅家庭账本）、消费日历。

所有统计 API 都必须带 `ledgerId`、`month` 与用户时区；只聚合 `entry_type = expense`、`deleted_at is null` 的流水。饼图、柱状图和日历点击均导航至同一个预筛选账单页，而非复制一套详情页面。

```text
首页「本月支出」 → 本月去向
  ├─ 饼图：餐饮 42% ──> 账单（本月 + 餐饮）
  ├─ 成员柱状图 ─────> 账单（本月 + 付款人）
  └─ 消费日历 ───────> 账单（所选日期）
```

## 2. 图表库选型

| 场景 | 推荐 | 原因 | 不推荐作为主实现的原因 |
| --- | --- | --- | --- |
| Kotlin Multiplatform Compose 原生 App | **自绘 Compose Canvas + `drawArc`/`drawRect`**（v1） | 三种图仅需饼图、柱、日历格；零 WebView、离线可用、主题/无障碍/点击事件统一，安装包与依赖最小。 | 为两个轻量图引入大型图表库会牺牲启动与维护成本。 |
| 未来 Web 管理台或需要复杂交互 | **Apache ECharts** | 内置饼图、柱状图和 calendar heatmap； tooltip、legend、dataZoom、click 事件成熟，配置式开发快。 | 在 Compose 原生端需 WebView 或桥接，手势、深色主题、离线包和无障碍需要额外处理。 |
| 纯 Web 小型页面 | **Chart.js** | 包体相对轻，基础饼图和柱状图快速。 | 核心不原生提供 ECharts 那样完整的 calendar heatmap；需要额外插件或自行绘制，故不适合本需求优先选型。 |

**结论**：轻记的 Kotlin Multiplatform Compose 移动端 v1 采用原生 Canvas；若后续增加 Web 端运营/数据页，复用下文 ECharts option。不要在同一移动端页面混用 Compose Canvas 与 WebView 图表。

## 3. 接口与聚合数据

```http
GET /v1/ledgers/{ledgerId}/analytics/monthly?month=2026-09&timezone=Asia/Shanghai
```

```json
{
  "currency": "CNY",
  "month": "2026-09",
  "totalExpenseCents": 615400,
  "categories": [
    {"categoryId": "food", "name": "餐饮", "color": "#F58A6D", "amountCents": 258000},
    {"categoryId": "home", "name": "居住", "color": "#526D82", "amountCents": 220000}
  ],
  "memberExpenses": [
    {"userId": "u_me", "name": "老公", "amountCents": 300000},
    {"userId": "u_partner", "name": "老婆", "amountCents": 315400}
  ],
  "dailyExpenses": [
    {"date": "2026-09-10", "amountCents": 25800, "transactionCount": 2}
  ]
}
```

- 分类为空或金额为 0 时展示“本月还没有支出”，不渲染空饼图。
- 成员柱状图按 `paid_by` 聚合，标题明确写为“谁付款了”；“谁参与消费”属于账单详情/垫付视角，不能和付款混为一谈。
- `dailyExpenses` 必须按**账本时区**而非 UTC 截断日期，避免深夜记账落在错误日期。

## 4. 月度分类饼图

### 交互规则

1. 显示金额最多的前 5 个分类，其余合并为“其他”；右侧列表始终显示分类名、金额和百分比，不能只依赖色块。
2. 点击扇区或列表行，扇区外扩 6dp，并跳转 `账单?month=2026-09&categoryId=food`。
3. 圆心显示“本月支出”和总金额；切换月份使用 150–200ms 渐变，不用炫目旋转动画。

### ECharts 基础配置

```ts
import * as echarts from "echarts";

const chart = echarts.init(document.getElementById("category-pie")!);
const categories = [
  { id: "food", name: "餐饮", amountCents: 258000, color: "#F58A6D" },
  { id: "home", name: "居住", amountCents: 220000, color: "#526D82" },
  { id: "travel", name: "交通", amountCents: 73400, color: "#91B3A6" },
];
const totalCents = categories.reduce((sum, item) => sum + item.amountCents, 0);

chart.setOption({
  tooltip: { trigger: "item", valueFormatter: value => `¥${Number(value) / 100}` },
  title: { text: "本月支出", subtext: `¥${totalCents / 100}`, left: "center", top: "38%" },
  series: [{
    type: "pie", radius: ["58%", "82%"], padAngle: 2, selectedOffset: 6,
    label: { show: false },
    data: categories.map(item => ({
      id: item.id, name: item.name, value: item.amountCents,
      itemStyle: { color: item.color },
    })),
  }],
});
chart.on("click", params => navigate(`/ledger?month=2026-09&categoryId=${params.data.id}`));
```

### Compose 原生绘制要点

在 `Canvas` 内将每个分类的 `amountCents / totalCents` 转为 sweep angle，使用 `drawArc(useCenter = false)` 绘制圆环。保存每段 `(startAngle, sweepAngle, categoryId)` 的命中区间；点击时用触点相对圆心的角度找到分类，再调用同一 `navigateToLedger(Filter(categoryId))`。旁边仍使用普通 Compose 列表渲染图例与百分比。

## 5. 成员付款柱状图

仅在家庭账本有至少两名可见成员时显示。最多显示 6 位成员，按金额降序排列；横轴是姓名，纵轴是人民币金额，柱顶必须显示金额文字。点击柱子跳转 `账单?month=2026-09&paidBy={userId}`。个人账本隐藏此卡片，不留空白占位。

```ts
chart.setOption({
  xAxis: { type: "category", data: members.map(it => it.name), axisTick: { show: false } },
  yAxis: { type: "value", axisLabel: { formatter: value => `¥${value / 100}` } },
  tooltip: { trigger: "axis", valueFormatter: value => `¥${Number(value) / 100}` },
  series: [{ type: "bar", data: members.map(it => it.amountCents), barMaxWidth: 32, itemStyle: { borderRadius: [8, 8, 0, 0], color: "#295C49" } }],
});
```

## 6. 日历热力图（流水视图）

### 视觉与交互规则

- 使用当前月 7 列日历，每格至少 40dp；有消费的日期在右上角放置小红点，背景深浅映射**当日总支出**。
- 色阶必须提供 4 档并附“少 / 多”文字图例：`#F7EFEC`、`#F5C8BC`、`#EE947F`、`#D8563D`。红点表达“有流水”，深色表达金额，二者不可互相替代。
- 点击日期后以 Bottom Sheet 展示当天的流水明细、总支出和“查看全部”；未来日期禁用，零支出日期可点击但显示空状态。
- 计算色阶时取当月非零日金额的 P75 作为最高阈值，超过 P75 的日子都用最深色，避免房租等单笔大额消费导致其他天全部看不出差异。

### ECharts calendar heatmap 基础配置

```ts
const days = [
  ["2026-09-01", 0],
  ["2026-09-10", 25800],
  ["2026-09-11", 12600],
] as Array<[string, number]>;
const maxCents = percentile(days.map(([, cents]) => cents).filter(Boolean), 0.75) || 1;

calendarChart.setOption({
  visualMap: {
    min: 0, max: maxCents, calculable: false, orient: "horizontal", left: "center", bottom: 0,
    inRange: { color: ["#F7EFEC", "#F5C8BC", "#EE947F", "#D8563D"] },
    formatter: value => `¥${Number(value) / 100}`,
  },
  calendar: {
    range: "2026-09", cellSize: [42, 42], top: 34, left: 18, right: 18,
    dayLabel: { firstDay: 1, nameMap: ["日", "一", "二", "三", "四", "五", "六"] },
    monthLabel: { show: false }, yearLabel: { show: false },
  },
  series: [{ type: "heatmap", coordinateSystem: "calendar", data: days }],
});
calendarChart.on("click", params => openDaySheet(params.data[0]));
```

ECharts 的 heatmap 负责颜色深浅；小红点应通过 `calendarChart.on("finished")` 后计算每个有流水日期的 cell 坐标，再以覆盖层 DOM/Canvas 标记，或在 Compose 原生版本中直接在每格 `Canvas` 上绘制。不要把“金额为 0”的格子也画红点。

## 7. 验收标准

1. 用户从首页点击分类、成员或日期，均能进入已带正确筛选条件的账单明细。
2. 饼图前五分类与列表金额之和等于本月支出；“其他”可展开查看包含的分类。
3. 家庭成员柱状图清楚表述“付款额”，不会被理解为最终分摊额。
4. 日历任意有流水日期同时具备红点和正确的金额深浅；房租等异常大额不使其他消费日全部显示为最浅色。
