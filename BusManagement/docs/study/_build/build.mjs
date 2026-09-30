// Build a BusManagement study lesson: Markdown source -> self-contained HTML page.
// Usage: node build.mjs <input.md> <output.html> "<page title>" "<lesson chip>"
import { readFileSync, writeFileSync } from 'node:fs';
import { marked } from 'marked';

const [, , input, output, pageTitle, chip] = process.argv;
let md = readFileSync(input, 'utf8');

const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const slug = (s) => s
  .normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D')
  .replace(/<[^>]+>/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

// Pull H1 + the intro blockquote that follows it into the page header.
let h1 = '';
md = md.replace(/^# (.+)\n/, (_, t) => { h1 = t.trim(); return ''; });

const toc = [];
const usedIds = new Set();
const renderer = new marked.Renderer();

renderer.heading = (text, level, raw) => {
  let id = slug(raw) || 'muc';
  while (usedIds.has(id)) id += '-x';
  usedIds.add(id);
  if (level === 2) toc.push({ id, text: raw.replace(/`/g, '') });
  return `<h${level} id="${id}">${text}</h${level}>\n`;
};
renderer.code = (code, lang) => {
  if (lang === 'mermaid') {
    return `<figure class="diagram"><pre class="mermaid">${esc(code)}</pre></figure>\n`;
  }
  const cls = lang ? ` class="language-${lang}"` : '';
  return `<div class="code-wrap"><pre class="code"><code${cls}>${esc(code)}</code></pre></div>\n`;
};
renderer.table = (header, body) =>
  `<div class="table-wrap"><table><thead>${header}</thead><tbody>${body}</tbody></table></div>\n`;
renderer.blockquote = (quote) => `<aside class="callout">${quote}</aside>\n`;

marked.use({ renderer, gfm: true });
let html = marked.parse(md);

// Turn [CODE] / [SPRING] / [SUY LUẬN ...] into badges, but never inside <pre> or <code>.
const parts = html.split(/(<pre[\s\S]*?<\/pre>|<code[\s\S]*?<\/code>)/);
html = parts.map((p, i) => {
  if (i % 2 === 1) return p;
  return p
    .replace(/\[CODE\]/g, '<span class="tag tag-code">CODE</span>')
    .replace(/\[SPRING\]/g, '<span class="tag tag-spring">SPRING</span>')
    .replace(/\[SUY LUẬN([^\]]*)\]/g, (_, rest) => `<span class="tag tag-guess">SUY LUẬN${rest}</span>`);
}).join('');

const tocHtml = toc.map((t) => `<li><a href="#${t.id}">${esc(t.text)}</a></li>`).join('\n');

const page = `<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>${esc(pageTitle)}</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:ital,wght@0,400;0,500;0,600;0,700;1,400&family=JetBrains+Mono:wght@400;600&display=swap">
<style>
:root {
  color-scheme: light;
  --bg: #F4F6F3;
  --surface: #FFFFFF;
  --ink: #17232D;
  --muted: #58646E;
  --line: #D8DED9;
  --accent: #1D6A51;
  --accent-soft: #E2EFE8;
  --code-bg: #EDF1EE;
  --code-ink: #1F2B33;
  --warn: #8F5C0E;
  --warn-soft: #FAF0DD;
  --spring: #2D5AA3;
  --spring-soft: #E4ECF8;
  --hl-kw: #8A2E6B;
  --hl-str: #1D6A51;
  --hl-com: #6F7B74;
  --hl-num: #9A4B10;
  --hl-type: #2D5AA3;
  --font-body: "Be Vietnam Pro", "Segoe UI", system-ui, sans-serif;
  --font-mono: "JetBrains Mono", "Cascadia Mono", Consolas, monospace;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    color-scheme: dark;
    --bg: #0F1518; --surface: #151D21; --ink: #E2E8E4; --muted: #9AA69F; --line: #263239;
    --accent: #5BC49B; --accent-soft: #15302A; --code-bg: #1A2429; --code-ink: #DCE4DF;
    --warn: #E2B25E; --warn-soft: #2D2415; --spring: #8DB2F2; --spring-soft: #1A2537;
    --hl-kw: #E48FC6; --hl-str: #7FD1A9; --hl-com: #7C8A83; --hl-num: #F0A76A; --hl-type: #8DB2F2;
  }
}
:root[data-theme="dark"] {
  color-scheme: dark;
  --bg: #0F1518; --surface: #151D21; --ink: #E2E8E4; --muted: #9AA69F; --line: #263239;
  --accent: #5BC49B; --accent-soft: #15302A; --code-bg: #1A2429; --code-ink: #DCE4DF;
  --warn: #E2B25E; --warn-soft: #2D2415; --spring: #8DB2F2; --spring-soft: #1A2537;
  --hl-kw: #E48FC6; --hl-str: #7FD1A9; --hl-com: #7C8A83; --hl-num: #F0A76A; --hl-type: #8DB2F2;
}
* { box-sizing: border-box; }
body {
  margin: 0; background: var(--bg); color: var(--ink);
  font-family: var(--font-body); font-size: 16px; line-height: 1.65;
  padding-inline: 16px;
}
a { color: var(--accent); }
a:focus-visible, summary:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.shell {
  max-width: 1180px; margin: 0 auto; padding-block: 28px 64px;
  display: grid; grid-template-columns: 1fr; gap: 32px;
}
@media (min-width: 1000px) {
  .shell { grid-template-columns: 250px minmax(0, 1fr); }
  .toc { position: sticky; top: calc(env(safe-area-inset-top, 0px) + 20px); align-self: start;
         max-height: calc(100vh - 40px); overflow-y: auto; }
  .toc details { display: contents; }
  .toc summary { display: none; }
}
.toc { font-size: 14px; }
.toc summary { cursor: pointer; font-weight: 600; padding-block: 6px; }
.toc .label { font-size: 11px; letter-spacing: .08em; text-transform: uppercase; color: var(--muted); margin: 0 0 8px; }
.toc ol { list-style: none; margin: 0; padding: 0; display: grid; gap: 2px; border-left: 2px solid var(--line); }
.toc a { display: block; padding: 4px 10px; color: var(--muted); text-decoration: none; margin-left: -2px; border-left: 2px solid transparent; }
.toc a:hover { color: var(--ink); border-left-color: var(--accent); }
main { min-width: 0; max-width: 820px; }
header.lesson { padding-bottom: 20px; margin-bottom: 8px; border-bottom: 1px solid var(--line); }
.chip { display: inline-block; font-family: var(--font-mono); font-size: 12px; letter-spacing: .04em;
        color: var(--accent); background: var(--accent-soft); padding: 3px 10px; border-radius: 999px; }
h1 { font-size: clamp(26px, 4vw, 36px); line-height: 1.2; margin: 12px 0 0; text-wrap: balance; font-weight: 700; }
h2 { font-size: 24px; line-height: 1.3; margin: 48px 0 12px; padding-top: 12px; border-top: 1px solid var(--line); text-wrap: balance; }
h3 { font-size: 18px; margin: 30px 0 8px; text-wrap: balance; }
h4 { font-size: 16px; margin: 22px 0 6px; }
p, li { max-width: 72ch; }
ul, ol { padding-left: 22px; }
li + li { margin-top: 4px; }
hr { border: 0; border-top: 1px solid var(--line); margin: 32px 0; }
code { font-family: var(--font-mono); font-size: .88em; background: var(--code-bg); color: var(--code-ink);
       padding: 1px 5px; border-radius: 4px; overflow-wrap: anywhere; }
.code-wrap { overflow-x: auto; margin: 14px 0; border: 1px solid var(--line); border-radius: 8px; background: var(--code-bg); }
pre.code { margin: 0; padding: 14px 16px; font-size: 13.5px; line-height: 1.55; }
pre.code code { background: none; padding: 0; font-size: inherit; overflow-wrap: normal; white-space: pre; }
.hljs-keyword, .hljs-meta, .hljs-selector-tag, .hljs-built_in { color: var(--hl-kw); }
.hljs-string, .hljs-attr, .hljs-template-variable { color: var(--hl-str); }
.hljs-comment, .hljs-quote { color: var(--hl-com); font-style: italic; }
.hljs-number, .hljs-literal { color: var(--hl-num); }
.hljs-title, .hljs-type, .hljs-name, .hljs-tag { color: var(--hl-type); }
.table-wrap { overflow-x: auto; margin: 14px 0; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); }
table { border-collapse: collapse; width: 100%; font-size: 14.5px; }
th, td { text-align: left; vertical-align: top; padding: 9px 12px; border-bottom: 1px solid var(--line); }
th { font-weight: 600; background: var(--accent-soft); white-space: nowrap; }
tbody tr:last-child td { border-bottom: 0; }
td { font-variant-numeric: tabular-nums; }
.callout { margin: 16px 0; padding: 12px 16px; border-left: 3px solid var(--accent); background: var(--accent-soft); border-radius: 0 8px 8px 0; }
.callout p { margin: 6px 0; }
.diagram { margin: 18px 0; padding: 16px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); overflow-x: auto; }
.diagram pre.mermaid { margin: 0; background: none; text-align: center; font-family: var(--font-body); }
.tag { display: inline-block; font-family: var(--font-mono); font-size: 11px; font-weight: 600; letter-spacing: .04em;
       padding: 1px 6px; border-radius: 4px; vertical-align: 1px; white-space: nowrap; }
.tag-code { color: var(--accent); background: var(--accent-soft); }
.tag-spring { color: var(--spring); background: var(--spring-soft); }
.tag-guess { color: var(--warn); background: var(--warn-soft); }
details { margin: 20px 0; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); padding: 4px 16px; }
details > summary { cursor: pointer; font-weight: 600; padding-block: 10px; color: var(--accent); }
details[open] > summary { border-bottom: 1px solid var(--line); margin-bottom: 8px; }
strong { font-weight: 600; }
@media (prefers-reduced-motion: reduce) { html { scroll-behavior: auto; } }
@media (prefers-reduced-motion: no-preference) { html { scroll-behavior: smooth; } }
</style>

<div class="shell">
  <nav class="toc" aria-label="Mục lục">
    <details>
      <summary>Mục lục</summary>
      <p class="label">Mục lục</p>
      <ol>
${tocHtml}
      </ol>
    </details>
  </nav>
  <main>
    <header class="lesson">
      <span class="chip">${esc(chip)}</span>
      <h1>${esc(h1)}</h1>
    </header>
${html}
  </main>
</div>

<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
<script>
  try {
    document.querySelectorAll('pre.code code').forEach(function (el) {
      if (window.hljs) window.hljs.highlightElement(el);
    });
  } catch (e) { /* tô màu là tiện ích, lỗi thì để code thường */ }

  // Sơ đồ Mermaid: artifact trên claude.ai tự vẽ. Khi mở file cục bộ (file://)
  // thì tự nạp thư viện Mermaid từ CDN để vẽ.
  (function () {
    if (location.protocol !== 'file:') return;
    var s = document.createElement('script');
    s.src = 'https://cdn.jsdelivr.net/npm/mermaid@10.9.1/dist/mermaid.min.js';
    s.onload = function () {
      var dark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
      window.mermaid.initialize({ startOnLoad: false, theme: dark ? 'dark' : 'default', securityLevel: 'loose' });
      window.mermaid.run({ querySelector: 'pre.mermaid' });
    };
    document.head.appendChild(s);
  })();
</script>
`;

writeFileSync(output, page);
console.log(`wrote ${output}: ${toc.length} sections, ${page.length} chars`);
