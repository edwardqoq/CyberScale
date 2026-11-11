// 用本机 Chrome 无头模式 + DevTools 协议逐帧渲染演示片
// 用法：node capture.mjs <stage.html> <输出目录> [fps] [时长秒]
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

// 可用环境变量覆盖浏览器路径：
//   macOS   CHROME_PATH="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
//   Linux   CHROME_PATH=/usr/bin/google-chrome
//   Windows 默认读 Chrome 安装目录，也可指向 msedge.exe
const CHROME = process.env.CHROME_PATH
  || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const htmlPath = path.resolve(process.argv[2]);
const outDir = path.resolve(process.argv[3]);
const fps = Number(process.argv[4] || 30);
const dur = Number(process.argv[5] || 11.8);
const W = 1920, H = 1080;
const total = Math.round(dur * fps);

fs.mkdirSync(outDir, { recursive: true });
for (const f of fs.readdirSync(outDir)) {
  if (f.endsWith('.png') || f.endsWith('.jpg')) fs.unlinkSync(path.join(outDir, f));
}

const profile = path.join(process.env.TEMP || '/tmp', 'cs-chrome-' + Date.now());
const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--hide-scrollbars',
  '--force-device-scale-factor=1', '--force-color-profile=srgb',
  '--disable-lcd-text', '--font-render-hinting=none',
  `--window-size=${W},${H}`, '--remote-debugging-port=0',
  `--user-data-dir=${profile}`,
  '--no-first-run', '--no-default-browser-check',
  '--allow-file-access-from-files', '--mute-audio', 'about:blank'
], { stdio: ['ignore', 'pipe', 'pipe'] });

let stderrBuf = '';
chrome.stderr.on('data', d => { stderrBuf += d.toString(); });

function waitForDevTools() {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Chrome DevTools 启动超时\n' + stderrBuf)), 25000);
    const onData = () => {
      const m = stderrBuf.match(/DevTools listening on (ws:\/\/[^\s]+)/);
      if (m) { clearTimeout(timer); chrome.stderr.off('data', onData); resolve(m[1]); }
    };
    chrome.stderr.on('data', onData);
    onData();
    chrome.on('exit', c => { clearTimeout(timer); reject(new Error('Chrome 提前退出 code=' + c)); });
  });
}

const browserWs = await waitForDevTools();
const port = new URL(browserWs).port;

let pageWs = null;
for (let i = 0; i < 60; i++) {
  try {
    const list = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
    const pg = list.find(t => t.type === 'page');
    if (pg?.webSocketDebuggerUrl) { pageWs = pg.webSocketDebuggerUrl; break; }
  } catch { /* 端口尚未就绪 */ }
  await new Promise(r => setTimeout(r, 120));
}
if (!pageWs) throw new Error('未找到可用的 page target');

const ws = new WebSocket(pageWs);
await new Promise((res, rej) => { ws.onopen = res; ws.onerror = e => rej(new Error('WS 连接失败')); });

let seq = 0;
const pending = new Map();
const listeners = new Map();
ws.onmessage = ev => {
  const m = JSON.parse(ev.data);
  if (m.id && pending.has(m.id)) {
    const { res, rej } = pending.get(m.id); pending.delete(m.id);
    m.error ? rej(new Error(m.method + ' ' + JSON.stringify(m.error))) : res(m.result);
  } else if (m.method) {
    (listeners.get(m.method) || []).forEach(f => f(m.params));
  }
};
const send = (method, params = {}) => new Promise((res, rej) => {
  const id = ++seq; pending.set(id, { res, rej });
  ws.send(JSON.stringify({ id, method, params }));
});
const once = method => new Promise(res => {
  const fn = p => { listeners.set(method, (listeners.get(method) || []).filter(x => x !== fn)); res(p); };
  listeners.set(method, [...(listeners.get(method) || []), fn]);
});

await send('Page.enable');
await send('Emulation.setDeviceMetricsOverride', { width: W, height: H, deviceScaleFactor: 1, mobile: false });

const loaded = once('Page.loadEventFired');
await send('Page.navigate', { url: pathToFileURL(htmlPath).href });
await loaded;
await new Promise(r => setTimeout(r, 400));   // 等字体与首帧稳定

const t0 = Date.now();
for (let i = 0; i < total; i++) {
  const t = i / fps;
  await send('Runtime.evaluate', { expression: `window.renderFrame(${t.toFixed(4)})` });
  const { data } = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  fs.writeFileSync(path.join(outDir, `f_${String(i).padStart(4, '0')}.png`), Buffer.from(data, 'base64'));
  if (i % 40 === 0 || i === total - 1) {
    const el = ((Date.now() - t0) / 1000).toFixed(1);
    process.stdout.write(`\r  帧 ${i + 1}/${total} · t=${t.toFixed(2)}s · ${el}s`);
  }
}
process.stdout.write('\n');

// 额外导出海报帧（全景完成）
const posters = [[4.95, 'poster-wide'], [3.95, 'poster-measure']];
for (const [t, name] of posters) {
  await send('Runtime.evaluate', { expression: `window.renderFrame(${t})` });
  const { data } = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  fs.writeFileSync(path.join(outDir, `${name}.png`), Buffer.from(data, 'base64'));
}

ws.close();
chrome.kill();
console.log(`完成：${total} 帧 → ${outDir}`);
