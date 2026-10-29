# -*- coding: utf-8 -*-
"""把 固定搭配6.docx 的 75 条并入蓝哥本，词库 v8 -> v9，生成核对表。"""
import json
import re
import zipfile

DOCX = r"C:/Users/王佳豪/Downloads/固定搭配6.docx"
LIB = "tools/assets/app_library.json"
PUSH = "tools/update/gitee-push/library/app_library.json"
CHECKLIST = "固定搭配6-导入核对表.md"

# ---------- 1. 解析 docx ----------
with zipfile.ZipFile(DOCX) as z:
    xml = z.read("word/document.xml").decode("utf-8")

paras = []
for pm in re.finditer(r"<w:p[ >].*?</w:p>", xml, re.S):
    texts = re.findall(r"<w:t[^>]*>(.*?)</w:t>", pm.group(0), re.S)
    line = "".join(texts)
    line = line.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", '"')
    if line.strip():
        paras.append(line.strip())

def unescape(s):
    return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", '"')

CJK = re.compile(r"[\u4e00-\u9fff]")
entries = []
for p in paras:
    p = unescape(p)
    # 去掉开头序号 "12. "
    body = re.sub(r"^\d+\.\s*", "", p).strip()
    m = CJK.search(body)
    if not m:
        print("!! 找不到中文，跳过:", repr(p))
        continue
    en = body[: m.start()].strip().rstrip("；;")
    cn = body[m.start():].strip()
    if en and cn:
        entries.append({"english": en, "chinese": cn})

print(f"解析出 {len(entries)} 条")

# ---------- 2. 与现有蓝哥本去重 ----------
with open(LIB, encoding="utf-8") as f:
    lib = json.load(f)

book = "蓝哥固定搭配单词本"
existing = {
    it["english"].strip().lower(): it
    for it in lib["items"] if it.get("bookName") == book
}

fresh, dup_same, dup_diff = [], [], []
for e in entries:
    key = e["english"].strip().lower()
    old = existing.get(key)
    if old is None:
        fresh.append(e)
    elif old["chinese"] == e["chinese"]:
        dup_same.append(e)
    else:
        dup_diff.append((old["chinese"], e["chinese"], e["english"]))

print(f"全新 {len(fresh)} 条 / 完全重复 {len(dup_same)} 条 / 释义不同 {len(dup_diff)} 条")
for old_c, new_c, en in dup_diff:
    print(f"  [释义不同] {en}: 现有「{old_c}」 vs 新「{new_c}」")

# ---------- 3. 追加 & 升版本 ----------
for e in fresh:
    lib["items"].append({
        "english": e["english"],
        "chinese": e["chinese"],
        "example": "",
        "tags": ["固定搭配6"],
        "bookName": book,
    })
lib["version"] = 9
lib["notes"] = "内置词库 v9：蓝哥本新增固定搭配6"

with open(LIB, "w", encoding="utf-8") as f:
    json.dump(lib, f, ensure_ascii=False, indent=2)
print(f"已写 {LIB}：v9，总条数 {len(lib['items'])}")

# 同步到热更新仓库
with open(LIB, encoding="utf-8") as f:
    data = f.read()
with open(PUSH, "w", encoding="utf-8") as f:
    f.write(data)
print(f"已同步 {PUSH}")

# ---------- 4. 核对表 ----------
with open(CHECKLIST, "w", encoding="utf-8") as f:
    f.write("# 固定搭配6 导入核对表\n\n")
    f.write(f"来源：固定搭配6.docx（解析 {len(entries)} 条）\n")
    f.write(f"全新加入：**{len(fresh)} 条**（词库 v8 → v9）\n")
    f.write(f"完全重复跳过：{len(dup_same)} 条；释义不同未覆盖：{len(dup_diff)} 条\n\n")
    if dup_diff:
        f.write("## 释义不同的词条（现有释义保留，未覆盖）\n\n| 英文 | 现有释义 | 新文档释义 |\n|---|---|---|\n")
        for old_c, new_c, en in dup_diff:
            f.write(f"| {en} | {old_c} | {new_c} |\n")
        f.write("\n")
    f.write("## 新增词条明细\n\n| # | 英文 | 中文 |\n|---|---|---|\n")
    for i, e in enumerate(fresh, 1):
        f.write(f"| {i} | {e['english']} | {e['chinese']} |\n")
print(f"已写 {CHECKLIST}")
