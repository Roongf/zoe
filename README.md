# Zoe · 钱多多

Zoe 项目中的「钱多多」Kotlin Multiplatform / Compose 实现。Android 和桌面共用界面与记账逻辑，账目通过 SQLDelight 写入本地 SQLite，不依赖登录或联网。

## 功能

- **外观**：蓝靛色 Material 3 主题，卡片使用分层底色，收入为青绿色、支出为玫红色。浅色与深色跟随系统设置。
- **流水**：启动默认查看当前月，按天列出账目；可切换月份，查看区间内的支出、收入与结余。
- **支出分类 Top 3**：在汇总卡片下显示本筛选区间支出最高的三个一级分类、金额及占比条。点分类可查看对应流水，子分类归入一级分类，未分类单列。
- **统计**：从首页点「统计」可查看六个月的收支对比，点月份切换总额和支出构成，点分类看明细。转账不计入收入和支出，空白月份按零展示。
- **记账**：新增、编辑和删除支出、收入、转账。支持二级分类、账户、日期和备注。转账从一个账户扣款、向另一个账户入账，不改变收入、支出或两个账户合计余额。
- **快捷录入**：新记账时优先聚焦金额，选分类后建议该分类最近一笔流水使用的有效账户；找不到同分类时用同类收支最近账户兜底。手动选择的账户不会被分类覆盖。常用分类直接选，全部分类、日期与备注按需展开。
- **连续记账与复制**：「保存并再记」保存当前账单并打开下一笔，保留收支类型与日期，清空金额、分类和备注。打开旧流水可点「复制账单」，原账单不变，新草稿默认用今天，确认后才保存。
- **流水搜索**：搜索全部日期，可按备注、分类、账户匹配，支持用空格组合关键词。有备注的流水优先显示备注。
- **账户**：默认提供现金、微信和支付宝。可以新增、编辑和删除账户，查看每个账户余额及净资产；删除账户不会删除历史账目。
- **时间筛选**：支持本月、上月、近 7 天、近 30 天、今年、全部，也可指定年月。

统计与明细之间的跳转参考 [Actual Budget 的报表下钻](https://actualbudget.org/docs/releases/)。连记、复制与账户建议分别参考 [钱迹的记账界面](https://docs.qianjiapp.com/ji-zhang-jie-mian/tian-jia-shou-zhi.html)、[Android 更新记录](https://docs.qianjiapp.com/change-log/change_log_android.html)和[默认账户说明](https://docs.qianjiapp.com/faq.html)，没有引用第三方代码。

## 构建和运行

使用 JDK 17、Android SDK Platform 35 和 Build Tools 34.0.0。将 SDK 路径写入 `ANDROID_HOME`，或在项目根目录的本地 `local.properties` 设置 `sdk.dir`。项目包含固定到 Gradle 8.10.2 的 Wrapper。

```bash
./gradlew :composeApp:run
./gradlew :composeApp:desktopTest :composeApp:assembleDebug :composeApp:assembleDemo
```

Windows 上用 `gradlew.bat` 代替 `./gradlew`。Android 调试 APK 位于 `composeApp/build/outputs/apk/debug/composeApp-debug.apk`。桌面界面测试需要图形环境；无显示器的 Linux 可使用 `SKIKO_RENDER_API=SOFTWARE xvfb-run -a ./gradlew :composeApp:desktopTest`，没有图形环境时只跳过界面测试，数据库测试照常运行。

## 真机体验包

`assembleDemo` 生成 `composeApp/build/outputs/apk/demo/composeApp-demo.apk`。桌面也可用 `./gradlew :composeApp:run --args="--demo --compact"` 体验，追加 `--dark` 或 `--light` 可固定预览模式。首次打开按设备日期生成包含当前月和前五个月的模拟账目，包含工资、房租、午餐、购物和账户间转账。无需手动录入。

- **体验包**：应用名「钱多多·体验」，Android ID `com.qingji.ledger.demo`，数据保存在独立数据库。测试记录可编辑，重新打开不会重复添加；在系统设置清除体验包的数据后，下一次启动会重新生成。
- **普通包**：应用 ID `com.qingji.ledger`，保持原有数据库，不填充模拟账目。体验包和普通包可以并排安装。
- **推荐试法**：打开「记一笔」，切换「午餐」和「公交地铁」看账户建议；输入金额后点「保存并再记」。也可以搜索「咖喱饭」，打开旧流水并点「复制账单」。
- **图标**：以提供的双熊原图作为 Android 彩色桌面图标，保留原图对白和构图，并提供自适应图标及旧版回退资源。系统主题图标由启动器处理。

## APK 复现范围

参考包是 `Qian-Duo-Duo.apk`，Android 应用 ID 为 `com.qianduoduo`，版本为 `0.1.0`。参考包 SHA-256 为 `c72d06a759268a3a28413fdeb1f25c12356f990b900a7227c4d8eda4a1d0e0eb`。本项目从编译产物核对了四个主要界面、预置账户和分类、主题、启动图标和数据库表结构；界面及业务逻辑以可编译、可维护的 Kotlin 源码重建，并非原 `.kt` 源文件的逐字恢复。

- **主要实现**：`composeApp/src/commonMain/kotlin/com/qianduoduo/`；SQLDelight 定义位于 `composeApp/src/commonMain/sqldelight/`。
- **SQLite**：金额按整数分保存。保留参考包的 `account`、`category`、`txn`、`budget`、`member` 表和索引；预算与成员没有在参考包中确认到入口，本次未新增对应界面。
- **Android 标识**：保留 Zoe 的 `com.qingji.ledger`，桌面文件为 `~/.zoe/qianduoduo.db`。Android 新版与参考 APK 是不同的应用，不读取或自动迁移参考应用的数据，也不包含任何既有账目。
- **历史原型**：`com/zoe/ledger` 下保留原有轻记原型及测试，两个平台入口均改为新版。原产品设计见 [轻记产品与数据设计](docs/qingji-product-design.md)。
