# 应用内更新 —— 使用说明

从此以后，用户**不用再去群里找安装包**了：App 会自己检查新版本、自己在应用内下载、
自己跳到安装界面。词库和释义的更新更是完全无感 —— 连包都不用换。

---

## 一、两条更新通道（先搞清这个）

| | **词库热更新** | **整包更新** |
|---|---|---|
| 更新什么 | 词条、释义、删错词 | 代码、界面、新功能 |
| 用户要做什么 | **什么都不用做** | 点一下「安装」 |
| 要不要重新打包 | 不用 | 要 |
| 什么时候用 | 加词、改释义、删错词 | 改了界面、加了功能、修了 bug |

判断标准很简单：

- **只动了 `tools/assets/app_library.json`** → 只需词库热更新，**不用发新版**
- **动了 `app/src/` 下的任何代码** → 必须整包更新

---

## 二、第一次准备（只做一次）

### 1. 建一个放更新文件的仓库

Gitee 免费、国内快，推荐用它。

1. 注册/登录 [gitee.com](https://gitee.com)
2. 新建仓库，名字随意，比如 `collocation-update`
3. 必须是**公有**仓库（私有仓库的 raw 链接要带 token，App 拿不到）
4. 建好之后，仓库地址形如 `https://gitee.com/你的用户名/collocation-update`

### 2. 填配置

打开 `tools/update/publish.json`，把两处 `REPLACE_ME` 换掉：

```json
{
  "giteeBase": "https://gitee.com/你的用户名/collocation-update",
  "githubBase": "https://github.com/REPLACE_ME/collocation-update"
}
```

> 只用 Gitee 的话，`githubBase` 留 `REPLACE_ME` 就行，脚本会自动跳过它。
> 两处都填的话，Gitee 是主源、GitHub 是备用源 —— 某个源挂了 App 会自动换另一个。

### 3. 把地址写进 App

打开 `app/src/main/java/com/example/collocationmemory/data/remote/UpdateModels.kt`，
把 `UpdateConfig.MANIFEST_URLS` 里的 `REPLACE_ME` 换成同样的用户名和仓库名：

```kotlin
val MANIFEST_URLS: List<String> = listOf(
    "https://gitee.com/你的用户名/collocation-update/raw/master/manifest.json",
    "https://raw.githubusercontent.com/REPLACE_ME/collocation-update/master/manifest.json"
)
```

然后打一个正式包，以后就不用再动这里了。

---

## 三、发一版更新（以后每次的固定动作）

### 情况 A：只加了词 / 改了释义（不用发包，最省事）

1. 改 `tools/assets/app_library.json`，把里面的 `version` **+1**
   （如果是改旧词的释义，还要在 `tools/update/corrections.json` 里加一条，见下方「注意」）
2. 把 `tools/assets/app_library.json` 传到 Gitee 仓库的 `library/` 目录
3. 跑一次清单生成，更新 `manifest.json`：

```bash
python tools/update/make_manifest.py --apk 任意已有的apk --note "词库更新"
```

> 更简单的做法：直接手改服务器上的 `manifest.json`，把 `library.version` 加 1。
> App 那边一比对，发现远端版本更高，就自动拉了。

**用户侧效果**：下次打开 App，词库已经是新的了，什么都不用做。

### 情况 B：改了代码 / 界面（要发新版）

**第 1 步：升版本号**

改 `app/build.gradle.kts`：

```kotlin
versionCode = 30          // 必须 +1，且大于任何已发出去的包
versionName = "3.9"
```

> ⚠️ `versionCode` 忘了 +1，用户那边会检测不到更新，或者被系统拒绝覆盖安装。

**第 2 步：打包**

```powershell
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.4-bin\<hash>\gradle-8.4\bin\gradle.bat" assembleRelease
```

产物：`app\build\outputs\apk\release\app-release.apk`

> ⚠️ 必须用**同一把** `release.keystore` 签名（项目已配好，别改）。
> 签名不一致 → 系统拒绝覆盖安装 → 用户只能卸载重装 → **数据全丢**。

**第 3 步：生成更新清单**

```bash
python tools/update/make_manifest.py --changelog 更新公告-v3.9.md
```

它会自动读版本号、算 APK 的 SHA-256 和大小、生成 `tools/update/dist/` 下的所有文件。
`--changelog` 里的文字会**原样显示在更新弹窗里**，建议只写要点（脚本超过 800 字会截断）。

**第 4 步：上传（网页上点几下）**

去 Gitee 仓库页面：

1. **「发行版」→「新建发行版」**
   - Tag：`v3.9`（和脚本提示的一致）
   - 附件：上传 `tools/update/dist/CollocationMemory-3.9-release.apk`
2. **仓库根目录**上传/覆盖 `manifest.json`（内容取 `tools/update/dist/manifest.json`）
3. 有词库更新的话，把 `tools/update/dist/library/` 里的文件传到仓库 `library/` 目录

传完就生效了。App 最多 6 小时后自动检查到；想立刻验证，就用**旧版本**的 App 打开
「设置 → 版本与更新 → 检查更新」。

---

## 四、强制更新

默认是**可以跳过**的（用户点「以后再说」后，同一个版本不再打扰他，但下个版本还会提示）。

需要强制时，改 `tools/update/publish.json`：

```json
"minSupportedVersionCode": 28
```

含义：**手机上装的版本低于 28 的，必须更新完才能用**（弹窗没有「以后再说」，
点外面也关不掉）。

什么时候才该用：

- 数据库表结构变了，旧版本会读写坏数据
- 修了会导致数据损坏的严重 bug
- 旧版本调用的接口已经下线

**平时请留 0**。强制更新很赶人，用多了用户会烦。

---

## 五、本地自测（不用真的发版）

想先把流程跑通再对外发布：

```bash
# 1. 生成清单（用当前工程打的包）
python tools/update/make_manifest.py --note "测试更新流程"

# 2. 起假服务器
python tools/update/serve_local.py
```

然后临时把 `UpdateConfig.MANIFEST_URLS` 第一项改成：

```kotlin
"http://10.0.2.2:8000/manifest.json",     // 模拟器
// 或真机：
// "http://127.0.0.1:8000/manifest.json",  + adb reverse tcp:8000 tcp:8000
```

重装 App，就能看到完整的「检查 → 下载 → 安装」流程了。

> 明文 HTTP 只在 `10.0.2.2` / `127.0.0.1` / `localhost` 上被放行
> （见 `res/xml/network_security_config.xml`），其余域名一律必须 HTTPS。
>
> **测完记得把地址改回 Gitee。**

---

## 六、必须知道的三件事

### 1. 最后一下「安装」按钮，一定要用户自己点

Android **不允许**第三方 App 静默安装 APK。这是系统的安全底线，
除非上架应用商店走商店的自动更新，否则绕不过去。

App 能做到的是：`检查 → 下载 → 校验 → 自动跳到安装界面`，
用户只需要在系统弹窗上点一下「安装」，比去群里翻文件省事太多。

用户第一次更新时，还会被要求去系统设置里打开「允许搭配记忆安装未知应用」。
App 里有引导，一步一步写着怎么点。

### 2. 学习数据不会丢

只要满足这三条，覆盖安装后学习进度、生词本、复习排期、打卡记录全部原样保留：

| # | 条件 | 项目里的现状 |
|---|---|---|
| 1 | 用同一把密钥签名 | ✅ 已配置（`release.keystore`），别改 |
| 2 | `versionCode` 递增 | ⚠️ **每次发版要手动 +1** |
| 3 | 表结构变了要写迁移 | ⚠️ 改了 `Collocation` 实体就要加 `Migration` |

APK 下载后 App 还会比对**签名证书**，对不上就拒绝安装 ——
这样即使更新地址被劫持，也装不进别人的恶意包。

### 3. 词条一旦发出去，就改不掉也删不掉

这是 `seedBuiltInLibrary`「只新增、不更新」策略的必然结果 ——
它是为了保护用户的学习进度（进度挂在那一行上，覆盖会让进度错位）。

所以：

| 你的操作 | 走哪条路 | 老用户那边 |
|---|---|---|
| 新增词条 | 改 `app_library.json`，`version` +1 | ✅ 自动出现 |
| **修改**已有词条释义 | 加进 `tools/update/corrections.json` | ✅ 自动生效 |
| **删除**错误词条 | 加进 `tools/update/corrections.json` 的 `removals` | ✅ 自动删掉 |

`corrections.json` 的格式（**这就是改释义的通道，不用发新版**）：

```json
{
  "version": 4,
  "definitionFixes": {
    "往年固定搭配单词本": {
      "play an key / vital role in": "起重要作用"
    }
  },
  "removals": {
    "往年固定搭配单词本": ["steps to do"]
  }
}
```

- 键是**词本名 + 英文**，精确匹配，匹配不到就跳过，不会误伤别的词
- 只改释义或删整条，**绝不碰学习进度字段**
- 这个文件也会被一起打包上传，老用户下次打开 App 就生效

---

## 七、目录里都是什么

```
tools/update/
├── publish.json           ← 你要填的唯一一个配置文件（仓库地址、强制更新开关）
├── make_manifest.py       ← 一键生成 manifest.json
├── serve_local.py         ← 本地假服务器，测试用
├── corrections.json       ← （可选）释义订正表，不用发版就能改释义
├── README.md              ← 本文件
└── dist/                  ← 生成的产物，直接拿去上传
    ├── manifest.json
    ├── CollocationMemory-3.9-release.apk
    └── library/
        ├── app_library.json
        └── corrections.json
```
