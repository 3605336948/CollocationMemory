// 生成 v12 词库：固定搭配8 全量入蓝哥本（stdout 输出，由 shell 重定向写文件）
const fs = require('fs');
const P = 'tools/assets/app_library.json';
const lib = JSON.parse(fs.readFileSync(P, 'utf8'));
const xml = fs.readFileSync('doc8.xml', 'utf8');

const paras = [];
for (const pm of xml.matchAll(/<w:p[ >][\s\S]*?<\/w:p>/g)) {
  const t = [...pm[0].matchAll(/<w:t[^>]*>([\s\S]*?)<\/w:t>/g)].map(m => m[1]).join('');
  const line = t.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').trim();
  if (line) paras.push(line);
}

const entries = [];
for (const p of paras) {
  const body = p.match(/^\d+[.、]\s*(.+)$/)[1];
  const mm = body.match(/^([A-Za-z][A-Za-z0-9 ….'’\-]*?)\s+([\u4e00-\u9fff][\s\S]*)$/);
  if (!mm) { console.error('拆分失败: ' + p); process.exit(1); }
  entries.push({ english: mm[1].trim(), chinese: mm[2].trim(), example: '', tags: ['固定搭配8'], bookName: '蓝哥固定搭配单词本' });
}

// 蓝哥本内查重（normalize：小写+压空格）
const norm = s => s.toLowerCase().replace(/\s+/g, ' ').trim();
const inLG = new Set(lib.items.filter(x => x.bookName === '蓝哥固定搭配单词本').map(x => norm(x.english)));
const skip = entries.filter(e => inLG.has(norm(e.english)));
const add = entries.filter(e => !inLG.has(norm(e.english)));
if (skip.length) { console.error('跳过重复 ' + skip.length + ' 条: ' + skip.map(e => e.english).join(', ')); }

lib.items.push(...add);
lib.version = 12;
lib.notes = '内置词库 v12：固定搭配8 全量入蓝哥本（+' + add.length + '词，蓝哥本 ' + inLG.size + '→' + (inLG.size + add.length) + '）';

process.stdout.write(JSON.stringify(lib, null, 2) + '\n');
console.error('统计: 总 ' + lib.items.length + ' 条；蓝哥本 ' + lib.items.filter(x => x.bookName === '蓝哥固定搭配单词本').length + ' 条；version ' + lib.version);
