# 拾页 · 阶段记录

用途：每个阶段留下改动清单、构建结果、已实现/未实现，方便后续改代码时回溯和定位问题。
规格依据：`完整项目内容.txt`；UI 依据：`local_diary_ui_prototype.html`。

---

## 环境基线（本机，2026-10-15 实测）

| 项 | 值 |
|---|---|
| JDK | `D:\Android\Android studio\jbr`（OpenJDK 21.0.9，不在 PATH，构建需显式指定 JAVA_HOME） |
| Android SDK | `D:\Android\SDK`；仅 `android-36.1`（API 36）、build-tools 36.1.0 / 37.0.0、platform-tools；**无 cmdline-tools** |
| Gradle | 9.3.1（wrapper 指向本地 zip，离线可用） |
| AGP | 9.1.1 |
| Kotlin | **2.3.10**（AGP 9 自带 2.2.10，因 KSP 冲突在根 buildscript 里升到 2.3.10，KSP 同步升到 2.3.6） |
| minSdk / targetSdk / compileSdk | 26 / 36 / `release(36){minorApiLevel=1}` |

依赖解析：官方源 `google()` + `mavenCentral()` 为主，`settings.gradle.kts` 中追加阿里云镜像兜底。
已知的坑：本机出现过直连 `repo1.maven.org` 与 `dl.google.com` 超时的情况（关 VPN 时），
此时 Gradle 会依次重试到镜像源。官方源可用时镜像不会被访问。

构建命令（本机，命令行方式）：

```bash
cd /e/working/journal_app
export JAVA_HOME="D:/Android/Android studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew assembleDebug --console=plain
```

在 Android Studio 中：`File > Open` 选本目录 → Gradle Sync → `Run 'app'`。
注意 AS 的 Gradle JDK 需指向 `D:\Android\Android studio\jbr`（或任意 JDK 21）。

---

## 阶段 0 — 改造空项目（工具链跑通）

目标：把 Android Studio 生成的空项目改造成 Compose + Room 工程并**真实编译通过**。
本阶段不写任何业务功能。

### 改动文件

| 文件 | 改动 |
|---|---|
| `gradle/libs.versions.toml` | 新增 Kotlin / KSP / Compose BOM / Room / Lifecycle / Activity-Compose / serialization 版本与插件声明；删除 appcompat、material（改 Compose 后不再需要） |
| `settings.gradle.kts` | 追加阿里云镜像兜底（带说明注释）；rootProject.name 改为「拾页」 |
| `app/build.gradle.kts` | 加 kotlin-android / kotlin-compose / kotlin-serialization / ksp 插件；`namespace`+`applicationId` 改 `com.shiye.journal`；minSdk 24→**26**；JVM 目标 11→17；开 `compose = true`；配置 Room schema 导出到 `app/schemas`；替换依赖 |
| `app/src/main/res/values/strings.xml` | `app_name` → 拾页 |
| `app/src/main/res/values/themes.xml`、`values-night/themes.xml` | 老 MaterialComponents 主题 → 无 ActionBar 的 `Theme.Shiye`（颜色交给 Compose） |
| `app/src/main/res/values/colors.xml` | 删除（紫色色板不再使用） |
| `app/src/main/AndroidManifest.xml` | 声明 `MainActivity`；`allowBackup="false"`；主题改 `Theme.Shiye`；加 `windowSoftInputMode=adjustResize`；移除未使用的 tools 命名空间 |
| `app/src/main/res/xml/backup_rules.xml`、`data_extraction_rules.xml` | 排除 database / sharedpref / file / external，禁止系统自动备份与设备迁移带走业务数据（规格 7.5） |
| `app/src/main/java/com/shiye/journal/MainActivity.kt` | 新增，Compose 入口 |
| `app/src/main/java/com/shiye/journal/ShiyeApp.kt` | 新增，**临时占位**（居中显示「拾页」），仅用于验证工具链，阶段 1 会被真实骨架替换 |
| `app/src/test/java/com/example/...`、`app/src/androidTest/java/com/example/...` | 删除（包名 `com.example.journalapp` 已失效） |

### 构建结果（真实执行）

**`./gradlew assembleDebug` → BUILD SUCCESSFUL，耗时 6 分 3 秒**
产物：`app/build/outputs/apk/debug/app-debug.apk`，**12 MB**（debug 未混淆，规格目标是 release 约 15 MB 左右）。

`./gradlew buildEnvironment` 核对：`org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10` 生效，
其 BOM 内含 `compose-compiler-gradle-plugin:2.3.10`，与 KSP 2.3.6 同线。

### 踩过的坑（按时间顺序，都是真实报错，不是推测）

| # | 报错 | 原因 | 处理 |
|---|---|---|---|
| 1 | `Error resolving plugin [id: 'org.jetbrains.kotlin.android'] ... already on the classpath with an unknown version` | AGP 9 起内置 Kotlin 支持，不能再显式声明该插件 | 删掉 `kotlin-android` 插件声明（含版本目录条目） |
| 2 | `Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with built-in Kotlin`（KSP 生成目录） | AGP 9.1.1 内置的 KSP 2.2.10-2.0.2 仍用旧 DSL 注册生成源码 | 两条官方路：加 `android.disallowKotlinSourceSets=false` 抑制（治标，AGP 10 移除）或升到 KSP 2.3.6+（治本）。**选后者**，通过根工程 buildscript classpath 升级到 Kotlin 2.3.10 + KSP 2.3.6 |
| 3 | `Plugin [id: 'org.jetbrains.kotlin.plugin.compose'] was not found` | `plugins{}` DSL 只从插件仓库解析，读不到父工程 buildscript classpath；改为 `apply()` 后又因 classpath 上只有实现构件、没有插件标识（marker）构件而找不到 | 在根工程 buildscript 里补齐三个 marker 构件（`*.gradle.plugin`），模块里用 `apply(plugin = ...)` |
| 4 | `Unresolved reference 'ksp'` | `apply()` 方式不生成 Kotlin DSL 类型安全访问器 | 改用 `extensions.configure<KspExtension>("ksp")` 与 `dependencies { add("ksp", ...) }` |
| 5 | 依赖要求 `compileSdk` ≥ 37，本机只有 `android-36.1` | Compose 1.12.1 / Lifecycle 2.11.0 等新版依赖已要求 API 37 | 安装 `platform-37.2`（命令行下载 + 解压到 SDK），`compileSdk = release(37){minorApiLevel=2}` |

环境副作用（需要知道）：为装平台包，在 `D:\Android\SDK\cmdline-tools\latest` 放了命令行工具
（其自带 sdkmanager 版本太旧、读不懂新版仓库 XML，最终改用直接下载 platform 包解压的方式，
命令行工具保留备用）。SDK 新增 `platforms\android-37.2`。

### 已实现 / 未实现

- 已实现：工程按规格要求**真实编译通过**并产出 APK；包名 `com.shiye.journal`、应用名「拾页」、
  minSdk 26、targetSdk 36、compileSdk 37.2、主题与自动备份排除规则就位；Compose / Room(KSP) /
  serialization 工具链验证通过。
- 未实现：**全部业务功能**。日记、记账、灵感、设置、数据库、导航、主题配色均尚未开始；
  屏幕上只有一个居中「拾页」文字，属占位而非功能。
- 备注：`targetSdk` 暂为 36，阶段 1 会随 compileSdk 一并评估是否需要调到 37。

---

## 阶段 1 — 骨架（数据库 + 主题 + 导航 + 四 Tab）

目标：一次性定死 Room schema v1，落地取自 UI 原型的主题与自写导航，
四个 Tab 达到「最小化可用」，并且**每一项可用都是真实的**（不放假数据、不做假动作）。

### 改动文件

**数据层（新增）**

| 文件 | 作用 |
|---|---|
| `data/local/entity/DiaryEntities.kt` | `diary_entries` + `diary_fields` |
| `data/local/entity/TemplateEntities.kt` | `templates` + `template_fields` |
| `data/local/entity/LedgerEntities.kt` | `transactions` + `loans` + `categories` + `accounts` |
| `data/local/entity/InspirationEntity.kt` | `inspirations` |
| `data/local/dao/{Diary,Template,Transaction,Loan,Category,Account,Inspiration}Dao.kt` | 7 个 DAO，覆盖 9 张表 |
| `data/local/AppDatabase.kt` | `@Database(..., version = 1, exportSchema = true)` |
| `data/model/Models.kt` | 领域枚举与模型 + Entity ↔ 领域模型的成对映射 |
| `data/SeedData.kt` | 首次启动预置：两个模板 + 13 个分类 |
| `data/repository/{Diary,Template,Ledger,Inspiration,Settings}Repository.kt` | 5 个仓库，UI 不直接碰 DAO |
| `app/src/main/java/com/shiye/journal/AppContainer.kt` | 手写依赖容器 + ViewModel 工厂 |
| `app/src/main/java/com/shiye/journal/ShiyeApplication.kt` | Application，持有容器生命周期 |

**UI 层（新增）**

| 文件 | 作用 |
|---|---|
| `ui/theme/Color.kt` | 原型色板（浅/深）+ Material3 放不下的扩展色（收入/支出/借款金） |
| `ui/theme/Type.kt` | 字号：正文 16 / 标题 20 / 辅助 12 |
| `ui/theme/Theme.kt` | `ShiyeTheme`（三模式）+ 系统栏随主题 |
| `navigation/Route.kt`、`navigation/Navigator.kt` | 路由定义与自写导航状态机 |
| `ui/components/{Common,Bars,Amount}.kt` | 卡片、空状态、二次确认、分段控件、顶栏、底栏、金额文本 |
| `ui/screen/{Diary,Ledger,Inspiration,Settings}Screen.kt` | 四个 Tab 页面 |
| `ui/viewmodel/ShiyeViewModels.kt` | 四个 ViewModel |
| `ShiyeApp.kt`、`MainActivity.kt` | 重写：主题 → 导航宿主（顶栏 + 内容 + 底栏） |

**配置改动**：`AndroidManifest.xml` 加 `android:name=".ShiyeApplication"`。

### 数据库 schema（已生成 `app/schemas/.../1.json`，version = 1）

9 张表，列名全库统一 snake_case：

| 表 | 关键约束 |
|---|---|
| `diary_entries` | `date` **唯一索引**（同一天只能一篇）；`template_name` / `template_version` 是快照 |
| `diary_fields` | `entry_id` → `diary_entries.id`，**外键级联删除** |
| `templates` | `is_default` 由 Repository 保证全表最多一个 |
| `template_fields` | `template_id` → `templates.id`，外键级联删除 |
| `transactions` | 金额存**分**；借入借出**不进此表**；`date`+`time` 与 `date_time` 冗余存以便走索引 |
| `loans` | 借款独立表；结清只改状态，**不生成流水、不动账户余额** |
| `categories` | 归档（active=false）而非删除，历史流水仍可统计 |
| `accounts` | 余额**不落库**：期初 + 收入 − 支出，实时算 |
| `inspirations` | 只有正文与时间 |

### 构建与测试结果（真实执行，2026-10-15）

- **`./gradlew assembleDebug` → BUILD SUCCESSFUL，43 秒**，APK 12 MB。
- **`./gradlew testDebugUnitTest` → 21 个测试全部通过**（DateTimeUtil 8 / MoneyUtil 6 / Navigator 7）。
- 未做**真机**验证：本机 `adb devices` 无连接设备，也没有可用模拟器。
  数据库行为的静态验证靠导出的 schema（已逐表核对，见上）。

### 踩过的坑（按时间顺序，均为真实报错）

| # | 报错 | 原因 | 处理 |
|---|---|---|---|
| 1 | `e: [ksp] [MissingType]: Element '...Entity' references a type that is not present`（TemplateEntity / TemplateFieldEntity / InspirationEntity 三条） | **这三个文件用了 `@ColumnInfo` 却漏了 `import androidx.room.ColumnInfo`**。KSP 先于 Kotlin 编译器解析符号，未解析的注解被报成 MissingType，信息完全指不到 import 上 | 补 import |
| 2 | 误判：以为根因是 KDoc 文档链接 `[xxx]`，又以为是「类里没有嵌套 object」 | 两次对照实验都证伪（改注释、加嵌套 object 后错误原样复现） | 改用二分法：把 `AppDatabase` 缩到只剩一个实体，确认错误的确来自实体本身，才发现真正共同点是漏 import |
| 3 | 误判：以为是 AGP 9 内置 Kotlin + KSP 的上游 bug（google/ksp#3053），临时关掉 `android.builtInKotlin` | 关闭后 KSP 错误"消失"其实是因为构建被脚本错误（`Unresolved reference 'compilerOptions'`）提前拦下，**根本没跑到 KSP** | 修复 import 后回退该配置改动，验证内置 Kotlin + KSP 2.3.6 + Room 2.8.5 组合本身没问题 |
| 4 | `template_id referenced in the index does not exist in the Entity. Available column names: ..., templateId, ...` | 阶段 1 中途用脚本批量补 `@PrimaryKey` 时，正则误删了部分 `@ColumnInfo`，导致列名退回属性名 | 不再打补丁，直接重写 4 个实体文件，并统一全库 snake_case 列名 |
| 5 | `Unresolved reference 'toDomainEntry'` / `'ColumnScope'` / `'RowScope'` | 新增映射函数与布局作用域未导入 | 补 import |
| 6 | `No value passed for parameter 'onClick'`（`combinedClickable`） | Compose 新版 `combinedClickable` 的 `onClick` 是必传参数，只想响应长按时也得给 | 只长按的场景传 `onClick = {}` |
| 7 | **单元测试抓到的真 bug**：`Serializer for subclass 'DiaryHome' is not found in the polymorphic scope of 'Route'` | 密封接口 `Route` 的**基类自身**没标 `@Serializable`，子类不会被自动注册 | 给 `sealed interface Route` 加 `@Serializable`。这个缺陷编译期看不出来，只会在旋转屏幕/进程重建真正序列化时炸 —— 是写测试才提前发现的 |

### 已实现

- Room schema v1 **一次定死**（9 表 + 唯一约束 + 外键级联 + 索引），后续阶段只加功能不改结构。
- 数据库、5 个仓库、手写依赖容器真实可用；首次启动写入预置模板与分类（不预置账户）。
- 主题取自原型色板，浅/深/跟随系统三模式，**切换立刻生效并持久化**到 SharedPreferences。
- 自写导航：**每个 Tab 一条独立返回栈**，切走再切回子页面还在；整个导航状态可序列化，
  旋转屏幕与进程重建不丢页面（有 7 个单元测试覆盖）。
- 底部四 Tab（日记 ▤ / 记账 ◫ / 灵感 ✳ / 设置 ⚙，默认日记）。
- 日记：真实读取并展示日记，**空时显示直白空状态**；长按删除 → 二次确认 → 真删（字段靠外键级联）。
- 记账：本月收入/支出/结余**真实计算**（无流水时显示 ¥0.00，不是假数字）；本月流水真实读取。
- 灵感：输入框发送**真实写入数据库**，新发的立刻置顶；长按删除 → 二次确认 → 永久删除。
- 设置：主题三选一真实生效；上次备份时间从 SharedPreferences 真实读取（未备份显示「从未备份」）。

### 未实现（明确列出，后续阶段做）

- **日记（阶段 2）**：点卡片进编辑页、自动保存（0.8s 防抖 + 串行队列）、模板管理、
  模板切换保留旧字段、搜索（关键词 + 日期范围 + 排序 + 高亮）、单篇 Markdown 导出。
  现在点卡片只提示「编辑页在阶段 2 实现」。
- **记账（阶段 3）**：记一笔、分类管理、账户管理、借入借出、统计（日/周/月/年 + 分类占比）。
- **灵感（阶段 4）**：原地编辑、搜索。
- **设置与备份恢复（阶段 5）**：Markdown 批量导出、完整备份（ZIP）、恢复。
  设置页里这三项已列出并标注「阶段 5 开放」，**不做点了假装成功的假动作**。
- **V1 不出现 AI 入口**（规格要求 V1 不实现 AI、不申请网络权限），原型里的 AI 配置页留到 V2。

### 其他

- `targetSdk` 保持 36，未随 compileSdk(37.2) 上调：上调会引入新的行为变更，
  需要单独一轮适配验证，不属于本阶段范围。
- 图标沿用 Android Studio 默认图标，功能做完后另做。

---

## 阶段 2 — 日记（编辑页 + 自动保存 + 模板管理 + 搜索 + 单篇导出）

### 改动文件

**新增 · 领域层（纯 Kotlin，全部有单元测试）**

| 文件 | 作用 |
|---|---|
| `domain/AutosaveQueue.kt` | 串行自动保存队列 + 版本号；`SaveState` 四态；`SaveFlushRegistry`（退后台强制保存） |
| `domain/TemplateSwitch.kt` | 切换模板的字段合并规则（纯函数）+ 遗留字段判定 |
| `domain/DiaryMarkdown.kt` | 单篇日记 → Markdown（YAML 转义、空字段保留标题、换行归一） |
| `domain/SearchHighlight.kt` | 命中片段截取 + 高亮区间（忽略英文大小写，中文直接包含） |

**新增 · UI**

| 文件 | 作用 |
|---|---|
| `ui/components/Fields.kt` | 输入框、下拉菜单项、`SaveStateChip`（状态**带文字**） |
| `ui/screen/DiaryEditorScreen.kt` | 编辑页：只读日期、动态字段、保存状态、更多菜单、切换模板弹窗、导出 |
| `ui/screen/DiarySearchScreen.kt` | 搜索页：关键词、日期区间（日历选择器，两端可独立清空）、排序、高亮结果 |
| `ui/screen/TemplateScreens.kt` | 模板列表 + 模板编辑（字段增删改、上下移、单行/多行、必填） |
| `ui/viewmodel/DiaryEditorViewModel.kt` | 编辑页状态、自动保存驱动、标记完成、切换模板 |
| `ui/viewmodel/DiarySearchViewModel.kt` | 四条件组合 + 250ms 防抖自动重查 |
| `ui/viewmodel/TemplateViewModels.kt` | 模板列表 / 模板编辑（内存草稿，保存时才校验） |
| `util/FileUtil.kt` | SAF Uri 写文本（UTF-8） |

**修改**

| 文件 | 改动 |
|---|---|
| `navigation/Route.kt` | 新增 `DiaryEditor(date)`、`DiarySearch`、`TemplateList`、`TemplateEditor(id?)` |
| `ui/screen/DiaryScreen.kt` | 加「今天」按钮、长按操作菜单（永久删除）、点击进入编辑页、篇数 |
| `ui/components/Common.kt` | `PageHead` 支持右侧内容；新增 `ShiyeButton` / `ShiyeOutlineButton` / `TodayButton` |
| `data/local/dao/DiaryDao.kt` | 搜索增加字段标签匹配；排序末尾加 `id` 兜底保证稳定 |
| `data/local/dao/TemplateDao.kt` | 新增 `getAllOnce()`（重名校验用） |
| `data/repository/DiaryRepository.kt` | 新增 `saveContents` / `switchTemplate`（事务内重建字段快照）/ `setStatus` |
| `data/repository/TemplateRepository.kt` | 新增 `validate`（含**至少一个多行字段**、重名校验）；`update` 返回 `Result` |
| `util/DateTimeUtil.kt` | 新增与 Material3 日期选择器的 UTC 毫秒互转（有往返测试） |
| `ShiyeApp.kt` | 路由分发；带参数的 ViewModel 按 key 创建；编辑页隐藏品牌栏与底部栏 |
| `MainActivity.kt` | 监听 `ON_STOP`，触发 `SaveFlushRegistry.flushAll()` |
| `AppContainer.kt` | 新增三个 ViewModel 工厂 + `SaveFlushRegistry` |
| `gradle/libs.versions.toml`、`app/build.gradle.kts` | 加 `kotlinx-coroutines-test`（测试用，跑虚拟时间） |

### 构建与测试结果（真实执行，2026-10-15）

```
./gradlew assembleDebug      → BUILD SUCCESSFUL（43s），APK 12MB
./gradlew testDebugUnitTest  → 51 个测试全部通过
```

| 测试类 | 数量 | 覆盖 |
|---|---|---|
| `AutosaveQueueTest` | 7 | 防抖只写一次、每次输入重置计时、保存期间新输入续写、只有最新版成功才显示"已保存"、删除后失效、`flushAsync`、注册表 |
| `TemplateSwitchTest` | 6 | 按 key 匹配、退化到标签匹配、无法对应的旧字段必须保留、空字段不保留、id 全部重建、遗留判定 |
| `DiaryMarkdownTest` | 6 | 文件名、与规格示例逐字一致、空字段保留标题、YAML 转义、换行归一、字段顺序 |
| `SearchHighlightTest` | 9 | 英文大小写、中文包含、多次命中、空关键词、片段前后省略号、换行压平 |
| `DateTimeUtilTest` | 10 | 新增 2 条：日期选择器毫秒往返一致、UTC 零点 |
| `MoneyUtilTest` / `NavigatorTest` | 6 / 7 | 阶段 1 遗留，继续通过 |

### 踩过的坑（真实报错）

1. `Unresolved reference 'border'` —— `Common.kt` 用了 `Modifier.border` 但只 import 了 `BorderStroke`。
2. `Unresolved reference 'launch'` + `suspend function can only be called from a coroutine` —— 编辑页导出时直接调用挂起函数 `writeTextToUri`，补 `rememberCoroutineScope` 与 `kotlinx.coroutines.launch` 两处 import 后解决。
3. `'return' is prohibited here` —— `ViewModel` 的 `init {}` 块里写 `val id = templateId ?: return` 不合法（init 不是函数），改成 `if (id != null)`。
4. **两个测试断言写错，是测试的错不是实现的错**：
   - `SearchHighlight.ranges("ab-ab", "ab")` 我写成 `[0..2, 4..6]`，实际是 `[0..2, 3..4]`（`ab-ab` 的第 5 个字符位置是 3）。改用 `"abxxab"` 让位置无歧义。
   - `advanceTimeBy(800)` 后防抖任务没触发。原因是虚拟时间调度器的边界是**严格小于**，正好推进 800ms 不会执行排在 800ms 上的任务。改成推进 1000ms，并在测试里注明这是调度器语义而非实现 bug。

### 已实现

- **编辑页**：只读日期、保存状态（保存中/已保存/保存失败，带文字）、字段按模板动态生成（多行至少 5 行高）、更多菜单（标记完成/草稿、导出 Markdown）、切换模板弹窗。
- **自动保存**：0.8s 防抖、串行队列 + 版本号、旧内容不会覆盖新内容、离开页面等待最后一次保存完成、退到后台（ON_STOP）强制保存、失败保留内容并提供重试入口、日记被删除后在途写入全部失效。
- **切换模板**：按 fieldKey（退化到标签）匹配保留内容；无法对应且有内容的旧字段**原样保留**在日记里并标注来源，不静默丢失。
- **模板管理**：新建/编辑/删除/设为默认；字段增删改、上下移、单行多行、必填；校验名称非空、标签非空、至少一个字段、**至少一个多行字段**、名称不重复；删除前二次确认并写明"不会删除旧日记"；不允许删掉最后一个模板。
- **搜索**：关键词 + 起止日期（可独立清空）+ 排序；匹配字段内容与字段标签；忽略英文大小写；命中高亮；无结果显示"无结果"；点击打开原日记。
- **单篇导出**：SAF 选位置，文件名 `YYYY-MM-DD.md`，UTF-8，格式与规格 §7.2 示例一致。

### 未实现（明确列出）

- **批量 Markdown 导出**（设置页入口，选文件夹、进度、失败提示）—— 阶段 5。
- **完整备份 / 恢复**（ZIP + 版本化 JSON）—— 阶段 5。
- 记账、灵感、设置三个模块仍停留在阶段 1 的最小化可用状态。
- **未做真机验证**：本机无 adb 设备与模拟器，自动保存与导出的真实时序只靠单元测试覆盖，
  建议装到手机上手动确认一遍（尤其"打字 → 按 Home → 回来内容还在"）。

### 一个需要知道的行为

点「写下今天」会**立刻**创建一篇空草稿并进入编辑页（规格 §4.1：当天没有日记就创建）。
如果此时直接返回，列表里会留下一条标记为「草稿」、显示"这一天还没有记录"的空记录 ——
这是规格要求的行为（草稿方式），不是 bug；不想要的话长按删掉即可。

---

## 阶段 3：记账模块做完（2026-10-15）

### 做了什么

规格 §5 全量落到代码：首页概览、「记一笔」（含借款）一张表单、统计（日/周/月/年）、
分类管理、账户管理、借入借出。顺带发现 V1 schema 漏了名称快照，补了一次数据库迁移（见下）。

### 文件清单

**新增 · 领域层（纯 Kotlin，全部配单测）**

| 文件 | 职责 |
|---|---|
| `domain/LedgerPeriod.kt` | 日/周（周一起）/月/年 四种周期的起止换算、前后翻页、能否往后翻（`未来不能翻`） |
| `domain/CategoryBreakdown.kt` | 按分类聚合金额与排序，**占比分母是本列表自己的合计**（保证加起来 100%），总额为 0 时占比记 0 而不是除零 |
| `domain/LoanReminder.kt` | 逾期判定、到期文案（今天到期/还有 N 天/已逾期 N 天）、按方向汇总未结清、排序（逾期在前 → 有到期日的在前） |
| `domain/RecordForm.kt` | 「记一笔」四种类型的校验规则与取值换算（金额 > 0、日期格式、借款必须填对方姓名等） |

**新增 · UI 与 ViewModel**

| 文件 | 职责 |
|---|---|
| `ui/viewmodel/RecordEditorViewModel.kt` | 一张表单同时管收支与借款；写分类/账户**名称快照** |
| `ui/viewmodel/LedgerManageViewModels.kt` | 统计、分类管理、账户管理、借入借出四个 ViewModel |
| `ui/screen/RecordEditorScreen.kt` | 记一笔 / 编辑 |
| `ui/screen/LedgerStatsScreen.kt` | 统计与结算（周期切换 + 前后翻页 + 分类占比条 + 借款概况） |
| `ui/screen/CategoryManageScreen.kt` | 分类新建/重命名/归档/删除 |
| `ui/screen/AccountManageScreen.kt` | 账户新建/编辑/归档/删除 + 余额展示 |
| `ui/screen/LoanScreen.kt` | 借入借出列表 + 结清/改回/删除 + 逾期高亮 |

**修改**

| 文件 | 改动 |
|---|---|
| `ui/viewmodel/ShiyeViewModels.kt`（LedgerViewModel） | 改为：本月流水为唯一数据源 → 派生本月概览 / 今日 / 近 7 天；新增删除流水 |
| `ui/screen/LedgerScreen.kt` | 重写：本月结余卡 + 今日流水 + 最近 7 天 + 右下角「记一笔」+ 长按删除二次确认 |
| `ui/components/Common.kt` | 新增 `SectionHead` / `PrimaryFab` / `RatioBar` |
| `ui/components/Fields.kt` | 新增 `ChipSelector`（分类选择）与 `DatePickerField`（抽出给多个页面复用） |
| `ui/screen/DiarySearchScreen.kt` | 删掉页内私有的日期控件，改用公共 `DatePickerField` |
| `data/repository/LedgerRepository.kt` | 补 nextSortOrder / countByX；**余额改为同时监听流水表**（见下） |
| `data/local/dao/{Transaction,Category,Account}Dao.kt` | 新增 observeAll / countByCategory / countByAccount / maxSortOrder |
| `data/local/entity/LedgerEntities.kt` | transactions 增加 `category_name` / `account_name` 两列快照 |
| `data/local/AppDatabase.kt` | version 1 → 2，新增 `MIGRATION_1_2`（加列 + 回填） |
| `data/model/Models.kt` | Transaction 增两字段与快照取值规则（`categoryLabel` / `accountLabel`） |
| `navigation/Route.kt` | 新增 `RecordEditor` / `LedgerStats` / `CategoryManage` / `AccountManage` / `LoanHome` |
| `ShiyeApp.kt` | 五个新路由分发；记账首页顶栏加「◫」菜单（统计/分类/账户/借入借出） |
| `AppContainer.kt` | 新增五个 ViewModel 工厂 + `.addMigrations(MIGRATION_1_2)` |

### 数据库迁移 1 → 2（在本阶段的必要性）

规格 §5.3 要求流水存**分类名称快照**、§5.5 要求存**账户名称快照**，
V1 的 transactions 表只有 id，没有名字。缺了会这样：今天把「餐饮」改成「吃喝」，
上个月那笔账在列表里也跟着改名 —— 历史账目对不上，属于必须补的坑。

于是 `MIGRATION_1_2`：`ALTER TABLE` 加两列（`NOT NULL DEFAULT ''`，SQLite 要求的写法），
然后**立刻用子查询回填现有流水**（不回填的话升级完老数据会显示成"未分类"，看着像丢了数据）。
只动这两列，其它表一列没碰，也没有 `fallbackToDestructiveMigration`。

快照取值规则（写在 `RecordEditorViewModel.snapshotName`）：
**分类/账户没换就沿用旧快照；换了才按新 id 抄一份当前名字；查不到就留空**，
显示时空串退化到实时查名字，再查不到显示"未分类" / "未指定账户"。

### 构建与测试结果（真实执行，2026-10-15 16:41）

```
./gradlew assembleDebug      → BUILD SUCCESSFUL（1m17s），APK 12.8MB
./gradlew testDebugUnitTest  → 98 个测试全部通过（阶段 3 新增 47 个）
```

| 测试类 | 数量 | 覆盖要点 |
|---|---|---|
| `LedgerPeriodTest` | 11 | 日/周/月/年 起止、跨年周、周一算一周起点、前后翻页、**未来周期不能往前翻**、标题文案 |
| `CategoryBreakdownTest` | 7 | 同分类合并、按金额降序、**总额为 0 时占比 0 不除零**、占比相加为 1、未知分类折到「未分类」、归档分类仍能统计 |
| `LoanReminderTest` | 9 | 逾期判定、今天到期、**到期日为空不算逾期**、已结清不算逾期、按方向汇总、排序（逾期 → 有到期日 → 无到期日）、文案三态 |
| `RecordFormTest` | 12 | 金额必须 > 0、非借款必须有分类、借款必须有对方姓名、日期格式、备忘录正后可保存、四种类型的表归属换算 |
| `TransactionSnapshotTest` | 8 | 快照优先于实时名字、**分类改名不影响历史流水显示**、分类被删退到「未分类」、未指定账户、账户被删仍有快照时照样可读 |

### 踩过的坑（真实报错）

1. `Suspension functions can only be called within coroutine body` —— `snapshotName()` 里传入的
   `lookup` lambda 调了 DAO 的挂起函数，但函数本身没标 `suspend`。加 `suspend` 后通过。
2. 写文档时才发现 V1 漏了名称快照字段 —— 这不是编译错误，是**回头对着规格逐条核对**才看出来的。
   把它单独记下来：UI 跑通不代表规格覆盖到了。
3. `unresolved reference` 若干,全是漏 import（`FlowRow`、`Arrangement.spacedBy`、`Background`、
   `TextButton` 等），逐个补齐，无逻辑改动。

### 已实现

- **首页**：本月收入/支出/结余（= 收入 − 支出）、今日流水、最近 7 天、右下角「记一笔」；
  长按流水删除，二次确认写明"删除后本月结余和统计会一起变"。
  三类数字**只由流水算**，借款完全不参与。
- **记一笔**：四种类型（收入/支出 ‖ 借入/借出）分组选择；切到借款时表单字段整组切换
  （对方、到期日顶替分类与账户），标题与按钮文案跟着变；金额 > 0、非空校验在保存前做，
  错误就地显示不弹窗打扰。日期时间默认取设备本地当前时间，可改。
- **统计**：日/周（周一起）/月/年 + 前后翻页；收入/支出/结余；分类金额与占比条形
  （支出一份、收入一份）；借款**单独一块**概况（本周期发生 + 全局未结清），不与收支混排。
- **分类管理**：收入/支出分开；新建、重命名、归档、删除；每种方向最多 8 个有效分类；
  三重删除闸门 —— 预置分类不可删、有流水引用的不可删（只能归档）、其余删除也要二次确认。
- **账户管理**：期初余额可设；**余额 = 期初 + 收入 − 支出实时算出**，改流水余额立刻跟着变；
  有流水关联的账户只能归档不能删除。
- **借入借出**：借入/借出分别展示未结清合计与记录；到期日突出显示，
  逾期用错误色 + "已逾期 N 天"，今天到期也标出来；结清/改回/删除均可，删除二次确认。
  **结清只改状态**，不生成任何流水、不动任何账户余额（规格 §5.6 明确要求）。

### 未实现（明确列出）

- 记账模块的**导出**（CSV/Markdown）—— 未在本次范围内，阶段 5 统一做。
- 灵感、设置两个 Tab 仍是阶段 1 的最小化状态 —— 阶段 4 / 阶段 5。
- **真机未验证**：本机无 adb 设备与模拟器，以上结论来自单测 + 静态核对。
  建议装到手机上重点试三件事：① 记一笔 → 首页三个数字立刻变；
  ② 分类改名 → 历史流水显示**旧名字**；③ 记账中途按 Home → 回来内容还在（这条记一笔没有自动保存，
  没保存的直接返回会丢，目前是"点了保存才算"）。

### 一个刻意的产品决策

「记一笔」**不做自动保存**（与日记编辑页不同）。金额、分类、账户是一次性填写的短表单，
自动保存会让"手滑填到一半的输入"被静默存成一笔账，比丢内容麻烦得多 —— 这里宁可不保存。

---

## 阶段 3 补丁：两个用户报的 bug（2026-10-15 晚）

用户实测反馈：① 一天只能记一笔账；② 连点会"新建页面出现又立刻关闭"、编辑账单同理。
**两个现象是同一个根因**，另加一处连点保护。

### 根因：编辑页的 ViewModel key 用了业务 id，而"新建"时 id 恒为 null

```kotlin
// 修复前
viewModel(key = "record-editor-${it.recordId}", ...)
```

新建时 `recordId == null`，key 永远是 `"record-editor-null"`。于是：

1. 第一次记一笔 → 保存 → `_saved` 置 true；
2. 页面返回，但 **ViewModel 没被销毁，还在缓存里**；
3. 第二次点「记一笔」→ 拿到**同一个** ViewModel → `saved` 仍是 true
   → 页面一挂上，`LaunchedEffect(saved) { if (saved) onBack() }` 立刻触发返回。

表现就是"界面闪一下就关了，根本来不及填"——**所以一天看上去只能记一笔**。
编辑账单同理（同一条记录第二次进入会拿到同一个 ViewModel）。
顺带还有一个隐蔽后果：即使页面没被关掉，复用也会让金额/备注/分类残留上一次的输入。

### 修法（三处）

| 改动 | 说明 |
|---|---|
| `Route.RecordEditor` / `Route.TemplateEditor` 增加 `editorKey`（默认 `UUID.randomUUID()`） | 每次进页面都是新标识；它随路由一起序列化，所以**旋转屏幕仍拿回同一个 ViewModel**，正在填的内容不丢 |
| `ShiyeApp` 用 `editorKey` 拼 ViewModel key | 第二次新建 = 干净的新实例 |
| `RecordEditorViewModel` 的"保存成功"改为**一次性事件流** | `StateFlow<Boolean>(saved)` → `SharedFlow(savedEvent)`。状态会一直为 true，页面只要重新挂上就被误判成"刚保存完"；事件流只在真正发生的那次被消费 |
| `Navigator.push` 增加**连点防重** | 用 `Route.dedupeKey` 比较栈顶，相同则忽略。注意**不能直接比较整个 Route**：编辑页每次 editorKey 都不同，整体比较永远不等，等于关掉保护 |
| `save()` 加在途保护 + 按钮置灰 | 连点"保存"不会插出两笔一模一样的账 |

### 测试（真实执行）

```
./gradlew assembleDebug      → BUILD SUCCESSFUL（3m50s）
./gradlew testDebugUnitTest  → 103 个测试全部通过（NavigatorTest 7 → 12）
```

新增 5 条：连点同一入口只压一个页面、**退回后再进同一个入口必须仍然可以**（这条直接守着"一天只能记一笔"）、
编辑不同记录不受防重影响、新建页 editorKey 每次不同、**序列化往返保留 editorKey**（旋转屏幕后 ViewModel 不换实例）。

### 一条写在代码注释里的教训

"保存成功""删除成功"这类**一次性事件**不能用 StateFlow 表示。StateFlow 是状态，会一直为真，
页面任何一次重新挂载都会被误判。ViewModel 被缓存复用（key 相同）时尤其容易踩到。

---

## 阶段 3 补丁 2：统计页日期可以直接手输（commit 见 git log）

**用户反馈**：统计界面每次只能通过左右切换日期很麻烦，希望能点击日期那一栏直接输入。

### 交互

顶部周期标题那一行现在是**可点的输入框**：

- 平时显示周期标题（如「2026年10月」）+ 一行灰字提示"点上面的日期可以直接输入，如 2026-10"；
- 点一下就地变成输入框并自动弹出数字键盘；期间**左右箭头收起**，把宽度让出来；
- 直接敲数字：年 4 位 / 年月 6 位 / 年月日 8 位，**随粒度放开**；分隔线自动补，粘进来的杂字符被滤掉；
- 敲完按键盘「完成」或右边 ✓ 跳转；✕ 放弃回到标题；
- 旁边加了「回到今天」——手输跳远之后一步回来，比一路点箭头快。

### 新增/改动文件

| 文件 | 改动 |
|---|---|
| `domain/DateInput.kt` | **新增**。纯 Kotlin 的日期输入解析：`formatDigits`（整理输入）+ `parse`（位数→日期→周期，含合法性与未来拦截） |
| `ui/components/Fields.kt` | **新增** `DateJumpField` 组件 + 私有 `JumpActionButton` |
| `ui/screen/LedgerStatsScreen.kt` | 顶部卡片改用 `DateJumpField`；新增「回到今天」 |
| `ui/viewmodel/LedgerManageViewModels.kt` | `LedgerStatsViewModel` 新增 `jumpTo(text): Boolean` / `goToday()` / `dateInputError` / `consumeDateInputError()`；`setKind` / `move` 顺手清错误 |
| `app/src/test/.../DateInputTest.kt` | **新增** 16 条单测 |

### 两个刻意的选择

1. **用 STRICT 解析，不用默认的 SMART**。`DateTimeFormatter.ofPattern` 默认是 SMART，
   会把 `2026-02-30` **静默改成 2 月 28 日**——用户以为跳到了 30 号，实际停在 28 号，
   这种"悄悄改"比直接报错糟。STRICT 下必须用 `uuuu` 而不是 `yyyy`（`yyyy` 是 era 年，
   没有 era 无法解析，会直接抛异常）。
2. **输错了不动当前周期，只给一句红字原因**。跳转失败时输入框留在原地、提示留在原地，
   让人看清再改；要是随便跳到一个莫名其妙的周期，比不动更让人慌。
   所以 `jumpTo` 返回 Boolean，UI 据此决定收不收起。

### 测试（真实执行）

```
./gradlew assembleDebug      → BUILD SUCCESSFUL（27s）
./gradlew testDebugUnitTest  → 119 个测试全部通过（103 → 119，DateInputTest 新增 16）
```

覆盖：自动补分隔线、滤掉非数字与截断、**闰年 2 月 29 日可以**、
**2 月 30 日必须报错而不是被修正**、月份越界、日粒度下位数不够不猜、
未来日期（含年粒度）拦截、"今天所在的周期不算未来"。

### 踩的坑

- 项目**没有引 Truth**（`com.google.common.truth`），测试一律用 `org.junit.Assert.*`。
- `errorText?.let { Text(...) } ?: Text(...)` 是错的：`let` 返回 `Unit`（非空），
  Elvis 分支永远走不到。分支渲染要用 `if / else`。

---

## 阶段 3 补丁 3：所有日期框都能直接手输

**用户反馈**：「那就一并改为可手输入」——上一轮只改了统计页，这轮把共用的日期框一起改掉。

### 交互（记一笔 / 借款到期日 / 日记搜索通用）

平时一行日期，点一下就地变输入框并弹数字键盘；直接敲 8 位数字，分隔线自动补，
**敲满且是真实存在的日子就自动落定**，不用再按确定；键盘「完成」也能落定。
右边图标随状态切换：空闲是「历」（开日历），输入态是「✕」（放弃这次输入回到原值）。
需要清空的场景（日记搜索两端、借款到期日）旁边仍保留「清空」。

### 关键决定：`parseDate` 与 `parsePeriod` 分开，**未来拦截只作用于统计页**

拆分前 `DateInput.parse` 只有一个版本，带「拦未来」。这次要复用到借款的**到期日**上，
而到期日天然在未来 —— 一刀切拦掉等于不让用户填到期日。
所以拆成两条：

| API | 用在哪 | 是否拦未来 |
|---|---|---|
| `DateInput.parseDate(raw)` | 通用日期框（记一笔 / 到期日 / 日记搜索） | **否**，日期本身有没有意义由调用方判断 |
| `DateInput.parsePeriod(raw, kind, today)` | 统计页 | **是**，与左右箭头同一口径，就到"今天所在周期"为止 |

`DateInputResult.Ok` 现在带的是 `LocalDate`（原来是 `LedgerPeriod`），
周期由调用方 `LedgerPeriod(kind, date)` 组装 —— 解析层不替调用方决定粒度。

### 改动文件

| 文件 | 改动 |
|---|---|
| `domain/DateInput.kt` | 拆出 `parseDate`；`parse` 更名 `parsePeriod`；`Ok` 携带 `LocalDate` |
| `ui/components/Fields.kt` | `DatePickerField` 重写为**可点可敲**（`BasicTextField` + 状态切换的尾部图标 + 行内错因） |
| `ui/viewmodel/LedgerManageViewModels.kt` | `jumpTo` 改用 `parsePeriod` 并自行组装周期 |
| `ui/screen/DiarySearchScreen.kt` | 两个日期框由**并排改为上下排**（并排时每格只剩一半宽度，敲 8 位数字会被挤掉） |
| `app/src/test/.../DateInputTest.kt` | 16 → 20 条，补 `parseDate` 的用例 |

### 测试（真实执行）

```
./gradlew assembleDebug      → BUILD SUCCESSFUL（3m42s）
./gradlew testDebugUnitTest  → 123 个测试全部通过（119 → 123）
```

新增 4 条专门守住这次的拆分：`parseDate` 返回日期本身、
**`parseDate` 允许未来日期**（到期日场景）、位数不全报错、非法日期报错不修正。

### 踩的坑

`assertTrue(result is DateInputResult.Ok)` **不会**产生 smart cast。
`is` 判断写在函数实参里，编译器不做类型收窄，后面 `result.date` 照样 unresolved。
要智能转换就用 `if (x is T)`，否则老实写 `(x as T)`。

---

## 阶段 4：灵感模块（原地编辑 + 搜索）

**规格**：完整项目内容.txt §6 —— 长按气泡可编辑/删除、编辑是原地编辑、
搜索支持关键词 + 日期范围 + 排序、无结果显示「无结果」。

### 功能

- 列表改为聊天气泡：自己发的靠右、强调色浅底、右上角收窄（原型的 .bubble.own），
  时间在气泡下方；今天 `HH:mm`、昨天 `昨天 HH:mm`、更早 `MM-DD HH:mm`
  （修掉了月份不补零的问题，3 月原来是 `3-05`）。
- 输入条：Enter 发送 / Shift+Enter 换行（软键盘回车键变「发送」），空内容/超长给出提示。
- 长按气泡 → 菜单（编辑这条 / 删除这条）；删除走二次确认后永久删除。
- **原地编辑**：内容回填底部输入框，输入条进入编辑态（标题「编辑灵感」+ 取消 +
  按钮变「保存」），保存更新原记录不新建；气泡上出现「编辑中」高亮。
- 删除正在编辑的那条时自动退出编辑态（否则再点保存会把删掉的记录写回来）。
- 搜索页（新路由 InspirationSearch）：关键词 + 开始/结束日期 + 新旧排序，
  250ms 防抖自动重查；命中文字高亮；两端为空列出全部（标题「全部灵感」）。

### 领域规则（domain/ 纯 Kotlin + 单测）

| 类 | 规则 |
|---|---|
| `InspirationDraft` | 空白不能发；长度上限 2000（提示带当前字数）；`changed()` 只认实质变化 |
| `InspirationQuery` + `MillisRange` | 日期端点 → 左闭右开毫秒区间（结束日当天 23:59:59.999 也要命中）；开始晚于结束/日期非法 → 该侧不限制；AND 匹配语义与 DAO 的 SQL 一一对应 |

### 关键决定：日期解析用 `DateInput.parseDate`（STRICT），不用 `DateTimeUtil.parseDate`

后者是 SMART 解析，会把 `2026-02-30` 静默改成 `02-28`；搜索页敲错一天
却查出一个看起来正常的结果，比报错更难发现。测试抓到过。

### 改动文件

| 文件 | 改动 |
|---|---|
| `domain/InspirationDraft.kt` `domain/InspirationQuery.kt` | 新增 |
| `ui/viewmodel/InspirationSearchViewModel.kt` | 新增（250ms 防抖） |
| `ui/screen/InspirationSearchScreen.kt` | 新增 |
| `ui/screen/InspirationScreen.kt` | 重写：气泡 + 原地编辑 + 长按菜单；空状态占满剩余高度把输入条压到底 |
| `ui/viewmodel/ShiyeViewModels.kt` | `InspirationViewModel` 加 editingId / beginEdit / cancelEdit / submit；submit 编辑分支用 `changed()` 拦没改也保存 |
| `navigation/Route.kt` | 加 `InspirationSearch` |
| `ShiyeApp.kt` | 路由分发 + 灵感页顶栏搜索图标 + 子页面返回箭头 |
| `AppContainer.kt` | `inspirationSearchViewModelFactory` |
| `ui/components/Common.kt` | 高亮组件 `HighlightedText` 从日记搜索页抽出共用 |
| `ui/components/Fields.kt` | `ShiyeTextField` 加 `keyboardActions` 参数 |
| `util/DateTimeUtil.kt` | `formatInspirationTime` 月份补零（MM-DD） |
| `ui/components/Bars.kt` | **修复：顶栏补 `statusBarsPadding()`**（见下） |
| app/src/test | 新增 `InspirationDraftTest`（8 条）、`InspirationQueryTest`（12 条） |

### 踩过的坑（模拟器冒烟测试抓到的 3 个真 bug）

1. **顶栏被状态栏吃掉**：App 是 edge-to-edge，`ShiyeTopBar` 没做状态栏避让，
   时钟压在品牌文字上、右上角图标点不到（阶段 1 就埋着，这次自动化点按才发现）。
   修复：`ShiyeTopBar` 加 `statusBarsPadding()`。
2. **没改内容也冒「已编辑」**：编辑态保存无条件 update，刷新了 updatedAt。
   修复：submit 里用 `InspirationDraft.changed()` 拦掉无实质变化的保存。
3. **搜索页标题不刷新**：`hasNoCondition` 直接读 StateFlow.value，
   不在 Compose 快照体系里，结果列表恰好相等（新列表 equals 旧列表被 StateFlow 去重）时
   标题卡在旧值。修复：页面用已收集的状态现算（日记搜索页同款问题一并修）。

### 测试与构建（真实执行）

```
./gradlew testDebugUnitTest  → 143 个测试全部通过（123 → 143，新增 20）
./gradlew assembleDebug      → BUILD SUCCESSFUL（APK ~13MB）
```

模拟器（Pixel_5 / API 34 / WHPX）全流程冒烟通过：发送 → 长按编辑（改/不改两种）→
搜索命中高亮 → 删除二次确认后消失。

---

## 阶段 5：设置 / 备份 / 恢复 / 导出（2026-10-16）

**规格**：完整项目内容.txt §8 —— 设置页三个数据入口（导出 Markdown / 完整备份 / 从备份恢复）、
备份是带校验的 ZIP、恢复前显示摘要并二次确认、恢复是原子的（失败不影响现有数据）、
记录上次备份时间并提示「多少天没备份」。

### 功能

- **导出 Markdown**：SAF `OPEN_DOCUMENT_TREE` 选文件夹 → 逐篇写出 `.md`，
  进度实时显示「正在导出 N/M」；完成提示「导出完成，共写出 N 篇日记」。
- **完整备份**：SAF `CREATE_DOCUMENT`（建议名 `shiye-backup-yyyyMMdd-HHmm.zip`）→ 写出
  `backup.json` + `checksum.txt` 的 ZIP；成功后更新「上次备份」时间。
- **从备份恢复**：SAF `OPEN_DOCUMENT` 选文件 → **先解析出摘要弹确认框**
  （各表条数 + 备份时间 + 「恢复会替换当前数据，且不可撤销」警告）→ 确认才执行。
- 设置页补上版本号（versionName / versionCode），主题三段选择沿用阶段 1 的实现。

### 备份格式（domain/data 纯逻辑，可单测）

```
backup.json   → { format_version, app_db_version, exported_at, device,
                  diary_entries, diary_fields, templates, template_fields,
                  transactions, loans, categories, accounts, inspirations }
checksum.txt  → backup.json 的 SHA-256 十六进制
```

- **版本兼容判断**：`format_version > 当前` → 拒绝（备份来自更新版本，本版读不懂）；
  `app_db_version > 当前库版本` → 拒绝；低版本一律放行（向后兼容）。
- **校验和必须匹配**，否则报「备份文件已损坏」。
- 实体直接标 `@Serializable` 复用为传输结构，省一层 DTO 映射（改实体字段即改备份格式，
  靠 `app_db_version` 兜底）。

### 关键决定

1. **恢复必须在一个事务里做**：`BackupDao.replaceAll()` 用 `runInTransaction`，
   先 deleteAll 九张表再批量 insert；抛异常自动回滚，数据停在恢复前。
   UI 上的措辞也照此写：「恢复失败，当前数据未受影响」。
2. **一次性事件用「最近结果消息」而不是 SharedFlow**：备份/恢复的结果如果走事件流，
   旋转屏幕后消息就没了；改成 ViewModel 里的 `message: StateFlow<String?>`，
   页面重新挂载仍能看见，UI 也少一层收集。
3. **恢复分两步（解析预览 → 确认执行）**：直接替换太危险，规格要求先看摘要再确认。
   解析失败不进预览态，直接报错并回到空闲。
4. **Markdown 文件名同日多篇加序号**：`2026-10-16.md` / `2026-10-16-2.md` / `-3`，
   规则收进 `DiaryMarkdown.fileNameFor(entry, indexInDay)` 并配单测，
   避免同一天两篇互相覆盖。

### 改动文件

| 文件 | 改动 |
|---|---|
| `data/backup/BackupCodec.kt` | 新增：Payload 定义、版本校验、ZIP 打包/解包、SHA-256 校验 |
| `data/backup/BackupManager.kt` | 新增：SAF 读写、Markdown 批量导出（带进度）、事务内原子恢复、备份建议名 |
| `data/local/dao/BackupDao.kt` | 新增：九表全量读 / `replaceAll()` 事务替换 |
| `data/local/AppDatabase.kt` | 注册 `backupDao()` |
| 四个 `data/local/entity/*.kt` | 9 个实体加 `@Serializable` |
| `domain/DiaryMarkdown.kt` | 加 `fileNameFor(entry, indexInDay)`（同日多篇序号） |
| `ui/viewmodel/ShiyeViewModels.kt` | `SettingsViewModel` 加 `BackupStep` 状态机 + 三个入口 + 最近结果消息 |
| `ui/screen/SettingsScreen.kt` | 重写：三个入口 + 进度条 + 恢复预览确认框 + 结果提示 + 版本号 |
| `AppContainer.kt` | 接 `BackupManager` 与新的 settingsViewModelFactory |
| `app/build.gradle.kts` | 加 `androidx.documentfile` |
| `app/src/test/BackupCodecTest.kt` | 新增（10 条：往返、改版本、改校验和、空备份等） |
| `app/src/test/DiaryMarkdownTest.kt` | 新增文件名序号 1 条 |

### 测试与构建（真实执行）

```
./gradlew testDebugUnitTest  → 160 个测试全部通过（150 → 160，新增 10）
./gradlew assembleDebug      → BUILD SUCCESSFUL
```

### 模拟器实测（Pixel_5 / API 34）

| 场景 | 结果 |
|---|---|
| 完整备份 → SAF 保存 | ✅ 写出 `shiye-backup-20261016-0736.zip`（1496B），设置页显示「上次备份：2026-10-16」 |
| `pm clear` 清空数据后重启 | ✅ 日记列表归零、「上次备份：从未备份」 |
| 选备份文件恢复 | ✅ 预览框显示条数摘要 + 替换警告；点「替换当前数据」后日记「good」回来，共 1 篇 |
| 导出 Markdown | ✅ 提示「导出完成，共写出 1 篇日记」，`ShiyeExport/2026-10-16.md` 内容含 YAML front-matter 与字段分节 |
| 恢复后再次备份 | ✅ 正常，ZIP 内 `backup.json` + `checksum.txt` 齐全 |

### 踩到的环境坑（非代码问题）

- Android 14 的 DocumentsUI **不允许把 Documents 树授权到 `Download` 目录**
  （提示 "Can't use this folder"）。验证时改用「CREATE NEW FOLDER」建 `ShiyeExport` 目录。
  这是系统限制，不是本应用的问题。
- 单测里「篡改 ZIP 最后一个字节」**检测不出损坏**（只伤到 central directory，流式读取不受影响）。
  改成构造「数据与 checksum 不匹配」的场景来测校验逻辑。

---

## 阶段 5.5：用户试用反馈一批（2026-10-16 下午）

**一个 Bug + 四个需求**，全部完成。

### Bug：删掉今天的日记后，当天再也写不了

- 现象：长按删除当天日记 → 点「写下今天」→ 一敲字就弹「这篇日记已被删除」并自动返回。
- 根因：**编辑页 ViewModel 的 key 是日期**，退到列表再进来拿到的是同一个实例，
  里面还留着已删除那篇的 id；自动保存写一个不存在的 id → `saveContents` 返回 false →
  `_removed = true` → 页面弹提示并返回。
- 修复（两层）：
  1. `AutosaveQueue.reset()`：解除失效状态、清空版本计数，让队列能重新工作；
  2. `DiaryEditorViewModel.refreshIfMissing()`：每次进页面调一次，
     发现日记不在库里就按日期重建；`consumeRemoved()` 让「已删除」提示只弹一次，
     不会在重新挂载时又被当成"刚被删掉"弹回去。
- 判断顺序刻意安排：`_entry` 还没加载完时不插手（避免和 init 各建一篇）、
  日记还在库里就什么都不做（正在输入的内存内容一律不动）。

### 1. 记账 + 灵感右上角批量删除

- 顶栏加「批量删除」文字按钮（`TopBarTextButton`，40dp 图标按钮放不下四个字）；
- 进入多选态后顶栏变三件套：全选 / 删除 N 条 / 取消，
  一条没选时「删除」置灰（不给一个点了没反应的按钮），删除前二次确认；
- 流水行左侧出勾选圈（`SelectMark`，自绘而非系统 CheckBox）；
- 多选态下「记一笔」FAB 和灵感输入条都收起 —— 多选态下还挂着"去新建"的入口，
  很容易被误当成"确认选择"；
- 批量删除的状态放 ViewModel（bulkMode / selectedIds / bulkMessage），
  顶栏与页面读同一个真值，不会出现"顶栏退出了、列表还在勾选"。

### 2. 灵感多页（数据库 v3）

- 新表 `inspiration_pages`，`inspirations` 加 `page_id` 列；
  **`MIGRATION_2_3` 建默认页「随手记」**，老灵感全部归到它名下，一行不丢；
- 默认页 id 是固定常量（`page-default`）而非随机 UUID：迁移 SQL 里没法用运行时生成的 id；
- 页之间完全隔离：列表按页查、**搜索也按当前页查**（在「小说」页搜到「菜谱」会让人以为记串了）；
- 删页 = 页内内容一并删，跑在一个事务里（`withTransaction`），失败整体回滚；
- 换页记住上次停留（SharedPreferences），下次打开还在那一页；
- 删掉当前页时自动切回第一个页，不会停在一个不存在的页上；
- 备份格式同步：Payload 加 `inspirationPages`（带默认值 → **能读 v2 旧备份**），
  `DB_VERSION` 升到 3；恢复时若备份里没有页（旧备份），补一个默认页把老灵感接住，
  否则恢复完一页都不存在、灵感列表显示为空，看起来就像"恢复把灵感弄丢了"。

### 3. 「补写回忆」

- 「写下今天」旁加一个小按钮，**显示的是"切过去会变成什么"**（不是 ⇄ 图标）；
- 补写模式点主按钮先弹日期弹窗：‹ › 一天天地挪、日期框可直接敲（复用 `DatePickerField`
  的手输逻辑）、点「历」开系统日历；
- 只让选**今天或更早**：这是"补写回忆"，选到未来会写出一篇排在列表最前面、
  却还没发生的日记，看着像排序坏了。

### 4. 设置-「关于」改成按钮 + 弹窗

- 长文案收进可滚动的弹窗（设置页首屏不该被它占满），末句加粗；
- 顺手修了一个真 bug：**设置页是普通 Column 不能滚动**，屏幕不够高时
  「关于」区块整个在屏幕外且滚不到。根 Column 补 `verticalScroll`。

### 测试与构建（真实执行）

```
./gradlew testDebugUnitTest  → 163 个测试全部通过（160 → 163）
./gradlew assembleDebug      → BUILD SUCCESSFUL
```

### 模拟器实测（Pixel_5 / API 34）

| 场景 | 结果 |
|---|---|
| 删今天日记 → 重写 | ✅ 「已保存」，无"已删除"弹窗，列表回到 1 篇 |
| 补写回忆（‹ › 挪到 10-14） | ✅ 写完出现在列表且排序正确（10-16 在前） |
| 灵感新建页「Novel」/ 切页 | ✅ 副标题显示当前页名，两页内容互相隔离 |
| 灵感批量删除 | ✅ 全选 → 删除 1 条 → 确认后清空并退出多选 |
| 记账批量删除 | ✅ 全选 → 删除 2 条 → 结余归 0 |
| 关于弹窗 | ✅ 全部文案显示，末句加粗 |
| v2 → v3 迁移 | ✅ 覆盖安装后旧数据原样在（日记/灵感/流水） |

### 踩坑（本次新增）

- `runInTransaction` 不接受挂起 lambda —— 事务里有 suspend 调用要用
  `androidx.room.withTransaction`（项目里两种写法并存，别抄错）。
- 序列化的键名是**属性名**（camelCase），`@ColumnInfo` 只管 Room；
  手写备份 JSON 测试时照列名写全部对不上。
- `advanceTimeBy` 边界严格小于，正好推进 N ms 不触发排在 N ms 的任务（又一次）。
- KSP `[MissingType]` 又是漏 import（`InspirationPageDao`），教训在备忘里记过两次了还是犯。

---

## 键盘遮挡修复（2026-10-17 上午）

**现象**：日记编辑页、记一笔页里，键盘弹出后直接盖住正在编辑的内容，看不到自己在敲什么。

**根因**：`windowSoftInputMode` 已是 `adjustResize`，但页面是 edge-to-edge 绘制，
调整后的窗口尺寸变化不会自动传导给 Compose —— 滚动容器的可视区仍是整屏，
Compose 判定"字段在视野内"，就不会滚动。

**修复（两层配合，缺一不可）**：

1. **两个编辑页的可滚区加 `imePadding()`**：键盘弹起时可视区压到键盘上方，
   Compose 才知道"哪些内容被盖住了"；
2. **`ShiyeTextField` 聚焦时主动 `bringIntoView`**：聚焦后延迟约 120ms（等键盘把
   可视区压下来那一帧过去，否则按旧高度算会滚不到位）把当前框滚进可视区。

日记编辑页底部留白从 40dp 收到 24dp（原来那个魔法数字就是为了对付键盘，
现在有正经的 ime 避让，留一点呼吸空间就够了）。

**模拟器实测**：
- 日记编辑页：正文连敲 8 行，光标始终可见；聚焦最底部的「备注」，框完整停在键盘上方；
- 记一笔页：聚焦「备注」，页面自动上滚约 290px，框完整露出。

**坑**：`kotlinx.coroutines.launch` 用在 `rememberCoroutineScope()` 返回值上
也要显式 import —— 只有 `delay` 不够。

---

## 阶段 6（V2）：可选 AI（规格 §8）

**核心前提：AI 默认关闭。** 关着的时候一个字节都不往外发 —— 装完 App 就是纯离线。

新增依赖：OkHttp 4.12（规格 §2.1 指定）、androidx.security:security-crypto 1.1.0-alpha06。
两者都在阿里云镜像上解析成功。

### 6.1 配置与客户端（§8.1 / §8.2）

- 三种模式：关闭 / 本地模型 / 云端 API。**配置存本机偏好、不进备份** ——
  换台机器恢复数据时不该把原机器的 API Key 和"AI 已开启"状态带过来。
- API Key 存 **EncryptedSharedPreferences**（Android Keystore 保护的 AES）；
  非云端模式连 Authorization 头都不带（单测把这条按住了）。
- OpenAI 兼容、不写死供应商：预设只是填表快捷方式，地址与模型名随时可改。
- **地址拼装容错**：用户可能填 `.../v1`、`.../v1/`、或写全 `.../v1/chat/completions`，
  三种都能用；没有协议头的一律拒绝（别拿着怪地址去发请求）。
- **错误必须说人话**：401 认证失败、404 模型不存在（并回显模型名）、
  超时（说多少秒）、连不上、返回结构不认识、地址不合法，各说各的。
- 测试连接发的是最小请求（正文只有 hi，max_tokens=1）。

### 6.2 润色与总结（§8.3 / §8.4）

- **发送前先确认**：弹窗写明"发什么、发到哪个模型/主机"，并说明只发这一段。
- **采纳的冲突保护**：用户在等 AI 那几十秒里很可能继续打字。
  采纳前用 `decideApply` 判一次，改动过就先提示而不是悄悄覆盖（§8.3 明确禁止）。
- **可回滚**：替换前存原文快照，页面给出「撤销」（`AiNoticeBar`）。
- 总结结果三种去处：存入日记末尾（带「AI 摘要」标记，不混进原文）、
  存入独立 AI 栏目（一个字都不改日记）、复制、丢弃。

### 6.3 建议与 AI 栏目（§8.5 / §8.6）

- 新表 `ai_notes`（数据库 **v4**，`MIGRATION_3_4` 纯建表、幂等）。
  单独一张表而不是塞进日记：AI 生成的东西不是用户写的，混进去久了分不清。
- 备份同步带 `aiNotes`（带默认值，v3 及更早的备份照样能读）。
- **最近七天建议**只发摘要不发全文（每篇截取前 120 字），
  **借款单独成段并写明"不计入收支"** —— 混进收支会得出"这个月很宽裕"的鬼话。
- 结果由用户决定是否保存（§8.5：不得自动记账、自动写日记）。

### 改动文件

| 文件 | 改动 |
|---|---|
| `features/ai/` | 新增：AiConfig / AiProtocol / AiClient / AiError / AiTask / AiDigest / AiConfigStore / OkHttpEngine |
| `domain/AiPrompts.kt` | 新增：润色/总结/建议三个提示词 + `decideApply` 冲突判断 |
| `ui/viewmodel/AiSettingsViewModel` `AiNotesViewModel` | 新增 |
| `ui/screen/AiSettingsScreen` `AiNotesScreen` | 新增 |
| `ui/components/AiTaskDialog` | 新增：确认发送 / 结果 / 失败三阶段弹窗 |
| `ui/components/Fields.kt` | `ShiyeTextField` 加 `action` 槽位（挂「AI 润色」） |
| `DiaryEditorViewModel` / `DiaryEditorScreen` | 接 AI：润色按钮、总结菜单、结果落地、撤销 |
| `AppDatabase` / `BackupCodec` / `BackupDao` / `BackupManager` | 数据库 v4 + 备份带 ai_notes |
| `AndroidManifest.xml` | 加 INTERNET（按规格要求同步更新了注释） |

### 测试与构建（真实执行）

```
./gradlew testDebugUnitTest  → 183 个测试全部通过（178 → 183）
./gradlew assembleDebug      → BUILD SUCCESSFUL（APK 14MB）
```

### 需要你在真机上确认的

模拟器验证交给你，重点看这几条：
1. **关着 AI 时真的没联网**：设置页 AI 显示"已关闭"，日记编辑页不出现「AI 润色」按钮；
2. 配置页填本机 Ollama 地址 → 测试连接能通（模拟器里本机地址是 `10.0.2.2`）；
3. 云端模式 401/404 的提示是否说清楚了。

---

## V2 补丁：API Key 带换行被误报成「地址格式不对」（2026-10-17 晚）

**用户现象**：地址填的是 `https://api.deepseek.com/v1`（Key 和模型名都确认没问题），
App 却一直报"地址格式不对"。

**排查**：先写一个 JVM 断言复现 OkHttp 的行为，拿到确凿证据 ——

```
IllegalArgumentException: Unexpected char 0x0a at 16 in Authorization value
```

**根因**：Key 是从网页后台复制的，末尾粘进了一个换行（0x0A）。
HTTP 头的值不允许含控制字符，OkHttp 在**构建请求这一步**就抛了异常。
而这个异常当时被统一翻译成 `BAD_URL` → 提示"地址格式不对"。
提示指向了完全错误的地方，难怪怎么查地址都查不出问题。

**改两层**：

1. **治本** —— 新增 `AiCredentials.sanitize`，在**输入 / 保存 / 发请求**三处
   都把首尾空白与所有控制字符剔掉。用户不必学会小心翼翼地复制。
   UI 检测到刚才粘贴的内容被清洗过时，明确说一句
   "粘贴内容里带着换行，已自动去掉 —— 这些字符会让请求直接发不出去"。
2. **治万一** —— 错误分类拆开：新增 `AiErrorKind.BAD_CREDENTIAL`，
   `OkHttpEngine` 里把 `.url()` 和 `addHeader()` 的异常分开捕获，
   前者才是"地址不对"，后者一定是"凭据里有脏字符"。

**新增测试 30 条**（183 → 213）：
- 地址：deepseek 官方地址、带端口号、首尾空白都能用；
- 凭据：`\n` `\r` `\r\n` `\t` 中间换行、全脏字符、中间正常空格要保留；
- 把这条 OkHttp 行为本身也钉成测试（`ApiKeySanityTest`）：
  依赖库什么时候放宽这条校验，测试会先响。

### 教训

错误提示**指向错地方比不提示更糟**：不提示用户至少知道"失败了"，
指错方向会让他对着无辜的地方反复排查。
把不同来源的异常合并成一个笼统分支时要格外小心 ——
`IllegalArgumentException` 就是个典型：同一个类型，
URL 和 header 两处抛出来，含义毫无关系。

---

## V2：AI 配置支持多套档案（2026-10-17 晚）

用户诉求：配过 DeepSeek 和 GPT 两套之后能都存着，想用哪个点一下就切。

**数据模型**：`AiConfig` 加 `id` 与 `name`，一套配置 = 一份档案。
Key **按档案 id** 单独存在加密区，档案列表的 JSON 里绝不含 Key（有测试钉死）。

**存储**（`AiConfigStore`）：
- `profiles`（JSON 数组，不含 Key）+ `active_profile_id` + 每个档案一个 `api_key_<id>`；
- **旧的单套配置自动迁移**成一份档案（固定 id `profile-legacy`，跑完不删旧键，
  万一迁移没成下次还能再试）；原来关着就保持关闭，配置留着下次不用重填；
- 删除正在用的那套 → 退回关闭，**不随便挑一套顶上**（用哪家得用户自己定）。

**交互**：
- 列表顶部固定一项「不使用 AI（关闭）」—— 它是默认状态，不该藏在列表里；
- **「用哪套」和「改哪套」分开**：点名字是切换使用，点「编辑」才是改它，
  免得"只想改个名字结果把它切成正在用的了"；
- 保存完自动切到这套（填了半天结果没生效会很困惑）；
- 设置页摘要现在会说"在用「DeepSeek」· deepseek-chat"。

**踩到的坑**：枚举序列化默认用**常量名**（`CLOUD`）而不是我定义的 `storageKey`（`cloud`），
而且遇到不认识的值会直接抛异常、连累整个档案列表读不出来 ——
用户会看到"我保存的配置全没了"。
给 `AiMode` 写了 `AiModeSerializer`：写稳定的小写键，读时未知值退回 OFF（永远是安全的那一侧）。

测试 213 → 219（新增 6：多套往返、落盘不含 Key、缺字段兼容、模式无法识别退回关闭、
显示名回退、关闭态不可用）。

---

## V2 补丁：手机连电脑本地模型的三个坎（2026-10-17 夜）

用户反馈：本地部署 Ollama（模型 qwen-flu），手机上报错
`CLEARTEXT communication to localhost not permitted by network security policy`；
且云端 API 页面不该出现本机 Ollama / LM Studio 的预设。

### 1. 明文通信被系统拦了

Android 9+ 默认禁止 http 明文。Ollama / LM Studio 都是 http，
所以必须加 `res/xml/network_security_config.xml` 放行
（manifest 的 application 上挂 `networkSecurityConfig`）。

规格边界（§8 / §9.3）这样守：本 App 唯一网络出口是 AI，目标地址完全由用户
自己配置，放行明文影响的只是他自己填的那个地址，云端 https 不受影响。

### 2. 预设按模式过滤（逻辑错误）

`AiPreset` 加 `mode` 字段，UI 只显示属于当前模式的预设 ——
本地模型页面不该冒出"OpenAI 兼容接口"（会让人以为本地也要填 Key）。
顺带把 10.0.2.2（模拟器访问宿主的特例地址）换成局域网 IP 示例，
加了 DeepSeek 预设。

### 3. 手机连电脑模型的完整路径（写在 App 里）

配置页在本地模式下直接写给用户看，连不上时也作为排障提示：
1. 手机和电脑在同一网络（同一 Wi-Fi，或手机开热点让电脑连）；
2. 电脑上 Ollama 设 `OLLAMA_HOST=0.0.0.0` 后重启（默认只听 127.0.0.1）；
3. 电脑防火墙放行 11434 端口。

`AiFailure.message(localTarget)` 据此分支：本地目标连不上/超时时
给出局域网专属步骤，云端目标仍是原来的提示（延续"提示要指对方向"的教训）。

### 4. AI 总结改为"给建议" + 独立字段

用户口径：**要建议，不要流水账式的概括**。
- `AiPrompts.summarize` → `entryAdvice`：给 2~3 条贴合日记的建议，
  明确禁止复述/概括内容（否则就是把他刚写的东西再说一遍）；
- 结果存进 `diary_entries.ai_summary` **独立列**（数据库 **v5**，
  `MIGRATION_4_5` 用 ALTER TABLE 只加一列，不重建表）；
  编辑页单独一块「AI 总结」卡片，**不可编辑**（那是模型说的话），带「清除」；
- 正文一个字都不动；原来"追加到日记末尾"的做法删掉。

测试 219 → 222。

---

## V2：自定义提示词（用户要求"AI 可塑性太低"）

设置 → AI 辅助里新增「提示词（可改，也可不改）」一栏，默认收起。
三类提示词都能改：**AI 润色** / **AI 总结（一篇日记的建议）** / **最近七天的建议**。
全都留空就完全等同原来写死的默认行为。

### 两段式结构（关键设计）

提示词拆成两段，只有后一段开放给用户：

1. **安全底线（固定，改不掉）**
   - 只输出结果本身，不要解释和客套话；
   - **不添加原文没有的信息，不推测事实，不补写情节**。
   这两条是保护私人记录的底线：不能因为用户随手写句"尽管发挥想象力"，
   就让 AI 开始给日记编情节。有单测钉死。
2. **任务要求（可自定义，留空用默认）**
   用户填的是**替换**默认要求，不是追加 ——
   追加会出现"只改错别字"和"改写成正式语气"同时存在的自相矛盾指令。

另：最近七天建议的"借款不计入收支"属于**数据说明**而非任务要求，
所以即使自定义了规则也保留，否则模型会把借来的钱当收入。

### 存储

提示词**跟随配置档案**（AiConfig 加 polishRule / entryAdviceRule / adviceRule）。
理由：换模型往往也想要不同的说话方式，不同模型对同一句提示词的敏感度差很多。
三个字段都带默认值 → 旧配置读出来自动是"留空=默认"，无需迁移。

UI：未填时把默认那一套作为灰字占位显示（空白框让人无从下手），
填过之后才出现「恢复默认」。

测试 222 → 228。

---

## V2：本地模型教程内置 + AI 总结可多版本取舍

### 1. 连接教程进到 App 里

把用户写的《本地部署连接教程》放进 `res/raw/local_model_guide.txt`，
AI 辅助页顶部有入口「不会连电脑上的本地模型？点这里看完整教程」，
点开即弹出（可滚动）。改文案不用动代码。
教程里原本写的是别的应用名，已改成「拾页」。

### 2. 同一篇日记可以反复要建议，并逐条取舍

原来第二次总结会直接盖掉第一次，用户没法比较。现在：

- 结果弹窗变成**取舍对话框**：原文 → 这次的结果 → 以前的结果，
  每段可勾选，**多选**，按**从晚到早**存入（新的在最前）；
- **默认全勾**（这次的和以前的一起留），要舍弃得主动取消 ——
  反过来"默认只留本次"虽然干净，但用户一路点下来忘勾，旧总结就没了，
  丢数据比多点两下严重；
- 旧条目沿用各自原来的时间，不会被记成"刚生成的"，否则日期就骗人了；
- 编辑页的「AI 总结」卡片支持多条显示，逐条可移除，也可整体清空。

### 存储

`diary_entries.ai_summary` 这一列的格式从"纯文本"变成"JSON 数组"
（每条带时间戳）。**不动列、不重建表**，读取时向下兼容：
解析不出 JSON 就当一条没有时间的旧记录（退回用日记的更新时间兜底），
以前要过的建议不会凭空消失 —— 这条有单测钉死。

测试 232 → 241。

---

## V2 补丁：AI 超时输入框的光标问题

用户反馈：「ai超时限制也出现了类似之前日期的光标错误」。

**同一个病因第三次出现**：框里的值不是用户直接敲的原文，而是**由整数派生出来的
字符串**，而且我们会在输入过程中改写它（丢掉非数字、去掉多余前导零）。
文本长度一变，`TextField(value: String)` 会沿用旧的光标下标 ——
光标跑到被删掉的字符前面，表现为"看着能点进去，字却输不进"。

**治本做法和日期框一致**：用 `TextFieldValue` 自己接管光标。
- 新增 `domain/NumberInput.sanitize(text, cursor)`：清洗文本**并返回光标该在的位置**
  （按"光标前保留下来几个数字"定位，比追踪每个字符的增删稳）；
  `resolve()` 负责夹范围，且用 toLongOrNull 避开超大数字溢出成负数；
- 新增 `ShiyeTextField` 的 `TextFieldValue` 重载（与 String 版共用 `ShiyeTextFieldShell`，
  避免样式代码复制两份后改漏一处）；
- 超时框改成 `TimeoutField`：自己持有 TextFieldValue，
  **失焦时**才同步外部值（输入过程中同步会把用户正敲的 70 覆盖回去，又是光标跳飞）；
- 顺带把超时范围定死：`AiConfig.MIN_TIMEOUT_SECONDS = 5` / `MAX = 600`
  （本地大模型首次加载要好几十秒，30 秒默认、上限 10 分钟）。

测试 241 → 251。

### 教训（第三次了）

凡是**值由别的东西派生、且输入过程中会被改写**的输入框，
一律用 `TextFieldValue` 重载自己接管光标。日期框、超时框都栽在这上面。

---

## V2：发给 AI 可以选择字段 + 灵感长按「复制这条」

### 1. 日记里发给 AI 可选字段

以前点「AI 建议」是把整篇（所有有内容的字段）一股脑发出去。现在多一步：
`AiStage.PICK_FIELDS` → `AiFieldPickerDialog` 让用户勾选**发哪几个字段**，
选完才进原来的"发送前告知"（能看到实际要发的文字），再决定发不发。

- 默认全选（多数时候就是要整篇），要排除由用户主动取消；
- **只有一个字段时不问**，直接进入确认 —— 没得可选还弹一次是打扰；
- 空字段照样列出来但不可选，标注「还没写」：
  悄悄略过会让人以为漏了什么；
- 弹窗里明说"没勾选的连一个字都不会送出这台手机"。

润色不受影响（它本来就只针对单个字段）。

### 2. 灵感长按加「复制这条」

菜单顺序：复制这条 / 编辑这条 / 删除这条。
剪贴板工具抽成公共 `ui/util/Clipboard.kt`（原先是 DiaryEditorScreen 的 private 函数），
两个页面共用。**不自己弹 Toast**：Android 13+ 系统会统一给"已复制"浮层。

### 踩坑

`AiTask` 里 `pickedFieldIds` 一开始写在了**类体**里而不是构造函数里 ——
类体里的 `val` 没法通过 `copy()` 改，勾选根本不生效。data class 要能被
`copy` 的字段必须进主构造函数。

测试仍是 251（新增部分是 UI，纯逻辑无新增）。

---

## 记账：新增「补账单」入口

记账首页右下角原本只有一个「＋ 记一笔」（固定记今天）。
现在照日记页「补写回忆」的做法加一个模式切换：**补账单**，
点了先问哪一天，再把那个日期带进「记一笔」表单。

### 改动

| 文件 | 改动 |
|------|------|
| `ui/components/Common.kt` | 把 `TodayEntryButton` 的骨架抽成私有 `EntryButton(mainText, toggleText, ...)`；新增 `LedgerEntryButton`（文案：＋ 记一笔 / 补账单）。日记页外观与行为不变 |
| `ui/components/Fields.kt` | `BackfillDateDialog` 的文案改成参数（`title` / `confirmText` / `futureError`），默认仍是日记那套 |
| `navigation/Route.kt` | `RecordEditor` 加 `initialDate: String? = null`，并进 `dedupeKey` |
| `ui/viewmodel/RecordEditorViewModel.kt` | 新增 `initialDate` 构造参数（默认 null = 今天）；格式不合法退回今天 |
| `AppContainer.kt` / `ShiyeApp.kt` | 工厂与调用处往下传 `initialDate` |
| `ui/screen/LedgerScreen.kt` | 加模式状态（`rememberSaveable`）、日期弹窗、新回调 `onBackfillRecord(date)` |

### 两个刻意的决定

1. **不动「记一笔」的默认行为。** 记今天的账是最常用的路径，
   为了补账多一步是给多数人添堵；补账才需要选日期。
2. **日期上限仍是"今天或更早"**（复用 `BackfillDateDialog` 的校验）。
   记明天的账没有意义，而且会让一条未来日期的账单排在最前面，看着像排序坏了。

### 顺手修的

`LedgerScreen` 列表 `contentPadding` 底部 24.dp → 88.dp。
原来是 24dp，右下角按钮压在最后一条流水上；日记页一直用的 88dp。

### 已知行为（非 bug）

补完一笔回到记账页后，模式会退回「＋ 记一笔」。
这个模式状态是页面内的 `rememberSaveable`，离屏即丢（日记页同一套，保持一致），
而且回退到"记今天"更安全 —— 不会连着补第二笔时忘了自己还在补账模式。

测试仍是 251（新增部分为 UI 交互）。

---

## 记账首页：最近 7 天 → 最近 3 天，流水行尾加日期

### 改动

| 文件 | 改动 |
|------|------|
| `ui/viewmodel/ShiyeViewModels.kt` | `LedgerViewModel` 加常数 `RECENT_WINDOW_DAYS = 3`；过滤起点由 `today-6` 改为 `today-(N-1)` |
| `ui/screen/LedgerScreen.kt` | 段落标题与空文案读同一个常数；`TransactionItem` 行尾改为 `DateTimeUtil.formatShortDateTime(date, time)`，并删掉一直没人传 true 的 `showDate` 参数 |
| `util/DateTimeUtil.kt` | 新增 `formatShortDateTime`，输出如 `10-21 14:30` |
| `test/.../DateTimeUtilTest.kt` | 新增 2 个测试（正常 / 非法日期兜底） |

### 三个需要说明的点

1. **天数抽成常数 `RECENT_WINDOW_DAYS`，正文标题也读它。**
   否则改了过滤范围忘了改那句"最近 N 天"，标题和数据就是对不上的；
   这是那种一眼看不出、但每次看到都别扭的不一致。

2. **`formatShortDateTime` 用流水自己存的 `date` / `time`，不走 `dateTime` 毫秒回算。**
   `dateTime` 是写入那一刻按当时时区算出来的，换时区回算可能偏一天，
   而这里要显示的正是"用户填的那个日期"。

3. **月和日都补零。** 原先那个 `formatDateTime` 只给日补零，
   于是 `3-05` 和 `10-15` 宽度不一样，一列看下来会参差；新函数两边都补。

### 已知取舍

`今日流水` 那一段也显示日期（如 `10-21 14:30`），虽然同一天里它是冗余的。
原因是同一笔会同时在两段出现，一边只给时间、一边给日期时间会被当成数据不一致。

测试 251 → 253。

---

## 记账加搜索、批量删除收进搜索页、修灵感搜索延迟

### 用户提的三条

1. 记账和灵感首页顶栏的「批量删除」去掉
2. 记账右上角加搜索（**从右到左第二个**按钮），批量删除放进搜索页，**按钮放在左边**
3. 灵感搜索有延迟、刚写下的搜不到

### 改动

| 文件 | 改动 |
|------|------|
| `data/local/dao/TransactionDao.kt` | 新增 `observeSearch`（关键词命中备注 / 分类名 / 账户名 + 日期区间 + 排序），CASE 表达式排序不拼 SQL |
| `data/repository/LedgerRepository.kt` | 新增 `observeSearch` |
| `ui/viewmodel/LedgerSearchViewModel.kt` | **新增**。条件防抖 250ms + `flatMapLatest` 订阅数据库流 |
| `ui/screen/LedgerSearchScreen.kt` | **新增**。搜索表单 + 结果列表 + 结果头部左侧的批量删除 |
| `navigation/Route.kt` | 新增 `Route.LedgerSearch` |
| `ui/components/Common.kt` | 新增 `BulkDeleteBar`：常态左侧「批量删除」+ 右侧条数；多选态左侧换成「全选 / 删除 N 条 / 取消」 |
| `ui/viewmodel/ShiyeViewModels.kt` | `LedgerViewModel`、`InspirationViewModel` 删掉整套批量删除状态；后者新增 `pageMessage`（删页的结果提示） |
| `ui/screen/LedgerScreen.kt` | 去掉多选态、勾选圈、结果提示 |
| `ui/screen/InspirationScreen.kt` | 同上；`InspirationBubble` 保留 `selectable`/`selected` 给搜索页用 |
| `ui/screen/InspirationSearchScreen.kt` | 接上批量删除 |
| `data/local/dao/InspirationDao.kt` + `InspirationRepository.kt` | `search` 返回 `List` → `observeSearch` 返回 `Flow` |
| `ui/viewmodel/InspirationSearchViewModel.kt` | 结果改订阅流；当前灵感页改订阅偏好 |
| `ShiyeApp.kt` | 顶栏：记账 `[⌕ 搜索][◫ 菜单]`、灵感 `[⌕ 搜索][换页]`；删掉 `BulkDeleteActions` |

### 灵感搜索为什么搜不到刚写的（两个叠加的原因）

1. **结果是一次性查询而非订阅。** 原来 `dao.search()` 返回 `List`，只在条件变化那一刻打一次库，
   之后库里再变页面拿到的还是那份快照 —— 改一下关键词才突然出现。现在返回 `Flow`，
   Room 在表变化时重新发射。
2. **当前灵感页是构造时取一次的。** `pageId` 原来是构造参数，而这个 ViewModel 按类复用（没有 key），
   实例在第一次进搜索页时建好就不重建 —— 之后换页再进搜索，搜的还是**第一次进入时那一页**，
   新页里刚写的自然一条都搜不到。现在 `currentPageId` 直接订阅偏好里的"当前页"。

### 三个刻意的决定

1. **记账搜索不填条件时列出全部流水。** 正好覆盖"想批量删一批"的场景：
   打开搜索、勾几条、删，不需要先想关键词。
2. **批量删除按钮放结果区头部左侧**（不是顶栏、也不是右下角）。
   破坏性入口挂在首页顶栏太容易顺手点到；放在结果上方左端，是"先筛出来再处理"的动线。
3. **删页的提示改用独立的 `pageMessage`**，不复用 `errorMessage`：
   一个是"你填的东西不行"（error 色、2 秒），一个是"刚才那件事做完了"（primary 色、2.5 秒）。

### 已知取舍

- 记账搜索的关键词命中**备注、分类名、账户名**。分类改名后旧账单按当时的名字照样搜得到。
- 搜索页顶部只有一个返回箭头，批量删除的"取消"在 `BulkDeleteBar` 里。

测试仍是 253（新增部分为 UI 与 DAO 查询）。

---

## 记账搜索结果的时间前面加年月日

记账首页最近才加的行尾日期是 `10-21 14:30`（不带年份）。搜索页沿用了同一份组件，
但**两种列表的时间尺度完全不同**：首页只看最近 3 天，年份在那里是纯噪声；
搜索结果的日期范围由用户自己填，横跨几年时 "10-21" 是哪一年得靠猜。

- `util/DateTimeUtil.kt`：新增 `formatFullDateTime(date, time)` → `2026-10-21 14:30`。
  和短格式一样用流水自己存的 `date`/`time`，非法日期同样退回只显示时间。
- `ui/screen/LedgerScreen.kt`：`TransactionItem` 加 `fullDate: Boolean = false`，
  默认仍是短格式（`LedgerScreen` 两处调用保持原样）。
- `ui/screen/LedgerSearchScreen.kt`：结果列表传 `fullDate = true`。
- `test/.../DateTimeUtilTest.kt`：新增 2 个测试（正常 / 非法日期兜底）。

测试 253 → 255。
