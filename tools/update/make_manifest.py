"""生成应用内更新用的 manifest.json —— 一条命令搞定一次发版。

用法：
    # 1. 先打好包
    gradle assembleRelease

    # 2. 写一份更新说明
    #    （也可以不写，直接 --note "修了几个 bug"）

    # 3. 生成清单
    python tools/update/make_manifest.py --changelog 更新公告-v3.8.md

    # 4. 把 tools/update/dist/ 里的东西传上去（见 README.md）

它会自动完成：
  · 从 app/build.gradle.kts 读 versionCode / versionName（不靠手填，不会写错）
  · 找到 app/build/outputs/apk/release/app-release.apk（也可 --apk 指定路径）
  · 算出 apkSize 和 apkSha256（App 端会用它校验下载有没有被动手脚）
  · 从 tools/assets/app_library.json 读词库版本，顺手支持词库热更新
  · 从 tools/update/publish.json 读仓库地址，拼出 Gitee + GitHub 双份下载地址
  · 写出 tools/update/dist/manifest.json，并把 APK 复制成规范文件名

产物（tools/update/dist/ 目录）：
    manifest.json                        → 传到仓库根目录
    CollocationMemory-3.8-release.apk    → 传到 Gitee 发行版
    library/app_library.json             → 传到仓库 library/ 目录（有词库更新时）
    library/corrections.json             → 同上（有释义订正时）
"""
import argparse
import hashlib
import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

BUILD_GRADLE = ROOT / "app" / "build.gradle.kts"
DEFAULT_APK = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
LIBRARY_ASSET = ROOT / "tools" / "assets" / "app_library.json"
CORRECTIONS_FILE = ROOT / "tools" / "update" / "corrections.json"
PUBLISH_CONFIG = ROOT / "tools" / "update" / "publish.json"
DIST_DIR = ROOT / "tools" / "update" / "dist"


class Fail(Exception):
    """用户能看懂的错误，直接打印后退出"""


def read_version_from_gradle() -> tuple[int, str]:
    """从 build.gradle.kts 读 versionCode / versionName，避免手填写错。

    versionName 允许写成三元表达式（低版本兼容包用 -PlegacyCompat 切换版本名），
    此时取最后一个字符串字面量，也就是 else 分支＝主线版本号。
    """
    if not BUILD_GRADLE.exists():
        raise Fail(f"找不到 {BUILD_GRADLE}")
    text = BUILD_GRADLE.read_text(encoding="utf-8")

    code_match = re.search(r"^\s*versionCode\s*=\s*(\d+)", text, re.MULTILINE)
    name_line = re.search(r"^\s*versionName\s*=(.+)$", text, re.MULTILINE)
    if not code_match or not name_line:
        raise Fail("app/build.gradle.kts 里读不到 versionCode / versionName")

    quoted = re.findall(r'"([^"]+)"', name_line.group(1))
    if not quoted:
        raise Fail("app/build.gradle.kts 的 versionName 里找不到版本号字符串")

    return int(code_match.group(1)), quoted[-1]


def read_library_version() -> int:
    """读内置词库版本号；没有就返回 0（= 不启用词库热更新）"""
    if not LIBRARY_ASSET.exists():
        return 0
    try:
        data = json.loads(LIBRARY_ASSET.read_text(encoding="utf-8"))
        return int(data.get("version", 0))
    except Exception as e:
        print(f"  ! 读词库版本失败（按 0 处理）：{e}")
        return 0


def load_publish_config(path: Path) -> dict:
    if not path.exists():
        raise Fail(f"找不到 {path}")
    raw = json.loads(path.read_text(encoding="utf-8"))
    # 下划线开头的都是给人看的注释，不是配置
    return {k: v for k, v in raw.items() if not k.startswith("_")}


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_urls(cfg: dict, version_name: str, apk_name: str,
               tag_override: str | None = None) -> list[str]:
    """拼下载地址：国内源（Gitee）在前，GitHub 兜底在后。

    tag 写法做了容错：清单里同时登记「带前缀」和「不带前缀」两个变体
    （如 v3.9 和 3.9）。手动在 Gitee 建发行版时 tag 名偶尔会写错，
    少一个字母就是 404；App 按顺序尝试，哪个通用哪个。
    用 --tag 明确指定后只登记指定的那个，不再猜变体。
    """
    if tag_override:
        tags = [tag_override]
    else:
        prefix = cfg.get("tagPrefix", "v")
        tags = [f"{prefix}{version_name}"]
        if prefix and prefix not in version_name:
            tags.append(f"{version_name}")  # 不带前缀的变体
    urls = []
    for key in ("giteeBase", "githubBase"):
        base = (cfg.get(key) or "").rstrip("/")
        if not base or "REPLACE_ME" in base:
            continue
        for tag in tags:
            urls.append(f"{base}/releases/download/{tag}/{apk_name}")
    if not urls:
        raise Fail(
            "publish.json 里的 giteeBase / githubBase 还没填。\n"
            "  至少填一个：把 REPLACE_ME 换成你真实的用户名和仓库名。"
        )
    return urls


def build_file_urls(cfg: dict, relative_path: str, branch: str = "master") -> list[str]:
    """词库文件走 raw 直链。

    为什么不放 release：词库改一次就该立刻生效，不用每次都新建发行版。
    raw 链的写法两家不一样，这里分开拼：

      Gitee   https://gitee.com/<u>/<r>/raw/master/<path>
      GitHub  https://raw.githubusercontent.com/<u>/<r>/master/<path>
              （主机名不同，而且路径里没有 /raw 这一段）
    """
    urls = []

    gitee = (cfg.get("giteeBase") or "").rstrip("/")
    if gitee and "REPLACE_ME" not in gitee:
        urls.append(f"{gitee}/raw/{branch}/{relative_path}")

    github = (cfg.get("githubBase") or "").rstrip("/")
    if github and "REPLACE_ME" not in github:
        raw_base = github.replace("github.com", "raw.githubusercontent.com")
        urls.append(f"{raw_base}/{branch}/{relative_path}")

    return urls


def main() -> int:
    parser = argparse.ArgumentParser(
        description="生成应用内更新用的 manifest.json",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--apk", type=Path, default=None,
                        help=f"APK 路径，默认 {DEFAULT_APK.relative_to(ROOT)}")
    parser.add_argument("--changelog", type=Path, default=None,
                        help="更新说明文件（.md / .txt），内容会原样放进弹窗")
    parser.add_argument("--note", type=str, default=None,
                        help="直接给一句更新说明，和 --changelog 二选一")
    parser.add_argument("--no-library", action="store_true",
                        help="本次不启用词库热更新（清单里不写 library 段）")
    parser.add_argument("--config", type=Path, default=PUBLISH_CONFIG,
                        help="发布配置路径，默认 tools/update/publish.json")
    parser.add_argument("--tag", type=str, default=None,
                        help="手动指定发行版 tag 名（如 --tag 3.9v）。不填则按 publish.json 的 tagPrefix 自动生成，并附不带前缀的变体容错")
    parser.add_argument("--dry-run", action="store_true",
                        help="只打印结果，不写文件")
    args = parser.parse_args()

    print("=" * 64)
    print("  生成更新清单 manifest.json")
    print("=" * 64)

    # ---- 1. 版本号 ----
    version_code, version_name = read_version_from_gradle()
    print(f"\n[1/6] 版本号：versionCode={version_code}  versionName={version_name}")

    # ---- 2. 找 APK ----
    apk_path = args.apk or DEFAULT_APK
    if not apk_path.exists():
        raise Fail(
            f"找不到 APK：{apk_path}\n"
            "  先打一个正式包：\n"
            '    & "$env:USERPROFILE\\.gradle\\wrapper\\dists\\gradle-8.4-bin\\<hash>\\gradle-8.4\\bin\\gradle.bat" assembleRelease'
        )
    apk_size = apk_path.stat().st_size
    print(f"[2/6] APK：{apk_path.name}  {apk_size / 1024 / 1024:.1f} MB")

    # ---- 3. 算校验值 ----
    sha = sha256_of(apk_path)
    print(f"[3/6] SHA-256：{sha}")

    # ---- 4. 更新说明 ----
    if args.changelog and args.note:
        raise Fail("--changelog 和 --note 只能用其中一个")

    changelog = ""
    if args.changelog:
        if not args.changelog.exists():
            raise Fail(f"找不到更新说明文件：{args.changelog}")
        changelog = args.changelog.read_text(encoding="utf-8").strip()
    elif args.note:
        changelog = args.note.strip()
    else:
        changelog = f"更新到 v{version_name}"

    # 弹窗里放不下太长，超了就截断并提示作者
    MAX_CHANGELOG = 800
    if len(changelog) > MAX_CHANGELOG:
        print(f"      ! 更新说明 {len(changelog)} 字，弹窗里太长，已截到 {MAX_CHANGELOG} 字")
        print("      （完整说明建议只发到群里，弹窗里放要点即可）")
        changelog = changelog[:MAX_CHANGELOG] + "\n（更多说明见群公告）"
    print(f"[4/6] 更新说明：{len(changelog)} 字")

    # ---- 5. 仓库地址 ----
    cfg = load_publish_config(args.config)
    apk_name = f"{cfg.get('apkBaseName', 'CollocationMemory')}-{version_name}-release.apk"
    apk_urls = build_urls(cfg, version_name, apk_name, tag_override=args.tag)
    print(f"[5/6] 下载地址（按优先级，{len(apk_urls)} 个镜像）：")
    for u in apk_urls:
        print(f"        {u}")

    manifest: dict = {
        "app": {
            "versionCode": version_code,
            "versionName": version_name,
            "minSupportedVersionCode": int(cfg.get("minSupportedVersionCode", 0)),
            # 主线 APK 的 minSdk 门槛：低版本兼容包的 App 看到它就知道
            # 这个更新包装不上，静默跳过整包更新（词库热更新不受影响）
            "minSdk": int(cfg.get("mainMinSdk", 35)),
            "apkUrls": apk_urls,
            "apkSize": apk_size,
            "apkSha256": sha,
            "changelog": changelog,
            "forceUpdate": bool(cfg.get("forceUpdate", False)),
        }
    }

    # ---- 6. 词库热更新 ----
    library_version = 0 if args.no_library else read_library_version()
    if library_version > 0:
        manifest["library"] = {
            "version": library_version,
            "libraryUrls": build_file_urls(cfg, "library/app_library.json"),
            "correctionsUrls": build_file_urls(cfg, "library/corrections.json"),
            "notes": f"内置词库 v{library_version}",
        }
        print(f"[6/6] 词库热更新：v{library_version}（无需重装即可加词/改释义）")
    else:
        print("[6/6] 词库热更新：本次关闭")

    text = json.dumps(manifest, ensure_ascii=False, indent=2)

    if args.dry_run:
        print("\n--- 预览（--dry-run，未写文件）---")
        print(text)
        return 0

    # ---- 落盘 ----
    DIST_DIR.mkdir(parents=True, exist_ok=True)
    (DIST_DIR / "manifest.json").write_text(text, encoding="utf-8")

    target_apk = DIST_DIR / apk_name
    shutil.copy2(apk_path, target_apk)

    if library_version > 0:
        lib_dir = DIST_DIR / "library"
        lib_dir.mkdir(parents=True, exist_ok=True)
        shutil.copy2(LIBRARY_ASSET, lib_dir / "app_library.json")
        if CORRECTIONS_FILE.exists():
            shutil.copy2(CORRECTIONS_FILE, lib_dir / "corrections.json")
            print("\n  · 检测到 corrections.json，已一并打包（释义订正会传给老用户）")

    print("\n" + "=" * 64)
    print("  完成！产物在 tools/update/dist/")
    print("=" * 64)
    print(f"""
接下来手动上传三步（只需在网页上点几下，详见 README.md）：

  1. Gitee 仓库 → 「发行版」→「新建发行版」
       Tag:    {cfg.get('tagPrefix', 'v')}{version_name}
       附件：  {apk_name}

  2. Gitee 仓库根目录上传/更新 manifest.json
       （内容就是 tools/update/dist/manifest.json）

  3. 若有词库更新，把 dist/library/app_library.json
       （和 corrections.json）上传到仓库的 library/ 目录

传完就可以打开了：App 最多 6 小时后自动检查到，
想立刻验证就在 App 里「设置 → 版本与更新 → 检查更新」。
""")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Fail as e:
        print(f"\n[失败] {e}\n")
        sys.exit(1)
