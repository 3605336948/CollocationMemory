"""本地假更新服务器 —— 用来在真机/模拟器上把整条更新流程跑通，不用真的发版。

用法：
    # 先按 README 生成 dist/
    python tools/update/make_manifest.py --note "测试更新流程"

    # 起服务（默认 8000 端口）
    python tools/update/serve_local.py

然后在手机/模拟器上把 App 的更新地址指过来：

  方式 A（模拟器，最简单）
      改 UpdateConfig.MANIFEST_URLS，把第一项换成
      http://10.0.2.2:8000/manifest.json  ，重装 App。
      10.0.2.2 是模拟器访问「宿主机 localhost」的固定地址。

  方式 B（真机，用 USB 反向代理）
      adb reverse tcp:8000 tcp:8000
      再把 MANIFEST_URLS 第一项换成 http://127.0.0.1:8000/manifest.json

  明文 HTTP 只在 10.0.2.2 / 127.0.0.1 / localhost 上被放行
  （见 res/xml/network_security_config.xml），别的域名一律必须 HTTPS。

测完记得把 MANIFEST_URLS 改回 Gitee 地址！
"""
import argparse
import functools
import http.server
import os
import socket
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DIST_DIR = ROOT / "tools" / "update" / "dist"


class Handler(http.server.SimpleHTTPRequestHandler):
    """带上正确的 MIME + 关掉缓存。

    关缓存很关键：不然测第二轮更新时，App 拿到的还是上一版的 manifest。
    """

    extensions_map = {
        **http.server.SimpleHTTPRequestHandler.extensions_map,
        ".json": "application/json; charset=utf-8",
        ".apk": "application/vnd.android.package-archive",
    }

    def end_headers(self):
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        super().end_headers()

    def log_message(self, fmt, *args):
        sys.stderr.write("  %s\n" % (fmt % args))


def local_ip() -> str:
    """猜一个局域网 IP，方便真机直接连（不用 adb reverse）"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def main() -> int:
    parser = argparse.ArgumentParser(description="本地假更新服务器")
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()

    if not DIST_DIR.exists() or not (DIST_DIR / "manifest.json").exists():
        print(f"[失败] 找不到 {DIST_DIR / 'manifest.json'}")
        print("       先生成清单：python tools/update/make_manifest.py --note \"测试更新\"")
        return 1

    handler = functools.partial(Handler, directory=str(DIST_DIR))

    print("=" * 64)
    print("  本地更新服务器已启动")
    print("=" * 64)
    print(f"\n  服务目录：{os.path.relpath(DIST_DIR, ROOT)}")
    print(f"\n  清单地址（挑一个填进 UpdateConfig.MANIFEST_URLS）：")
    print(f"    模拟器    http://10.0.2.2:{args.port}/manifest.json")
    print(f"    真机+adb  http://127.0.0.1:{args.port}/manifest.json   (先 adb reverse tcp:{args.port} tcp:{args.port})")
    print(f"    局域网    http://{local_ip()}:{args.port}/manifest.json")
    print(f"\n  浏览器里先自己打开看一眼，确认能返回 JSON。")
    print(f"  Ctrl+C 停止。\n")

    try:
        http.server.ThreadingHTTPServer(("0.0.0.0", args.port), handler).serve_forever()
    except KeyboardInterrupt:
        print("\n已停止。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
