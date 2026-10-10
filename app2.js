(function () {
  "use strict";

  /* ============================================================
     0. 后端接口地址（相对路径：网页由ApiServer同源托管时直接可用）
     ============================================================ */
  const API = {
    graph: "/api/graph",
    path: function (a, b) { return "/api/path?a=" + encodeURIComponent(a) + "&b=" + encodeURIComponent(b); },
    circle: function (name) { return "/api/circle?name=" + encodeURIComponent(name); },
    nearby: function (name, r) { return "/api/nearby?name=" + encodeURIComponent(name) + "&radius=" + r; },
    classify: "/api/classify",
    move: function (name, x, y) { return "/api/move?name=" + encodeURIComponent(name) + "&x=" + x + "&y=" + y; }
  };

  let backendConnected = false, connectionRequest = 0;
  async function getJson(url, opts) {
    const request = ++connectionRequest;
    const controller = new AbortController();
    const timeout = setTimeout(function () { controller.abort(); }, 10000);
    try {
      const res = await fetch(url, Object.assign({}, opts, { signal: controller.signal }));
      if (request === connectionRequest) backendConnected = true;
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || data.reason || ("HTTP " + res.status));
      return data;
    } catch (err) {
      if (err.name === "AbortError" || err instanceof TypeError) {
        if (request === connectionRequest) backendConnected = false;
        throw new Error("服务无响应，请确认 Java 服务正在运行后重试");
      }
      throw err;
    } finally {
      clearTimeout(timeout);
      renderStatusbar();
    }
  }
  function panelError(box, err) {
    box.textContent = "查询失败：" + err.message + "。请重试。";
  }

  let nodes = [];      // 由 /api/graph 填充：{id,name,degree,cls,geoX,geoY,x,y,vx,vy,pinned,circleEl,labelEl}
  let edges = [];      // {a,b,lineEl}
  let edgeByKey = {};
  let nameToId = Object.create(null);
  let N = 0;

  /* ============================================================
     1. 启动：从后端拉取整张图（对应需求(1)(2)：建图+坐标，这些数据全部来自GraphLoader/MatGraphClass）
     ============================================================ */
  async function loadGraph() {
    const data = await getJson(API.graph);
    nameToId = Object.create(null);
    N = data.nodes.length;
    nodes = new Array(N);
    data.nodes.forEach(function (n) {
      nodes[n.id] = {
        id: n.id, name: n.name, degree: n.degree, cls: n.cls,
        geoX: n.x, geoY: n.y, x: 0, y: 0, vx: 0, vy: 0,
        pinned: false, saving: false, neighbors: [], circleEl: null, labelEl: null
      };
      nameToId[n.name] = n.id;
    });
    edges = [];
    edgeByKey = {};
    data.edges.forEach(function (e) {
      const edgeObj = { a: e.a, b: e.b, lineEl: null };
      edges.push(edgeObj);
      edgeByKey[e.a + "_" + e.b] = edgeObj;
      nodes[e.a].neighbors.push(e.b);
      nodes[e.b].neighbors.push(e.a);
    });
  }

  /* ============================================================
     2. 坐标映射：geo(0~100) -> svg viewBox
     ============================================================ */
  const VB_W = 1400, VB_H = 1000, PADX = 90, PADY = 90;
  const SCALE_X = (VB_W - 2 * PADX) / 100;
  const SCALE_Y = (VB_H - 2 * PADY) / 100;
  function geoToSvg(nd) { return { x: PADX + nd.geoX * SCALE_X, y: PADY + nd.geoY * SCALE_Y }; }

  /* ============================================================
     3. SVG 渲染（纯前端视觉层，不涉及六项需求的业务逻辑）
     ============================================================ */
  const svg = document.getElementById("svg");
  const viewport = document.getElementById("viewport");
  const edgeLayer = document.getElementById("edgeLayer");
  const nodeLayer = document.getElementById("nodeLayer");
  const labelLayer = document.getElementById("labelLayer");
  const scanLayer = document.getElementById("scanLayer");
  const NS = "http://www.w3.org/2000/svg";

  function radiusOf(nd) { return nd.cls === "core" ? 8 : (nd.cls === "active" ? 5.5 : 3.5); }
  function colorOf(nd) { return nd.cls === "core" ? "var(--copper)" : (nd.cls === "active" ? "var(--cyan)" : "var(--muted2)"); }

  function buildSvg() {
    edgeLayer.innerHTML = ""; nodeLayer.innerHTML = ""; labelLayer.innerHTML = "";

    nodes.forEach(function (nd) {
      const p = geoToSvg(nd);
      nd.x = p.x; nd.y = p.y;
    });

    edges.forEach(function (e) {
      const line = document.createElementNS(NS, "line");
      line.setAttribute("class", "edge-line");
      line.setAttribute("x1", nodes[e.a].x); line.setAttribute("y1", nodes[e.a].y);
      line.setAttribute("x2", nodes[e.b].x); line.setAttribute("y2", nodes[e.b].y);
      edgeLayer.appendChild(line);
      e.lineEl = line;
    });

    nodes.forEach(function (nd) {
      const c = document.createElementNS(NS, "circle");
      c.setAttribute("r", radiusOf(nd));
      c.setAttribute("cx", nd.x); c.setAttribute("cy", nd.y);
      c.setAttribute("fill", colorOf(nd));
      c.setAttribute("stroke", "#04070a");
      c.setAttribute("stroke-width", "1");
      c.style.cursor = "pointer";
      nodeLayer.appendChild(c);
      nd.circleEl = c;

      const t = document.createElementNS(NS, "text");
      t.setAttribute("class", "node-label");
      t.setAttribute("x", nd.x + radiusOf(nd) + 3);
      t.setAttribute("y", nd.y + 3);
      t.textContent = nd.name;
      labelLayer.appendChild(t);
      nd.labelEl = t;

      c.addEventListener("mousedown", function (e) { startNodeDrag(nd, e); });
    });
  }

  function updateNodeVisual(nd) {
    nd.circleEl.setAttribute("cx", nd.x);
    nd.circleEl.setAttribute("cy", nd.y);
    nd.labelEl.setAttribute("x", nd.x + radiusOf(nd) + 3);
    nd.labelEl.setAttribute("y", nd.y + 3);
    nd.neighbors.forEach(function (wid) {
      const key = nd.id < wid ? nd.id + "_" + wid : wid + "_" + nd.id;
      const e = edgeByKey[key];
      if (!e) return;
      e.lineEl.setAttribute("x1", nodes[e.a].x); e.lineEl.setAttribute("y1", nodes[e.a].y);
      e.lineEl.setAttribute("x2", nodes[e.b].x); e.lineEl.setAttribute("y2", nodes[e.b].y);
    });
    if (selectedId !== null && nd.id === selectedId) updateScanRing();
  }

  /* ============================================================
     4. 简易力导向布局（纯前端视觉排布，跟后端算法无关）
     ============================================================ */
  let mode = "topo";
  let alpha = 1, simRunning = false, simFrame = null;
  const REPULSE_K = 2600, SPRING_L = 78, SPRING_K = 0.02, CENTER_K = 0.006, DAMP = 0.82, MAX_SPEED = 18;

  function forceTick() {
    simFrame = null;
    if (mode !== "topo" || !simRunning) return;
    const cx = VB_W / 2, cy = VB_H / 2;
    const ax = new Array(N).fill(0), ay = new Array(N).fill(0);

    for (let i = 0; i < N; i++) {
      for (let j = i + 1; j < N; j++) {
        let dx = nodes[i].x - nodes[j].x, dy = nodes[i].y - nodes[j].y;
        let d2 = dx * dx + dy * dy + 0.01;
        let d = Math.sqrt(d2);
        let f = (REPULSE_K / d2) * alpha;
        let fx = (dx / d) * f, fy = (dy / d) * f;
        ax[i] += fx; ay[i] += fy;
        ax[j] -= fx; ay[j] -= fy;
      }
    }
    edges.forEach(function (e) {
      const A = nodes[e.a], B = nodes[e.b];
      let dx = B.x - A.x, dy = B.y - A.y;
      let d = Math.sqrt(dx * dx + dy * dy) + 0.01;
      let f = (d - SPRING_L) * SPRING_K * alpha;
      let fx = (dx / d) * f, fy = (dy / d) * f;
      ax[e.a] += fx; ay[e.a] += fy;
      ax[e.b] -= fx; ay[e.b] -= fy;
    });

    let maxV = 0;
    for (let i = 0; i < N; i++) {
      const nd = nodes[i];
      if (nd.pinned) { nd.vx = 0; nd.vy = 0; continue; }
      ax[i] += (cx - nd.x) * CENTER_K * alpha;
      ay[i] += (cy - nd.y) * CENTER_K * alpha;
      nd.vx = (nd.vx + ax[i]) * DAMP;
      nd.vy = (nd.vy + ay[i]) * DAMP;
      const speed = Math.sqrt(nd.vx * nd.vx + nd.vy * nd.vy);
      if (speed > MAX_SPEED) { nd.vx = (nd.vx / speed) * MAX_SPEED; nd.vy = (nd.vy / speed) * MAX_SPEED; }
      nd.x += nd.vx;
      nd.y += nd.vy;
      maxV = Math.max(maxV, Math.abs(nd.vx), Math.abs(nd.vy));
    }
    nodes.forEach(updateNodeVisual);

    alpha *= 0.992;
    if (alpha < 0.01 && maxV < 0.02) { simRunning = false; return; }
    simFrame = requestAnimationFrame(forceTick);
  }
  function wakeSim() {
    if (mode === "topo") {
      alpha = 1; simRunning = true;
      if (simFrame === null) simFrame = requestAnimationFrame(forceTick);
    }
  }
  function stopSim() {
    simRunning = false;
    if (simFrame !== null) cancelAnimationFrame(simFrame);
    simFrame = null;
  }

  /* ============================================================
     5. 缩放 / 平移
     ============================================================ */
  let tx = 0, ty = 0, scale = 1;
  function applyTransform() {
    viewport.setAttribute("transform", "translate(" + tx + "," + ty + ") scale(" + scale + ")");
    renderStatusbar();
  }
  function toViewBoxPoint(clientX, clientY) {
    // Respect SVG letterboxing at any browser size.
    const point = svg.createSVGPoint();
    point.x = clientX; point.y = clientY;
    return point.matrixTransform(svg.getScreenCTM().inverse());
  }
  function zoomAt(px, py, factor) {
    const newScale = Math.min(4, Math.max(0.25, scale * factor));
    const wx = (px - tx) / scale, wy = (py - ty) / scale;
    tx = px - wx * newScale; ty = py - wy * newScale; scale = newScale;
    applyTransform();
  }
  svg.addEventListener("wheel", function (e) {
    e.preventDefault();
    const p = toViewBoxPoint(e.clientX, e.clientY);
    zoomAt(p.x, p.y, e.deltaY < 0 ? 1.12 : 0.89);
  }, { passive: false });

  let panning = false, panStart = null, panOrigin = null, dragMoved = false;
  svg.addEventListener("mousedown", function (e) {
    if (e.button !== 0 || e.target.tagName === "circle") return;
    panning = true; dragMoved = false;
    panStart = toViewBoxPoint(e.clientX, e.clientY);
    panOrigin = { x: e.clientX, y: e.clientY };
    svg.classList.add("dragging");
  });
  window.addEventListener("mousemove", function (e) {
    if (!panning) return;
    const p = toViewBoxPoint(e.clientX, e.clientY);
    tx += p.x - panStart.x; ty += p.y - panStart.y;
    dragMoved = dragMoved || Math.hypot(e.clientX - panOrigin.x, e.clientY - panOrigin.y) > 3;
    panStart = p;
    applyTransform();
  });
  window.addEventListener("mouseup", function () {
    if (!panning) return;
    panning = false; svg.classList.remove("dragging");
    if (!dragMoved) { clearSelection(); }
  });

  document.getElementById("zoomIn").addEventListener("click", function () { zoomAt(VB_W / 2, VB_H / 2, 1.2); });
  document.getElementById("zoomOut").addEventListener("click", function () { zoomAt(VB_W / 2, VB_H / 2, 0.83); });
  document.getElementById("zoomReset").addEventListener("click", resetView);
  function resetView() { tx = 0; ty = 0; scale = 1; applyTransform(); }

  /* ============================================================
     6. 节点拖拽
        地理视图下拖动 = 调用后端 POST /api/move 真实修改Vertex坐标
        （对应需求(4)：位置动态变化 -> 后端Vertex.setX/setY被真实调用）
     ============================================================ */
  let dragNode = null, dragLastClient = null, dragOrigin = null, dragOriginal = null, nodeDragMoved = false;
  function startNodeDrag(nd, e) {
    if (e.button !== 0) return;
    e.stopPropagation();
    e.preventDefault();
    if (nd.saving) return; // Do not send overlapping position writes for one node.
    dragNode = nd; dragLastClient = { x: e.clientX, y: e.clientY }; nodeDragMoved = false;
    dragOrigin = dragLastClient;
    dragOriginal = { geoX: nd.geoX, geoY: nd.geoY, x: nd.x, y: nd.y };
    nd.pinned = true;
  }
  window.addEventListener("mousemove", function (e) {
    if (!dragNode) return;
    if (Math.hypot(e.clientX - dragOrigin.x, e.clientY - dragOrigin.y) > 3) nodeDragMoved = true;
    const before = toViewBoxPoint(dragLastClient.x, dragLastClient.y);
    const after = toViewBoxPoint(e.clientX, e.clientY);
    const dx = (after.x - before.x) / scale;
    const dy = (after.y - before.y) / scale;
    dragLastClient = { x: e.clientX, y: e.clientY };
    dragNode.x += dx; dragNode.y += dy;

    if (mode === "geo") {
      dragNode.geoX = Math.min(100, Math.max(0, (dragNode.x - PADX) / SCALE_X));
      dragNode.geoY = Math.min(100, Math.max(0, (dragNode.y - PADY) / SCALE_Y));
      const bounded = geoToSvg(dragNode);
      dragNode.x = bounded.x; dragNode.y = bounded.y;
    }
    updateNodeVisual(dragNode);
  });
  window.addEventListener("mouseup", async function () {
    if (dragNode) {
      dragNode.pinned = false;
      const moved = nodeDragMoved;
      const nd = dragNode;
      const original = dragOriginal;
      dragNode = null;

      if (!moved) {
        nd.geoX = original.geoX; nd.geoY = original.geoY;
        nd.x = original.x; nd.y = original.y;
        updateNodeVisual(nd);
        selectNode(nd.id);
      } else if (mode === "geo") {
        // Update Java memory for this run; no disk persistence is implied.
        nd.saving = true;
        nd.circleEl.style.cursor = "wait";
        const feedback = document.getElementById("moveStatus");
        feedback.textContent = "正在保存 " + nd.name + " 的模拟位置…";
        try {
          nd.geoX = Number(nd.geoX.toFixed(3)); nd.geoY = Number(nd.geoY.toFixed(3));
          const result = await getJson(API.move(nd.name, nd.geoX, nd.geoY), { method: "POST" });
          if (!result.success) throw new Error("服务未确认位置更新");
          feedback.textContent = nd.name + " 的模拟位置已保存到本次运行，附近列表已重新查询；重启后恢复初始值。";
          if (selectedId !== null) refreshNearbyPanel();
        } catch (err) {
          nd.geoX = original.geoX; nd.geoY = original.geoY;
          feedback.textContent = "位置保存失败，已恢复上次确认的显示位置：" + err.message + "。可刷新页面重新核对服务端状态。";
        } finally {
          nd.saving = false;
          nd.circleEl.style.cursor = "pointer";
          if (mode === "geo") { const p = geoToSvg(nd); nd.x = p.x; nd.y = p.y; updateNodeVisual(nd); }
          if (selectedId === nd.id) renderDetail();
        }
      }
      wakeSim();
      return;
    }
  });

  /* ============================================================
     7. 视图模式切换
     ============================================================ */
  document.querySelectorAll("#viewSeg button").forEach(function (btn) {
    btn.addEventListener("click", function () {
      document.querySelectorAll("#viewSeg button").forEach(function (b) { b.classList.remove("active"); });
      btn.classList.add("active");
      mode = btn.dataset.view;
      if (mode === "geo") {
        stopSim();
        // Switch immediately: no old tween can overwrite a later drag or view.
        nodes.forEach(function (nd) { const p = geoToSvg(nd); nd.x = p.x; nd.y = p.y; updateNodeVisual(nd); });
      } else {
        wakeSim();
      }
      updateScanRing();
      renderStatusbar();
    });
  });

  /* ============================================================
     8. 选中 / 高亮 / 面板刷新（数据全部来自后端接口）
     ============================================================ */
  let selectedId = null;
  let pathEdgeKeys = new Set();
  let pathNodeIds = new Set();
  let pathRequest = 0, circleRequest = 0;

  function refreshEdgeStyles() {
    edges.forEach(function (e) {
      const key = e.a + "_" + e.b;
      const cls = ["edge-line"];
      if (pathEdgeKeys.has(key)) cls.push("path-highlight");
      else if (selectedId !== null && (e.a === selectedId || e.b === selectedId)) cls.push("circle-highlight");
      else if (selectedId !== null || pathEdgeKeys.size) cls.push("dim");
      e.lineEl.setAttribute("class", cls.join(" "));
    });
    nodes.forEach(function (nd) {
      nd.labelEl.setAttribute("class", "node-label" + (nd.id === selectedId || pathNodeIds.has(nd.id) ? " selected" : ""));
    });
  }

  function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, function (ch) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch];
    });
  }

  function tagHtml(name, colorClass) {
    const id = nameToId[name];
    const cls = "tag" + (colorClass ? " " + colorClass : "");
    return '<span class="' + cls + '" data-id="' + id + '">' + escapeHtml(name) + "</span>";
  }
  function bindTagClicks(container) {
    container.querySelectorAll(".tag[data-id]").forEach(function (el) {
      el.addEventListener("click", function () { selectNode(parseInt(el.dataset.id, 10)); });
    });
  }

  function clsLabel(cls) { return cls === "core" ? "核心人物" : (cls === "active" ? "活跃人物" : "边缘人物"); }
  function clsBadgeClass(cls) { return cls === "core" ? "core" : (cls === "active" ? "active" : "edge"); }

  async function selectNode(id, keepPath) {
    selectedId = id;
    if (!keepPath) { ++pathRequest; pathEdgeKeys = new Set(); pathNodeIds = new Set(); document.getElementById("pathResult").innerHTML = ""; }
    refreshEdgeStyles();
    renderDetail();
    updateScanRing();
    renderStatusbar();
    await Promise.all([renderCircle(), refreshNearbyPanel()]);
  }
  function clearSelection() {
    ++pathRequest; ++circleRequest; ++nearbyRequest;
    selectedId = null; pathEdgeKeys = new Set(); pathNodeIds = new Set();
    refreshEdgeStyles();
    document.getElementById("detailBody").innerHTML = '<span class="empty">点击图中任意节点查看详情</span>';
    document.getElementById("circleBody").innerHTML = '<span class="empty">选中一个节点后显示其直接联系人</span>';
    document.getElementById("nearbyBody").innerHTML = '<span class="empty">先在图中选中一个节点</span>';
    document.getElementById("pathResult").innerHTML = "";
    updateScanRing();
    renderStatusbar();
  }

  /* ---- 需求(1)(2)：节点详情（数据来自 /api/graph 初次加载的结果） ---- */
  function renderDetail() {
    const nd = nodes[selectedId];
    document.getElementById("detailBody").innerHTML =
      '<div class="kv"><span class="k">名称</span><span class="v">' + escapeHtml(nd.name) + '</span></div>' +
      '<div class="kv"><span class="k">模拟坐标</span><span class="v">(' + nd.geoX.toFixed(2) + ', ' + nd.geoY.toFixed(2) + ')</span></div>' +
      '<div class="kv"><span class="k">度数</span><span class="v">' + nd.degree + '</span></div>' +
      '<div class="kv"><span class="k">分类</span><span class="v"><span class="badge ' + clsBadgeClass(nd.cls) + '">' + clsLabel(nd.cls) + '</span></span></div>';
  }

  /* ---- 需求(6)：交往圈子 -> 调用 GET /api/circle ---- */
  async function renderCircle() {
    const request = ++circleRequest;
    const nd = nodes[selectedId];
    const box = document.getElementById("circleBody");
    box.innerHTML = '<span class="empty">正在向后端查询…</span>';
    let data;
    try { data = await getJson(API.circle(nd.name)); } catch (e) {
      if (request === circleRequest && selectedId === nd.id) panelError(box, e);
      return;
    }
    if (request !== circleRequest || selectedId !== nd.id) return;
    if (!data.found || data.circle.length === 0) { box.innerHTML = '<span class="empty">该节点没有直接联系人</span>'; return; }
    box.innerHTML = '<div class="kv"><span class="k">直接联系人</span><span class="v">' + data.circle.length + ' 人</span></div>' +
      '<div class="taglist">' + data.circle.map(function (name) { return tagHtml(name); }).join("") + '</div>';
    bindTagClicks(box);
  }

  /* ---- 需求(4)：附近信号 + 1个中间人可达 -> 调用 GET /api/nearby ---- */
  let nearbyRequest = 0;
  async function refreshNearbyPanel() {
    const request = ++nearbyRequest;
    const box = document.getElementById("nearbyBody");
    if (selectedId === null) { box.innerHTML = '<span class="empty">先在图中选中一个节点</span>'; return; }
    const nd = nodes[selectedId];
    const r = parseInt(document.getElementById("radiusSlider").value, 10);
    box.innerHTML = '<span class="empty">正在向后端查询…</span>';
    let data;
    try { data = await getJson(API.nearby(nd.name, r)); } catch (e) {
      if (request === nearbyRequest && selectedId === nd.id) panelError(box, e);
      return;
    }
    if (request !== nearbyRequest || selectedId !== nd.id) return;
    if (!data.found) { box.innerHTML = '<span class="empty">查询失败</span>'; return; }

    box.innerHTML =
      '<div class="kv"><span class="k">半径 ' + r + ' 内共</span><span class="v" style="color:var(--teal)">' + data.nearby.length + ' 人</span></div>' +
      '<div class="taglist">' + data.nearby.map(function (n2) { return tagHtml(n2, "teal"); }).join("") + '</div>' +
      '<div class="kv" style="margin-top:10px"><span class="k">经1个中间人可达</span><span class="v" style="color:var(--copper)">' + data.oneIntermediary.length + ' 人</span></div>' +
      '<div class="taglist">' + (data.oneIntermediary.length ? data.oneIntermediary.map(function (n2) { return tagHtml(n2, "copper"); }).join("") : '<span class="empty">无</span>') + '</div>';
    bindTagClicks(box);
    updateScanRing();
  }

  let scanOn = false;
  function updateScanRing() {
    scanLayer.innerHTML = "";
    if (!scanOn || selectedId === null || mode !== "geo") return;
    const nd = nodes[selectedId];
    const r = parseInt(document.getElementById("radiusSlider").value, 10);
    const rx = r * SCALE_X, ry = r * SCALE_Y;

    const staticRing = document.createElementNS(NS, "ellipse");
    staticRing.setAttribute("cx", nd.x); staticRing.setAttribute("cy", nd.y);
    staticRing.setAttribute("rx", rx); staticRing.setAttribute("ry", ry);
    staticRing.setAttribute("fill", "rgba(89,230,176,0.05)");
    staticRing.setAttribute("stroke", "var(--teal)");
    staticRing.setAttribute("stroke-width", "1");
    staticRing.setAttribute("stroke-dasharray", "4 3");
    scanLayer.appendChild(staticRing);

    const ping = document.createElementNS(NS, "ellipse");
    ping.setAttribute("cx", nd.x); ping.setAttribute("cy", nd.y);
    ping.setAttribute("rx", "4"); ping.setAttribute("ry", "4"); ping.setAttribute("fill", "none");
    ping.setAttribute("stroke", "var(--teal)"); ping.setAttribute("stroke-width", "1.4");
    const anR = document.createElementNS(NS, "animate");
    anR.setAttribute("attributeName", "rx"); anR.setAttribute("values", "4;" + rx);
    anR.setAttribute("dur", "1.8s"); anR.setAttribute("repeatCount", "indefinite");
    const anO = document.createElementNS(NS, "animate");
    anO.setAttribute("attributeName", "opacity"); anO.setAttribute("values", "0.65;0");
    anO.setAttribute("dur", "1.8s"); anO.setAttribute("repeatCount", "indefinite");
    const anY = anR.cloneNode();
    anY.setAttribute("attributeName", "ry"); anY.setAttribute("values", "4;" + ry);
    ping.appendChild(anR); ping.appendChild(anY); ping.appendChild(anO);
    scanLayer.appendChild(ping);
  }
  document.getElementById("scanToggle").addEventListener("click", function () {
    scanOn = !scanOn;
    this.classList.toggle("on", scanOn);
    updateScanRing();
  });
  document.getElementById("radiusSlider").addEventListener("input", function () {
    document.getElementById("radiusVal").textContent = this.value;
    updateScanRing();
    refreshNearbyPanel();
  });
  document.getElementById("btnRefreshNearby").addEventListener("click", refreshNearbyPanel);

  /* ---- 需求(3)：最短路径查询 -> 调用 GET /api/path ---- */
  document.getElementById("btnPath").addEventListener("click", async function () {
    const request = ++pathRequest;
    const nameA = document.getElementById("pathA").value.trim();
    const nameB = document.getElementById("pathB").value.trim();
    const box = document.getElementById("pathResult");
    pathEdgeKeys.clear(); pathNodeIds.clear(); refreshEdgeStyles();
    if (!nameA || !nameB) { box.textContent = "请填写起点和终点名称"; return; }
    box.innerHTML = '<span class="empty">正在向后端查询…</span>';

    let data;
    try { data = await getJson(API.path(nameA, nameB)); } catch (e) {
      if (request === pathRequest) panelError(box, e);
      return;
    }
    if (request !== pathRequest) return;

    if (!data.found) {
      box.innerHTML = '<span class="empty">' + (data.reason === "name_not_found" ? "名字不存在，请检查拼写（可从下拉列表中选择）" : "两个人一点关系没有（网络中不连通）") + '</span>';
      pathEdgeKeys = new Set(); pathNodeIds = new Set();
      refreshEdgeStyles();
      return;
    }

    const path = data.path.map(function (name) { return nameToId[name]; });
    pathEdgeKeys = new Set();
    pathNodeIds = new Set(path);
    for (let i = 0; i < path.length - 1; i++) {
      const p = path[i], q = path[i + 1];
      pathEdgeKeys.add(p < q ? p + "_" + q : q + "_" + p);
    }
    box.innerHTML =
      '<div class="kv"><span class="k">最少经过中间人</span><span class="v" style="color:var(--copper);font-weight:600">' + data.intermediaries + ' 人</span></div>' +
      '<div class="taglist">' + data.path.map(function (name, i) {
        return '<span class="tag' + (i === 0 || i === data.path.length - 1 ? ' teal' : ' copper') + '" data-id="' + nameToId[name] + '">' + escapeHtml(name) + '</span>';
      }).join(' &rarr; ') + '</div>';
    bindTagClicks(box);
    await selectNode(path[0], true);
  });
  ["pathA", "pathB"].forEach(function (id) {
    document.getElementById(id).addEventListener("input", function () {
      ++pathRequest; pathEdgeKeys.clear(); pathNodeIds.clear(); refreshEdgeStyles();
      document.getElementById("pathResult").textContent = "输入已变更，请重新查询";
    });
  });

  /* ---- 需求(5)：核心/活跃/边缘统计 -> 调用 GET /api/classify ---- */
  async function renderStats() {
    let data;
    try { data = await getJson(API.classify); } catch (e) { panelError(document.getElementById("statGrid"), e); return; }
    document.getElementById("statGrid").innerHTML =
      '<div class="statcell"><div class="n" style="color:var(--copper)">' + data.core.length + '</div><div class="l">核心</div></div>' +
      '<div class="statcell"><div class="n" style="color:var(--cyan)">' + data.active.length + '</div><div class="l">活跃</div></div>' +
      '<div class="statcell"><div class="n" style="color:var(--muted)">' + data.edge.length + '</div><div class="l">边缘</div></div>';
    const box = document.getElementById("coreList");
    box.innerHTML = data.core.map(function (item) { return tagHtml(item.name, "copper"); }).join("");
    bindTagClicks(box);
  }

  document.querySelectorAll(".panel > .ph").forEach(function (h) {
    h.addEventListener("click", function () { h.parentElement.classList.toggle("collapsed"); });
  });

  function fillNameList() {
    const datalist = document.getElementById("nameList");
    datalist.innerHTML = "";
    nodes.forEach(function (nd) {
      const opt = document.createElement("option");
      opt.value = nd.name; datalist.appendChild(opt);
    });
  }

  function renderStatusbar() {
    const modeLabel = mode === "topo" ? "拓扑视图" : "模拟位置视图";
    const sel = selectedId !== null ? nodes[selectedId].name : "无";
    document.getElementById("statusbar").innerHTML =
      "<b>节点</b> " + N + " &nbsp; <b>关系</b> " + edges.length +
      " &nbsp; <b>视图</b> " + modeLabel + " &nbsp; <b>缩放</b> " + scale.toFixed(2) + "x &nbsp; <b>选中</b> " + escapeHtml(sel) +
      " &nbsp; <b>" + (backendConnected ? "● 已连接后端" : "● 服务未响应，请重试") + "</b>";
  }

  function showConnError() {
    document.getElementById("connOverlay").style.display = "flex";
  }

  /* ============================================================
     9. 键盘操作
     ============================================================ */
  document.addEventListener("keydown", function (e) {
    if (e.target.tagName === "INPUT") {
      if (e.key === "Escape") e.target.blur();
      if (e.key === "Enter" && (e.target.id === "pathA" || e.target.id === "pathB")) document.getElementById("btnPath").click();
      return;
    }
    switch (e.key) {
      case "ArrowUp": ty += 45; applyTransform(); break;
      case "ArrowDown": ty -= 45; applyTransform(); break;
      case "ArrowLeft": tx += 45; applyTransform(); break;
      case "ArrowRight": tx -= 45; applyTransform(); break;
      case "+": case "=": zoomAt(VB_W / 2, VB_H / 2, 1.15); break;
      case "-": case "_": zoomAt(VB_W / 2, VB_H / 2, 0.87); break;
      case "Escape": clearSelection(); resetView(); break;
      case "/": e.preventDefault(); document.getElementById("pathA").focus(); break;
    }
    if (["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "+", "=", "-", "Escape"].includes(e.key)) e.preventDefault();
  });

  /* ============================================================
     10. 启动流程：先向后端要图数据，失败则显示连接错误遮罩
     ============================================================ */
  (async function start() {
    try {
      await loadGraph();
    } catch (e) {
      showConnError();
      return;
    }
    buildSvg();
    applyTransform();
    refreshEdgeStyles();
    fillNameList();
    renderStatusbar();
    wakeSim();
    renderStats(); // 需求(5)，独立请求一次
  })();

})();
