# 验证记录与本机复核

本次记录对应 PR #1 合并后的后续修复，基线为 `main` 的 `3f8bdd9`。PR #1 已合并，不再是草稿；本次使用后续分支 `codex/project-showcase-followup`。验证日期：2026-10-10。

## 已执行的检查

| 检查 | 环境与方式 | 结果 |
| --- | --- | --- |
| Java 回归 | Linux x86_64，Temurin JDK 17.0.20.1+1 与 25.0.4.1+1，`bash scripts/run.sh --verify` | 两个 JDK 各 49 项通过；保留此前 47 项，增加缺少半径和 HTTP 断连路径检查 |
| 编译目标 | 启动脚本使用 `--release 17`、UTF-8；完整 JDK 的编译模块 | 源码和测试均按 Java 17 编译 |
| 浏览器完整流程 | Chromium 154.0.8037.57，Node.js 24.19.0，`node tests/browser-flow.mjs` | 43 项通过，包括真实鼠标／键盘输入与网络故障注入；没有未捕获的 JavaScript 异常 |
| PowerShell 脚本 | PowerShell 7.6.6 / Linux，JDK 17，`scripts/run.ps1 -Verify` | 49 项 Java 回归通过；此结果不是 Windows 实测 |
| 路径与调用目录 | 两种脚本，项目、JAVA_HOME、CSV 的路径包含中文、空格、方括号；从另一目录启动 | 编译、回归及实际 HTTP 建图均通过；显式相对 CSV 正确解析，PowerShell 恢复调用者目录 |
| 错误 JDK 配置 | 两种脚本传入不存在的 `JAVA_HOME` | 明确报错，返回非零状态 |
| 示例数据口径 | 默认 `data/sample_edges.csv` | 11 节点、13 无向关系，路径 `photoshop → css → jquery`，分类 3/5/3 |

JDK 和便携 PowerShell 从各自官方 GitHub Release 下载并校验官方 SHA-256，未关闭 TLS 或校验。应用本身仍无第三方 Java 依赖；可选浏览器回归不需要 npm 包。

## 浏览器检查覆盖什么

- 网页加载真实 Java 图、分类、最少中间人路径（包括同节点）、圈子与两跳扩展；实际请求覆盖六类 API。
- 空输入、未知名称、Enter 提交；编辑输入、Escape 和后发查询能作废旧路径响应。
- 切换拓扑／模拟位置，快速反复切换不残留旧动画；按钮、滚轮缩放及状态显示，真实背景平移保留选择。
- 缩放和平移后真实拖动节点，前端位置与 Java 保存坐标一致；松开后重新查询附近列表，关系和度数保持不变。
- 半径变化、扫描椭圆、手动刷新；故意延迟圈子／附近／路径请求，旧结果和旧失败不会覆盖新选择。
- 同一节点保存期间阻止重叠写入；故意让位置 POST 在到达服务器前失败，反馈失败并回退显示；重试后恢复连接状态。
- 故意让首次 `/api/graph` 加载失败，显示连接错误；点击重试可重新加载真实图。

鼠标位置允许小于一个屏幕像素的换算误差；另有检查确保最终 SVG 坐标与 Java 已保存值的映射一致。不是只检查开端口、进程 PID 或 JavaScript 语法。

测试中的断网发生在请求到达 Java 前。若真实网络在服务器已更新后丢失响应，无法据失败提示断言服务端未改动；需要刷新重新读取实际坐标。

## 复现命令

普通算法与 API 回归：

```bash
bash scripts/run.sh --verify
```

可选真实浏览器回归（需要 Node.js 22+ 以及 Chromium/Chrome，可通过 `BROWSER_PATH` 指定可执行文件）：

```bash
node tests/browser-flow.mjs
```

脚本自动创建临时服务、浏览器和用户目录，并在结束时清理其进程。生成的 `out/browser/result.json` 与 `simulation.png` 属于当前检查运行，目录已被 Git 忽略。截图包含故障注入后的失败提示，这是被验证的交互状态。

## 尚未进行的 Windows 与 IDEA 实测

云环境是 Linux；未在 Windows PowerShell 5.1、Windows PowerShell 7、Windows 原生文件系统或 IntelliJ IDEA 图形界面运行。Linux PowerShell 路径测试能验证脚本逻辑、参数传递和 Unicode，但不能证明所有 Windows 原生命令行差异都已消除。

在 Windows 中建议按以下顺序复核：

1. 将仓库放在 `D:\课程 项目\SocilaNetwork` 一类目录，选择已安装的完整 JDK 17+。让 `JAVA_HOME` 和 IDEA Project SDK 指向同一个安装目录。
2. 在 PowerShell 5.1 和已安装的 PowerShell 7 中分别执行 `.\scripts\run.ps1 -Verify`，核对 `PASS: 49 regression checks`；再执行 `.\scripts\run.ps1 -Port 8081`，核对启动日志与主页 11/13。
3. 从另一个目录调用 `& 'D:\课程 项目\SocilaNetwork\scripts\run.ps1' -CsvPath '.\数据 样本.csv' -Port 8081`。CSV 可复制仓库样本到调用目录；检查项目和 CSV 的中文、空格、方括号名称。按 Ctrl+C 停止后检查当前目录。
4. IDEA 以 `$PROJECT_DIR$` 为工作目录直接运行 `WebMain`；分别测试无参数和 `"D:\课程 项目\数据 样本.csv" 8081`。按 [项目说明](project-guide.md#idea-与-windows-启动检查) 核对模块 SDK 和 JRE。
5. 手动完成路径、圈子、两跳、视图切换、缩放／平移与模拟位置拖动；浏览器网络面板核对 POST `/api/move` 与后续 GET `/api/nearby`。暂停 Java 后查询，检查失败反馈；恢复后重试。
6. 若安装 Node.js 22+ 和 Chrome，设置 `BROWSER_PATH` 后执行 `node tests/browser-flow.mjs`。此项在 Windows 尚未执行；手动步骤始终可用于复核。

未执行生产部署、真实地理数据分析、社区发现、加权最短路或大规模性能测试；这些不是本次通过的功能。
