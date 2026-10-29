# 搭配记忆 (CollocationMemory)

> **开源说明**：本仓库**不含**「蓝哥固定搭配单词本」等授权专有词库内容（版权归湖北小蓝书教育科技有限公司）。`tools/assets/app_library.json` 为**占位空库**（`version=0`），克隆后可直接构建但无内置词条；正版词库通过应用内词库热更新通道获取。签名密钥与密码（`release.keystore`、`gradle.properties`）不入库，请复制 `gradle.properties.example` 后填入你自己的签名信息再构建。

一个专注于固定搭配记忆的间隔重复复习 Android App，使用 Kotlin + Jetpack Compose 开发。

## 功能特性

- **内置词库**：187 条固定搭配打包在 APK 内，首次启动自动写入，装完即用。

- **JSON 导入搭配数据**：从本地 JSON 文件导入固定搭配、释义、例句和标签（可选，用于追加别的词库）。

- **新学模式 (Learn)**：卡片正面显示英文搭配，四个中文释义选项，翻转查看答案。

- **复习模式 (Review)**：按改良艾宾浩斯间隔重复算法推送今日需复习搭配。

- **今日 / 明日明细**：首页「今日概览 → 查看明细」可看今天新学和复习过的搭配；Review 卡片上的「明日 N」可预览明天要复习哪些。

- **答错即时反馈**：选错的那一刻就计数（累计错误次数），并给出真实数据文案；答错后可「再考一次」把这张卡排到队尾重考，不做罚抄。

- **盲盒模式**：设置里可开启，复习顺序随机 —— 抽到哪个背哪个。

- **学习战绩分享图**：统计页右上角一键生成 1080×1620 竖版分享图，可直接发朋友圈/微信。

- **拼写巩固**：每组学习/复习结束后进入拼写环节加深记忆。

- **生词本**：答错的搭配自动加入生词本，可手动移除。

- **统计与签到**：学习热力图、连续打卡、学习时长等统计。

- **主题切换**：支持明亮 / 深邃 / 跟随系统三种主题。

## 技术栈

- Kotlin

- Jetpack Compose

- Room 数据库

- DataStore

- Navigation Compose

- Material 3

## 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本

- JDK 17

- 最低支持 Android 15（API 35）

## 运行步骤

1. 用 Android Studio 打开 `CollocationMemory` 文件夹。
2. 等待 Gradle 同步完成。
3. 连接 Android 15+ 设备或启动模拟器。
4. 点击 `Run`（▶）运行 App。

## 单词本（多词本）

### 当前的词本

内置 187 条固定搭配归属词本 **「蓝哥固定搭配单词本」**（由 `app_library.json` 的 `bookName` 决定）。

### 再添加一个单词本

两条路：

| 方式 | 做法 | 适用 |
|---|---|---|
| **给普通用户** | App 设置页 →「新建一个单词本导入」→ 选 JSON 文件 | 不重新打包就能加词本；词本名取文件名 |
| **内置进 APK** | 改 `app_library.json` 的 `bookName` + `items`，`version` +1，重打包 | 要发给别人、装完即用 |

### 进度是怎么算的（重要设计）

- **每个词条的进度字段**（复习排期、熟练度等）都存在**自己那一行**上，所以每个词本的进度是独立的。
- 但「**这个词你会不会**」是全局事实：同一个英文词只要在**任何**词本里学会过，
  它在其它词本里也会被算作「已学」，**不会让你重复背一遍**。
- 同一个英文词出现在两个词本里时，会**各存一行**（这样才能各自统计进度）；
  界面上会给这种行标上「**熟 · 已在其他词本学会**」，并提供「标记为已学会」。

判断「会不会」用的是 SQL 里的关联子查询：

```sql
-- 已学数（当前词本）
SELECT COUNT(*) FROM collocations WHERE bookName = :book AND EXISTS
  (SELECT 1 FROM collocations c2 WHERE c2.english = collocations.english AND c2.isLearned = 1)
```

所以：**新学队列会自动跳过所有词本里已会的词**，而首页进度仍按当前词本统计。

> 实测（模拟器）：蓝哥本学会 `have been to` 后，新导入的词本里该词显示为「已学」，
> 且**不进入新学队列**；同时该词本自己的新词照常进入队列。

---

## 版权与使用声明（维权证据）

**本软件为完全免费软件**，无任何收费项目，禁止私自售卖、倒卖、二次改版分发。
软件内全部学习资料（词库、释义、例句）版权归属：**湖北小蓝书教育科技有限公司**，
由该公司授权本软件内置使用，仅允许个人非盈利学习使用。

声明文字同时存在于**三处**，构成完整的证据链：

| # | 位置 | 作用 |
|---|---|---|
| 1 | `app/src/main/java/.../legal/CopyrightNotice.kt` | 源码 + Git 提交历史，可证明创作时间与作者身份 |
| 2 | `app/src/main/res/raw/copyright_notice.txt` | **打进 APK**，反编译即可提取比对 |
| 3 | `app/build.gradle.kts` 的 `buildConfigField` | 生成 `BuildConfig.COPYRIGHT_*`，同样在 dex 里 |
| 4 | `tools/copyright_notice.txt` | 仓库副本，便于人工查阅 |

### App 内的呈现

首次启动会弹出「版权与使用声明」，**必须点击「我同意」才能进入软件**；
弹窗下方还有一个「同意，下次不再提示」，点了之后启动不再弹出。

- 弹窗组件：`ui/screens/CopyrightNoticeDialog.kt`
- 偏好开关：`UserPreferencesDataStore.COPYRIGHT_NOTICE_ACCEPTED`（默认 `false`）
- 「完全免费软件」用语义红色强调（`AppColors.errorColor()`，已纳入对比度自检）

### 发现倒卖时怎么取证

1. 拿到对方分发的 APK；
2. 解包后查 `res/raw/copyright_notice.txt`（AAPT2 可能把文件名缩短，用
   `aapt2 dump resources <apk> | grep copyright` 定位真实文件名），或直接在
   `classes*.dex` 里搜 `湖北小蓝书教育科技有限公司` / `完全免费软件`；
3. 与本仓库的 `tools/copyright_notice.txt` 逐字比对即可证明来源。

实测：本项目 APK 内可提取到完整声明，`classes.dex` 里
`湖北小蓝书教育科技有限公司`、`完全免费软件`、`搭配记忆`、`CopyrightNotice` **全部命中**。

### 改动声明文字时

**必须同步三处**，然后跑校验脚本（内容不一致会 FAIL）：

```powershell
python tools\dev\check_copyright_sync.py
```

---

## 内置词库（装完即用）

187 条固定搭配已经打包进 APK，**首次启动会自动写入本地数据库**，使用者不需要导入任何文件。

- 数据文件：`tools/assets/app_library.json`（由 `app/build.gradle.kts` 的 `assets.srcDirs` 打进 APK）
- 写入逻辑：`CollocationMemoryApplication.seedBuiltInLibraryIfNeeded()`

### 往内置词库加新词（不会影响别人的学习数据）

**只需两步：**

1. 把新词库合并进 `tools/assets/app_library.json`
2. 把 App 的 `versionCode` +1，重新 `assembleRelease`

合并这一步有现成脚本，会自动去重、跳过已有词、并把词库 `version` +1：

```powershell
python tools\dev\merge_docx_to_library.py "C:\path\to\新词库.docx"
```

脚本会打印一份报告（新增多少条、跳过了哪些、校验是否 PASS），照着看一眼即可。

**不需要**写数据库迁移、**不需要**动表结构。

原理（`seedBuiltInLibrary` 的增量合并）：启动时先比对词库版本号，版本变高才执行；执行时**只新增本地还没有的英文搭配，绝不更新或删除已有记录**。所以老用户升级后：

- 学习进度、生词本、复习排期、拼写次数 —— 全部原样保留
- 新词作为「未学习」条目追加进来，可以直接开始学
- 去重以 `english` 为准；若某词已在本地（包括用户自己 JSON 导入的），跳过、不覆盖

> 注意：词条一旦写进用户手机，就不会再被"改"或"删"。
> 如果你要修改某个已有词条的释义，老用户那边不会生效——需要他手动清空数据后重新播种。

### 内置词库版本 vs App 版本

两个版本号是独立的，互不影响：

| 场景 | 要改什么 |
|---|---|
| 只加词 | 跑 `merge_docx_to_library.py`（自动把词库 `version` +1），App 的 `versionCode` 也 +1（因为要发新 APK） |
| 只改代码/界面 | 只改 `versionCode` |
| 改数据库表结构 | `versionCode` +1，**并且**在 `AppDatabase` 写 `Migration` + `version` +1 |

> 实际记录：1.5 版本把「固定搭配3」并入词库，词库 117 → 187 条（`version` 1 → 2）。
> 升级实测：老用户「已学习 10 / 生词本 6 / 每日记录 1」全部保留，新词 71 条以「未学习」追加。

### 词条一旦写进用户手机，就改不掉也删不掉

这是保护学习进度的必然代价（`english` 是唯一键，已存在就跳过）：

| 你的操作 | 老用户那边 |
|---|---|
| 新增词条 | ✅ 正常出现 |
| **修改**已有词条释义 | ❌ 不生效，他看到的还是旧释义 |
| **删除**某个词条 | ❌ 删不掉 |

所以**加词前把释义写准**。已知的历史遗留：`be famous for` 在早期词库里重复过，
老用户库里仍有 2 条同行条目（合并规则不删数据），全新安装则是干净的 1 条。

## 发更新给别人时，怎么保证不丢他的数据

**结论：只要满足下面 3 条，别人直接装新 APK 覆盖安装即可，学习记录不会丢。**

| # | 必须做到 | 漏了会怎样 |
|---|---|---|
| 1 | 用**同一把** `release.keystore` 签名 | 签名不一致 → 系统拒绝覆盖安装 → 只能卸载 → **数据全丢** |
| 2 | 升 `versionCode`（`app/build.gradle.kts`），例如 2 → 3 | 部分机型拒绝覆盖安装 |
| 3 | 数据库结构变了就**写迁移**（`AppDatabase` 里加 `Migration`） | 见下方说明 |

### 为什么去掉 `fallbackToDestructiveMigration()`

原来开了这个兜底：一旦漏写迁移，Room 会**静默删除整个数据库**——不报错、不提示，用户的学习记录直接清零。
现在去掉了，漏写迁移时 Room 会**抛异常崩溃**。崩溃是吵闹的、能立刻发现并修复；静默丢数据是悄悄的，等用户来反馈时已经无法挽回。

### 每次发版前的检查清单

1. `versionCode` +1，`versionName` 更新
2. 改过实体/表结构？→ 在 `AppDatabase` 加对应 `Migration`，并把 `version` +1
3. `assembleRelease` 打正式包
4. **先在旧版有数据的设备上做一次覆盖安装测试**（不要卸载）
5. 再发给别人

### 数据安全网

- `backup_rules.xml` / `data_extraction_rules.xml` 已包含 `database` 域 → 用户换机、恢复出厂时可随系统备份带走学习记录
- 开发时如需在真机上装 debug 包排查问题，**先备份**：
  `adb shell "run-as com.example.collocationmemory tar cf - databases" > backup.tar`

## 应用内更新（3.8 起）

**用户不用再去群里找安装包了。** App 会自己检查新版本、自己在应用内下载、自己跳到安装界面。

### 两条通道

| | **词库热更新** | **整包更新** |
|---|---|---|
| 更新什么 | 词条、释义、删错词 | 代码、界面、新功能 |
| 用户要做什么 | **什么都不用做** | 点一下「安装」 |
| 要不要重新打包 | 不用 | 要 |
| 触发方式 | 启动时静默进行 | 启动时检查 → 弹窗提示 |

判断标准：**只改了 `tools/assets/app_library.json`** 走热更新；**动了 `app/src/` 任何代码**必须整包更新。

### 代码在哪

```
app/src/main/java/com/example/collocationmemory/
├── data/remote/
│   ├── UpdateModels.kt        # manifest 数据结构 + UpdateConfig（★ 换服务器只改这里）
│   └── RemoteFetch.kt         # HttpURLConnection 工具（文本获取 / 带进度下载 / sha256）
├── data/repository/
│   ├── UpdateRepository.kt    # 业务总入口：检查版本、下载 APK、同步词库
│   └── LibraryCorrections.kt  # 订正表：内置基线 + 远端增量合并
├── util/ApkInstaller.kt       # 权限引导 + 唤起系统安装界面 + 签名校验
└── ui/screens/UpdateDialog.kt # 更新弹窗（发现新版/下载中/去授权/开始安装）
```

### 怎么发一版

完整步骤见 **`tools/update/README.md`**。核心就三步：

```bash
# 1. versionCode +1 并打包
gradle assembleRelease

# 2. 生成清单（自动算 sha256、读版本号、拼多镜像地址）
python tools/update/make_manifest.py --changelog 更新公告-v3.9.md

# 3. 把 tools/update/dist/ 里的文件传到 Gitee
```

### 两个必须知道的限制

**① 最后一下「安装」按钮必须用户自己点。**
Android 不允许第三方 App 静默安装 APK（系统安全底线，除非上架应用商店）。
App 能做到 `检查 → 下载 → 校验 → 自动跳安装页`，用户只需点一次「安装」。
首次更新还会引导用户去系统设置里打开「允许安装未知应用」。

**② 强制更新慎用。**
默认用户可以点「以后再说」（同一版本不再打扰，下个版本还会提示）。
只有改了表结构、旧版本会写坏数据时才在 `tools/update/publish.json` 里设
`minSupportedVersionCode`。

### 安全措施

- **多镜像回退**：Gitee 主源挂了自动切 GitHub，用户无感
- **APK 三重校验**：文件大小 + SHA-256 + **签名证书比对本机**，任一不符拒绝安装
- **词库只增不减**：热更新复用 `seedBuiltInLibrary`，学习进度绝不被动
- **并发保护**：`CollocationRepository.libraryMutex` 串行化「启动播种」与「联网同步」，
  避免两者同时读到旧快照而插出重复词条
- **失败不阻塞**：任何一步失败都只是「这次没更新成」，不影响 App 使用

## 打包分发给别人

```powershell
# 编译已签名的 release APK
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.4-bin\<hash>\gradle-8.4\bin\gradle.bat" assembleRelease

# 产物
app\build\outputs\apk\release\app-release.apk
```

- 签名密钥：`release.keystore`（项目根目录，已加入 `.gitignore`），别名 `collocation`
- 密码写在 `gradle.properties` 的 `RELEASE_*` 属性里
- ⚠️ `debug` 和 `release` **都**用这把密钥签名（见 `app/build.gradle.kts` 的 `debug` 块），
  这样两种包可以互相覆盖安装。**绝不要改回默认 debug 密钥**，否则又要卸载重装。
- ⚠️ 当前 `minSdk = 35`，只能安装在 **Android 15 及以上**的手机

## 导入数据（可选，界面仍保留）

设置页仍可以用 JSON 文件导入别的词库，格式是一个数组：

```json
[
  {"english": "make a decision", "chinese": "做决定", "example": "I need to make a decision soon.", "tags": ["动词搭配"]}
]
```

## 项目结构

```
app/src/main/java/com/example/collocationmemory/
├── data/
│   ├── local/          # Room 实体、DAO、DataStore
│   └── repository/     # 业务仓库
├── ui/
│   ├── screens/        # 页面 Composable
│   ├── theme/          # 颜色、字体、主题
│   └── viewmodel/      # ViewModel
└── util/               # JSON 导入、间隔重复算法、日期工具
```

