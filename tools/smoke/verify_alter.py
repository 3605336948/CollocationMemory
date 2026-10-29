import xml.etree.ElementTree as ET, subprocess, re, time, sys

adb = r"D:/Android/Sdk/platform-tools/adb.exe"
UI = r"D:/AndroidProjects/CollocationMemory/tools/smoke/ui.xml"


def dump():
    subprocess.run([adb, "shell", "uiautomator", "dump", "/sdcard/ui.xml"],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run([adb, "pull", "/sdcard/ui.xml", UI],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def tap(x, y):
    subprocess.run([adb, "shell", "input", "tap", str(x), str(y)])


def parse():
    t = ET.parse(UI).getroot()
    cur = None
    buttons = []
    change = None
    for n in t.iter("node"):
        txt = (n.get("text") or "").strip()
        b = n.get("bounds")
        if not b:
            continue
        m = re.findall(r"\d+", b)
        if len(m) < 4:
            continue
        x0, y0, x1, y1 = int(m[0]), int(m[1]), int(m[2]), int(m[3])
        if txt and txt.isascii() and (" " in txt or txt[0].islower()) and len(txt) < 40:
            cur = txt
        if txt in ("记住了", "再确认一次", "再考一次", "下一个"):
            buttons.append((txt, (x0 + x1) // 2, (y0 + y1) // 2))
        # 正确项：文本精确等于释义，且位于选项行（排除顶部释义提示）
        if txt == "v. 改变，改动，变更" and y0 > 1500:
            change = ((x0 + x1) // 2, (y0 + y1) // 2)
    return cur, buttons, change


done = False
log = []
for step in range(20):
    dump()
    cur, buttons, change = parse()
    labels = [b[0] for b in buttons]
    log.append(f"step{step} cur={cur} buttons={labels} change={change}")
    if not cur:
        log.append("  无当前英文词，可能已离开卡片页")
        break
    if any(b[0] == "再考一次" for b in buttons):
        b = [x for x in buttons if x[0] == "再考一次"][0]
        tap(b[1], b[2]); time.sleep(1.3); continue
    if change:
        tap(change[0], change[1]); time.sleep(1.3); continue
    if any(b[0] == "再确认一次" for b in buttons):
        b = [x for x in buttons if x[0] == "再确认一次"][0]
        tap(b[1], b[2]); time.sleep(1.3); continue
    if any(b[0] == "记住了" for b in buttons):
        b = [x for x in buttons if x[0] == "记住了"][0]
        tap(b[1], b[2]); done = True; time.sleep(1.3); break

print("\n".join(log))
print("RESULT done=", done)
