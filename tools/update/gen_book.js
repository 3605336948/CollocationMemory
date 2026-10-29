// 通用词库导入：docx → 蓝哥固定搭配单词本
// 用法：node gen_book.js <document.xml> <标签> <新version>
// 例：  node gen_book.js doc9.xml 固定搭配9 13
// stdout 输出新词库 JSON（由 shell 重定向写文件），stderr 输出统计
const fs = require('fs');
const [xmlPath, tag, versionArg] = process.argv.slice(2);
if (!xmlPath || !tag || !versionArg) {
  console.error('用法: node gen_book.js <document.xml> <标签> <新version>');
  process.exit(1);
}
const P = 'tools/assets/app_library.json';
const lib = JSON.parse(fs.readFileSync(P, 'utf8'));
const xml = fs.readFileSync(xmlPath, 'utf8');

const paras = [];
for (const pm of xml.matchAll(/<w:p[ >][\s\S]*?<\/w:p>/g)) {
  const t = [...pm[0].matchAll(/<w:t[^>]*>([\s\S]*?)<\/w:t>/g)].map(m => m[1]).join('');
  const line = t.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').trim();
  if (line) paras.push(line);
}

const entries = [];
for (const p of paras) {
  const body = p.match(/^\d+[.、]\s*(.+)$/)[1];
  // 以第一个汉字为分界：左边是英文，右边是中文释义
  // （不能要求释义以汉字开头——「A和B之间的区别」这种就是英文字母开头）
  const mm = body.match(/^(.*?)\s*([\u4e00-\u9fff][\s\S]*)$/);
  if (!mm) { console.error('拆分失败: ' + p); process.exit(1); }
  entries.push({ english: mm[1].trim(), chinese: mm[2].trim(), example: '', tags: [tag], bookName: '蓝哥固定搭配单词本' });
}

// 蓝哥本内查重（normalize：小写+压空格）
const norm = s => s.toLowerCase().replace(/\s+/g, ' ').trim();
const lg = lib.items.filter(x => x.bookName === '蓝哥固定搭配单词本');
const inLG = new Set(lg.map(x => norm(x.english)));
const skip = entries.filter(e => inLG.has(norm(e.english)));
const add = entries.filter(e => !inLG.has(norm(e.english)));
if (skip.length) console.error('跳过蓝哥本已有 ' + skip.length + ' 条: ' + skip.map(e => e.english).join(', '));

lib.items.push(...add);
lib.version = parseInt(versionArg, 10);
lib.notes = '内置词库 v' + versionArg + '：' + tag + ' 全量入蓝哥本（+' + add.length + '词，蓝哥本 ' + inLG.size + '→' + (inLG.size + add.length) + '）';

process.stdout.write(JSON.stringify(lib, null, 2) + '\n');
console.error('统计: 总 ' + lib.items.length + ' 条；蓝哥本 ' + lib.items.filter(x => x.bookName === '蓝哥固定搭配单词本').length + ' 条；version ' + lib.version);
