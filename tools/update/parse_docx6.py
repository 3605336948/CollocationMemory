# -*- coding: utf-8 -*-
"""解析 固定搭配6.docx（标准库，不依赖 python-docx），输出词条预览。"""
import zipfile
import re
import sys

PATH = r"C:/Users/王佳豪/Downloads/固定搭配6.docx"

with zipfile.ZipFile(PATH) as z:
    xml = z.read("word/document.xml").decode("utf-8")

# 按段落切分，取每段内的 w:t 文本
paras = []
for pm in re.finditer(r"<w:p[ >].*?</w:p>", xml, re.S):
    texts = re.findall(r"<w:t[^>]*>(.*?)</w:t>", pm.group(0), re.S)
    line = "".join(texts)
    line = line.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", '"')
    if line.strip():
        paras.append(line.strip())

print("非空段落数:", len(paras))
print("--- 前 30 行 ---")
for p in paras[:30]:
    print(repr(p))
print("--- 后 10 行 ---")
for p in paras[-10:]:
    print(repr(p))
