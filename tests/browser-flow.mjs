// Optional real-browser checks: Node.js 22+, Chromium/Chrome and compiled out/.
// No npm packages are required. Each run starts its own Java server and browser.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const profile = await mkdtemp(path.join(tmpdir(), 'social-browser-'));
const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
const browser = process.env.BROWSER_PATH || (process.platform === 'linux' ? 'chromium' : 'google-chrome');
let serverLog = '', browserLog = '', ws;
let checks = 0, sequence = 0;
const pending = new Map(), errors = [], requests = [], paused = [];
const networkDone = new Set();
const children = [];

function child(command, args, log) {
  const proc = spawn(command, args, { cwd: root, stdio: ['ignore', 'pipe', 'pipe'] });
  children.push(proc);
  proc.stdout.on('data', chunk => log(chunk.toString()));
  proc.stderr.on('data', chunk => log(chunk.toString()));
  proc.on('error', err => log(err.message));
  return proc;
}
async function waitFor(probe, label, timeout = 10000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    const result = await probe();
    if (result) return result;
    await new Promise(resolve => setTimeout(resolve, 40));
  }
  throw new Error('Timed out: ' + label);
}
function check(condition, label) {
  assert(condition, label);
  checks++;
  console.log('PASS', label);
}
function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++sequence;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error('CDP timeout: ' + method)); }, 10000);
    pending.set(id, { resolve, reject, timer });
    ws.send(JSON.stringify({ id, method, params }));
  });
}
async function evaluate(expression) {
  const response = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  assert(!response.exceptionDetails, JSON.stringify(response.exceptionDetails));
  return response.result.value;
}
const text = id => evaluate(`document.getElementById(${JSON.stringify(id)}).textContent`);
const tags = selector => evaluate(`Array.from(document.querySelectorAll(${JSON.stringify(selector)}), el=>el.textContent)`);
async function mouse(type, x, y, extra = {}) {
  await send('Input.dispatchMouseEvent', { type, x, y, ...extra });
}
async function click(selector) {
  const point = await evaluate(`(() => {const el=document.querySelector(${JSON.stringify(selector)}); el.scrollIntoView({block:'nearest'}); const r=el.getBoundingClientRect(); return {x:r.x+r.width/2,y:r.y+r.height/2};})()`);
  await mouse('mousePressed', point.x, point.y, { button: 'left', clickCount: 1 });
  await mouse('mouseReleased', point.x, point.y, { button: 'left', clickCount: 1 });
}
async function query(a, b, enter = false) {
  await evaluate(`document.getElementById('pathA').value=${JSON.stringify(a)}; document.getElementById('pathB').value=${JSON.stringify(b)};`);
  if (enter) {
    await click('#pathB');
    await send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 });
    await send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 });
  } else await click('#btnPath');
}
async function escape() {
  await evaluate('document.activeElement.blur()');
  await send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 });
}
async function intercept(route) {
  paused.length = 0;
  await send('Fetch.enable', { patterns: [{ urlPattern: base + route + '*', requestStage: 'Request' }] });
}
async function release(item, fail = false) {
  await send(fail ? 'Fetch.failRequest' : 'Fetch.continueRequest', fail ? { requestId: item.requestId, errorReason: 'Failed' } : { requestId: item.requestId });
}
async function settled(item) {
  await waitFor(() => networkDone.has(item.networkId), 'held network request completed');
  await evaluate('new Promise(resolve=>setTimeout(resolve,0))');
}
let base;
try {
  const server = child(java, ['-cp', 'out', 'WebMain', 'data/sample_edges.csv', '0'], chunk => serverLog += chunk);
  const port = await waitFor(() => {
    if (server.exitCode !== null) throw new Error(serverLog);
    return /localhost:(\d+)\//.exec(serverLog)?.[1];
  }, 'Java startup');
  base = 'http://127.0.0.1:' + port;
  const api = async (route) => (await fetch(base + route)).json();
  const initial = await api('/api/graph');
  check(initial.nodes.length === 11 && initial.edges.length === 13, 'Java sample graph: 11 nodes, 13 edges');
  const args = ['--headless', '--disable-dev-shm-usage', '--no-first-run', '--remote-debugging-port=0', '--user-data-dir=' + profile, 'about:blank'];
  // Container kernels may not support Chrome's additional sandbox. Only Linux
  // test processes use this flag; the browser visits this local test server.
  if (process.platform === 'linux') args.unshift('--no-sandbox');
  const chrome = child(browser, args, chunk => browserLog += chunk);
  const debugPort = await waitFor(async () => {
    if (chrome.exitCode !== null) throw new Error(browserLog);
    try { return (await readFile(path.join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]; }
    catch (err) { if (err.code === 'ENOENT') return false; throw err; }
  }, 'Chromium startup');
  const targets = await (await fetch('http://127.0.0.1:' + debugPort + '/json/list')).json();
  ws = new WebSocket(targets.find(t => t.type === 'page').webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
  ws.onmessage = event => {
    const message = JSON.parse(event.data);
    if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails);
    if (message.method === 'Network.requestWillBeSent') requests.push(message.params.request);
    if (message.method === 'Fetch.requestPaused') paused.push(message.params);
    if (message.method === 'Network.loadingFinished' || message.method === 'Network.loadingFailed') networkDone.add(message.params.requestId);
    const waiter = pending.get(message.id);
    if (waiter) {
      clearTimeout(waiter.timer); pending.delete(message.id);
      message.error ? waiter.reject(new Error(JSON.stringify(message.error))) : waiter.resolve(message.result);
    }
  };
  await send('Runtime.enable'); await send('Page.enable'); await send('Network.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: base + '/' });
  await waitFor(() => evaluate(`document.querySelectorAll('#nodeLayer circle').length===11 && document.querySelectorAll('#statGrid .n').length===3`), 'graph rendering');
  check(await evaluate(`document.querySelectorAll('#edgeLayer line').length===13`), 'SVG graph matches Java edges');
  check(JSON.stringify(await tags('#statGrid .n')) === JSON.stringify(['3','5','3']), 'degree rank groups: 3 / 5 / 3');
  check(await evaluate(`getComputedStyle(document.getElementById('connOverlay')).display==='none'`), 'connected dashboard has no error overlay');
  await query('photoshop', 'jquery');
  await waitFor(async () => (await tags('#pathResult .tag')).length === 3, 'shortest path');
  check(JSON.stringify(await tags('#pathResult .tag')) === JSON.stringify(['photoshop','css','jquery']), 'fewest intermediaries path from Java');
  check((await text('pathResult')).includes('1 人'), 'path shows one intermediary');
  await waitFor(async () => (await tags('#circleBody .tag')).length === 1, 'direct circle');
  check(JSON.stringify(await tags('#circleBody .tag')) === JSON.stringify(['css']), 'photoshop direct circle: css');
  await waitFor(async () => (await tags('#nearbyBody .copper')).length === 2, 'two-hop extension');
  check(JSON.stringify((await tags('#nearbyBody .copper')).sort()) === JSON.stringify(['html','jquery']), 'two-hop extension excludes self and direct contacts');
  await query('jquery', 'jquery', true);
  await waitFor(async () => (await tags('#pathResult .tag')).length === 1, 'same-node path via Enter');
  check((await text('pathResult')).includes('0 人'), 'Enter submits same-node path with zero intermediaries');
  await query('__missing__', 'jquery');
  await waitFor(async () => (await text('pathResult')).includes('名字不存在'), 'invalid name');
  check(await evaluate(`document.querySelectorAll('.path-highlight').length===0`), 'invalid path clears old highlights');
  await query('', 'jquery');
  check((await text('pathResult')).includes('请填写'), 'empty input gets useful feedback');

  // Deliberately hold real network requests to exercise ordering and Escape.
  await intercept('/api/path');
  await query('photoshop','jquery');
  await waitFor(() => paused.length === 1, 'first held path');
  await query('jquery','jquery');
  await waitFor(() => paused.length === 2, 'second held path');
  await release(paused[1]);
  await waitFor(async () => (await tags('#pathResult .tag')).length === 1, 'latest path');
  await release(paused[0]);
  await settled(paused[0]);
  check(JSON.stringify(await tags('#pathResult .tag')) === JSON.stringify(['jquery']), 'older path response cannot overwrite newer query');
  paused.length = 0;
  await query('photoshop','jquery');
  await waitFor(() => paused.length === 1, 'path before Escape');
  await escape();
  await release(paused[0]);
  await settled(paused[0]);
  check((await text('pathResult')).trim() === '' && (await text('statusbar')).includes('选中 无'), 'Escape invalidates pending path and selection');
  paused.length=0;
  await query('photoshop','jquery');
  await waitFor(() => paused.length===1, 'path before edited input');
  await evaluate(`document.getElementById('pathA').value='java';document.getElementById('pathA').dispatchEvent(new Event('input',{bubbles:true}));`);
  await release(paused[0]);
  await settled(paused[0]);
  check((await text('pathResult')).includes('输入已变更') && (await tags('#pathResult .tag')).length===0, 'editing input invalidates an outstanding path');
  await send('Fetch.disable');

  await click('[data-view="geo"]');
  check((await text('statusbar')).includes('模拟位置视图'), 'simulation position view is explicit');
  const coordinates = await evaluate(`Array.from(document.querySelectorAll('#nodeLayer circle'),el=>[Number(el.getAttribute('cx')),Number(el.getAttribute('cy'))])`);
  check(initial.nodes.every((n,i) => Math.abs(coordinates[i][0]-(90+n.x*12.2))<1e-6 && Math.abs(coordinates[i][1]-(90+n.y*8.2))<1e-6), 'simulation coordinates map to SVG without a pending tween');
  await click('#zoomIn');
  check((await text('statusbar')).includes('1.20x'), 'zoom button updates status immediately');
  await click('#zoomOut');
  check((await text('statusbar')).includes('1.00x'), 'zoom-out status matches rounded scale');
  await click('#zoomReset');
  const svgRect = await evaluate(`(() => {const r=document.getElementById('svg').getBoundingClientRect();return {x:r.x+12,y:r.y+12};})()`);
  await mouse('mouseWheel', svgRect.x, svgRect.y, { deltaY: -120, deltaX: 0 });
  await waitFor(async () => (await text('statusbar')).includes('1.12x'), 'wheel zoom');
  check((await text('statusbar')).includes('1.12x'), 'wheel zoom synchronizes status');
  await click('#zoomReset');
  await query('photoshop','jquery');
  await waitFor(async () => (await tags('#pathResult .tag')).length===3, 'selection before pan');
  const transformBefore = await evaluate(`document.getElementById('viewport').getAttribute('transform')`);
  await mouse('mousePressed',svgRect.x,svgRect.y,{button:'left',clickCount:1});
  await mouse('mouseMoved',svgRect.x+45,svgRect.y+30,{button:'left',buttons:1});
  await mouse('mouseReleased',svgRect.x+45,svgRect.y+30,{button:'left',clickCount:1});
  check(await evaluate(`document.getElementById('viewport').getAttribute('transform')`) !== transformBefore && (await text('statusbar')).includes('photoshop'), 'background pan changes transform and preserves selection');
  await click('#zoomIn');
  const drupal = initial.nodes.find(n=>n.name==='drupal');
  const jquery = initial.nodes.find(n=>n.name==='jquery');
  const nodeSelector = '#nodeLayer circle:nth-child(' + (drupal.id+1) + ')';
  // Drag while zoomed and panned, using the viewport's real screen transform.
  async function dragDrupal(targetX, targetY) {
    const points = await evaluate(`(() => {const node=document.querySelector(${JSON.stringify(nodeSelector)}); const rect=node.getBoundingClientRect(); const point=document.getElementById('svg').createSVGPoint();point.x=90+${targetX}*12.2;point.y=90+${targetY}*8.2;const end=point.matrixTransform(document.getElementById('viewport').getScreenCTM());return {start:{x:rect.x+rect.width/2,y:rect.y+rect.height/2},end:{x:end.x,y:end.y}};})()`);
    await mouse('mousePressed',points.start.x,points.start.y,{button:'left',clickCount:1});
    await mouse('mouseMoved',points.end.x,points.end.y,{button:'left',buttons:1});
    await mouse('mouseReleased',points.end.x,points.end.y,{button:'left',clickCount:1});
  }
  await query('jquery','photoshop');
  await waitFor(async () => (await text('detailBody')).includes('jquery'), 'jquery selection');
  await dragDrupal(jquery.x,jquery.y);
  await waitFor(async () => (await text('moveStatus')).includes('已保存'), 'drag position saved');
  const moved = await api('/api/graph');
  // Native mouse client coordinates can be rounded to a screen pixel.
  check(Math.abs(moved.nodes[drupal.id].x-jquery.x)<.2 && Math.abs(moved.nodes[drupal.id].y-jquery.y)<.2, 'zoomed/panned real mouse drag updates Java coordinates');
  const savedDrupal = moved.nodes[drupal.id];
  const savedVisual = await evaluate(`(() => {const el=document.querySelector(${JSON.stringify(nodeSelector)});return [Number(el.getAttribute('cx')),Number(el.getAttribute('cy'))];})()`);
  check(Math.abs(savedVisual[0]-(90+savedDrupal.x*12.2))<1e-6 && Math.abs(savedVisual[1]-(90+savedDrupal.y*8.2))<1e-6, 'confirmed SVG position exactly matches Java response state');
  await waitFor(async () => (await tags('#nearbyBody .teal')).includes('drupal'), 'nearby refresh after movement');
  check((await tags('#nearbyBody .teal')).includes('drupal'), 'saved movement refreshes selected node nearby list');
  check(JSON.stringify(moved.edges) === JSON.stringify(initial.edges) && moved.nodes.every((n,i)=>n.degree===initial.nodes[i].degree), 'moving positions preserves topology and degree ranks');
  await click('#scanToggle');
  check(await evaluate(`document.querySelectorAll('#scanLayer ellipse').length===2`), 'simulation range displays ellipses');
  await evaluate(`document.getElementById('radiusSlider').value=25;document.getElementById('radiusSlider').dispatchEvent(new Event('input',{bubbles:true}));`);
  await waitFor(async () => (await text('nearbyBody')).includes('半径 25'), 'radius query');
  check(await evaluate(`Number(document.querySelector('#scanLayer ellipse').getAttribute('rx'))===25*12.2 && Number(document.querySelector('#scanLayer ellipse').getAttribute('ry'))===25*8.2`), 'radius changes update both ellipse axes');
  const nearby = await api('/api/nearby?name=jquery&radius=25');
  check(JSON.stringify((await tags('#nearbyBody .teal')).sort())===JSON.stringify(nearby.nearby.sort()), 'nearby display agrees with Java radius query');
  await click('#btnRefreshNearby');
  await waitFor(async () => (await text('nearbyBody')).includes('半径 25'), 'explicit nearby refresh');
  check((await text('nearbyBody')).includes('半径 25'), 'nearby refresh/retry button works');

  await intercept('/api/move');
  await dragDrupal(Math.min(100,jquery.x+10),Math.min(100,jquery.y+10));
  await waitFor(() => paused.length===1, 'failed position request');
  check((await text('moveStatus')).includes('正在保存'), 'pending position is visible');
  await dragDrupal(Math.min(100,jquery.x+20),Math.min(100,jquery.y+20));
  check(paused.length===1, 'one node cannot send overlapping position writes');
  await release(paused[0],true);
  await waitFor(async () => (await text('moveStatus')).includes('位置保存失败'), 'failed drag feedback');
  check((await text('moveStatus')).includes('已恢复'), 'failed movement restores confirmed display position');
  const recovered = await evaluate(`(() => {const el=document.querySelector(${JSON.stringify(nodeSelector)});return [Number(el.getAttribute('cx')),Number(el.getAttribute('cy'))];})()`);
  check(Math.abs(recovered[0]-(90+savedDrupal.x*12.2))<1e-6 && Math.abs(recovered[1]-(90+savedDrupal.y*8.2))<1e-6, 'failed drag rolls back SVG coordinates');
  check(JSON.stringify((await api('/api/graph')).nodes)===JSON.stringify(moved.nodes), 'rejected drag leaves Java positions unchanged');
  await send('Fetch.disable');
  await click('#btnRefreshNearby');
  await waitFor(async () => (await text('nearbyBody')).includes('半径 25'), 'retry after service error');
  check((await text('statusbar')).includes('已连接后端'), 'successful retry restores connected status');

  await intercept('/api/nearby');
  await click('#btnRefreshNearby');
  await waitFor(() => paused.length===1, 'old radius request');
  await evaluate(`document.getElementById('radiusSlider').value=30;document.getElementById('radiusSlider').dispatchEvent(new Event('input',{bubbles:true}));`);
  await waitFor(() => paused.length===2, 'new radius request');
  await release(paused[1]);
  await waitFor(async () => (await text('nearbyBody')).includes('半径 30'), 'latest radius response');
  await release(paused[0],true);
  await settled(paused[0]);
  check((await text('nearbyBody')).includes('半径 30') && (await text('statusbar')).includes('已连接后端'), 'stale radius failure does not overwrite latest result or connection status');
  await send('Fetch.disable');
  await intercept('/api/circle');
  await query('photoshop','jquery');
  await waitFor(() => paused.length===1, 'old circle request');
  await query('jquery','jquery');
  await waitFor(() => paused.length===2, 'new circle request');
  await release(paused[1]);
  await waitFor(async () => (await tags('#circleBody .tag')).length===5, 'latest circle');
  await release(paused[0],true);
  await settled(paused[0]);
  check((await tags('#circleBody .tag')).length===5 && !(await text('circleBody')).includes('查询失败'), 'old circle failure cannot overwrite current selection');
  await send('Fetch.disable');
  await evaluate(`for(let i=0;i<5;i++){document.querySelector('[data-view=topo]').click();document.querySelector('[data-view=geo]').click();}`);
  await evaluate(`new Promise(resolve=>setTimeout(resolve,150))`);
  const afterSwitch = await evaluate(`Array.from(document.querySelectorAll('#nodeLayer circle'),el=>[Number(el.getAttribute('cx')),Number(el.getAttribute('cy'))])`);
  check(moved.nodes.every((n,i)=>Math.abs(afterSwitch[i][0]-(90+n.x*12.2))<.03 && Math.abs(afterSwitch[i][1]-(90+n.y*8.2))<.03), 'rapid view changes leave no old animation writing coordinates');
  await click('#zoomReset');
  await mkdir(path.join(root,'out','browser'),{recursive:true});
  const image = await send('Page.captureScreenshot',{format:'png'});
  await writeFile(path.join(root,'out','browser','simulation.png'),Buffer.from(image.data,'base64'));
  await click('[data-view=topo]');
  await waitFor(() => evaluate(`document.querySelectorAll('#scanLayer ellipse').length===0`), 'topology hides range');
  check((await text('statusbar')).includes('拓扑视图'), 'topology view hides spatial range');
  const beforeTopoDrag = await api('/api/graph');
  await click('#zoomReset');
  const nodePoint = await evaluate(`(() => {const r=document.querySelector('#nodeLayer circle').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()`);
  await mouse('mousePressed',nodePoint.x,nodePoint.y,{button:'left',clickCount:1});
  await mouse('mouseMoved',nodePoint.x+30,nodePoint.y+20,{button:'left',buttons:1});
  await mouse('mouseReleased',nodePoint.x+30,nodePoint.y+20,{button:'left',clickCount:1});
  check(JSON.stringify((await api('/api/graph')).nodes)===JSON.stringify(beforeTopoDrag.nodes), 'topology dragging does not write simulation positions');
  check(['/api/graph','/api/path','/api/circle','/api/nearby','/api/classify','/api/move'].every(route=>requests.some(r=>r.url.startsWith(base+route))), 'browser exercises all six Java API routes');
  await intercept('/api/graph');
  await send('Page.navigate',{url:base+'/'});
  await waitFor(() => paused.length===1, 'initial graph load failure');
  await release(paused[0],true);
  await waitFor(() => evaluate(`getComputedStyle(document.getElementById('connOverlay')).display==='flex'`), 'startup error overlay');
  check(await evaluate(`document.querySelectorAll('#nodeLayer circle').length===0`), 'failed initial load displays actionable overlay without a fake graph');
  await send('Fetch.disable');
  await click('#connOverlay button');
  await waitFor(() => evaluate(`document.querySelectorAll('#nodeLayer circle').length===11 && getComputedStyle(document.getElementById('connOverlay')).display==='none'`), 'startup retry');
  check(await evaluate(`document.querySelectorAll('#nodeLayer circle').length===11`), 'startup retry reloads the real Java graph');
  check(errors.length===0, 'no uncaught browser JavaScript exceptions');
  console.log(`PASS: ${checks} browser checks; actual mouse/keyboard input and delayed/failed requests`);
  await writeFile(path.join(root,'out','browser','result.json'),JSON.stringify({checks,passed:true,browser,java},null,2));
} catch (err) {
  console.error(err);
  console.error('Java log:',serverLog);
  console.error('Browser log tail:',browserLog.slice(-1500));
  process.exitCode=1;
} finally {
  ws?.close();
  for (const proc of children.reverse()) {
    if (proc.exitCode===null) {
      proc.kill();
      await Promise.race([new Promise(resolve=>proc.once('exit',resolve)),new Promise(resolve=>setTimeout(resolve,1000))]);
    }
  }
  for (const waiter of pending.values()) { clearTimeout(waiter.timer); waiter.reject(new Error('Browser closed')); }
  await rm(profile,{recursive:true,force:true,maxRetries:3,retryDelay:100}).catch(()=>{});
}
