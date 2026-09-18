# 轻记

一个为个人与核心家庭成员设计的 Kotlin Multiplatform Compose 记账原型。它采用**本地优先**的领域模型，并通过 `LedgerRepository` 隔离数据源：当前使用示例内存数据，未来可无缝替换为 SQLDelight 本地库及云同步实现。

## 已实现

- 收入、支出、转账三种流水及共享/个人归属；
- 本月收入、支出、结余、预算使用情况和近 6 个月现金流；
- 支出分类占比与按成员拆分，便于分析家庭消费结构；
- 账本页过滤记录、添加流水弹窗及分类选择；
- 为同步预留的 `LedgerRepository` 接口和稳定的领域 ID。

产品定位、移动端信息架构和可落地的 PostgreSQL 表结构见 [轻记产品与数据设计](docs/qingji-product-design.md)、[极速记账模块设计](docs/qingji-entry-module.md)、[家庭共享与账本设计](docs/qingji-family-ledger.md)、[轻量统计模块设计](docs/qingji-analytics.md) 与 [初始化数据库脚本](docs/sql/001_initial_schema.sql)。

## 运行

需要 JDK 17+：

```bash
gradle :composeApp:run
```

> 初始项目不带 Gradle Wrapper；如需固定构建环境，请使用本地 Gradle 8.10+ 生成 wrapper。

### Android APK

安装 Android SDK Platform 35 并配置 `ANDROID_HOME`（或在 `local.properties` 设置 `sdk.dir`）后，使用 JDK 17+ 执行：

```bash
gradle :composeApp:assembleDebug
```

生成的 APK 位于 `composeApp/build/outputs/apk/debug/composeApp-debug.apk`。Android 端会自动采用紧凑布局：底部导航、底部居中的新增按钮和纵向概览卡片；桌面端仍保持侧栏布局。
