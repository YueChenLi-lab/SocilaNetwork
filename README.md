# SOCIAL·NET：关系网络分析与可视化

用 Java 从 CSV 构建无向关系图，以浏览器界面展示**最少中间人路径、直接交往圈、两跳关系扩展、度数排名和动态位置查询**。

这是一个适合数据结构课程展示与进一步改进的分析原型：核心算法采用普通数组、邻接矩阵和手写 BFS 队列；界面提供拓扑／模拟位置两种视图，查询由 Java 后端计算。

> 仓库名保留为 `SocilaNetwork`。示例中的节点是技术标签，用于模拟关系网络，不是实际用户。位置为模拟坐标，“核心／活跃／边缘”是度数排名分组，不代表真实影响力或社区识别。

## 项目优势：从算法到交互演示

| 能力 | 实现与价值 | 如何验证 |
| --- | --- | --- |
| 数据结构可解释 | 数组手动扩容、邻接矩阵、队列与前驱数组直接实现，便于追踪算法执行 | 在 `PathFinder.findShortestPath` 中对 `front/rear/pre` 下断点；对照 [数组与 BFS 讲解](docs/project-guide.md#算法如何讲清楚) |
| 最少转介路径 | BFS 找到最少边数的路径，同时展示路径节点和中间人数 | 查询 `photoshop → jquery`，中间人为 `css` |
| 关系扩展 | 区分直接联系人与经一个中间人可达的新增节点，避免重复算入已有联系人 | 选中 `photoshop`：直接联系 `css`；两跳新增 `jquery`、`html` |
| 拓扑与位置分开 | 力导向布局帮助观察关系；位置视图根据模拟坐标进行半径查询 | 拓扑拖动不会发出 `/api/move`；模拟位置拖动松开后发送 POST，附近列表刷新，关系边与度数不变 |
| Java 与网页连通 | 同源 HTTP 服务提供六类 API；前端负责交互和渲染，后端负责分析 | 浏览器网络面板检查 `/api/path`、`/api/nearby` |
| 易于复现 | JDK 自带 HTTP 服务，无第三方 Java 依赖；附启动脚本、示例数据和回归检查 | 同一示例重复启动可核对 11 节点、13 关系、`photoshop → css → jquery`、3/5/3 分组及相同初始模拟坐标 |

## 快速启动

需要 **完整 JDK 17 或更新版本**（包含 `jdk.compiler`，仅 JRE 不够）。脚本优先使用 `JAVA_HOME`，未设置时使用 PATH 中的 `java`；两种脚本都按 Java 17 目标编译。端口默认 `8080`。运行应用无需 Maven、Gradle 或 npm。

### Windows PowerShell

在项目根目录执行：

```powershell
.\scripts\run.ps1
```

若 PATH 中是旧 Java 或 JRE，先设置正确的 JDK 安装目录；路径含空格时保留引号（请替换为本机实际目录）：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17'
.\scripts\run.ps1 -Verify
.\scripts\run.ps1
```

不要把 `JAVA_HOME` 指向 `bin`。若执行策略阻止本地脚本，可在遵循本机策略的前提下使用本次进程设置：`powershell -NoProfile -ExecutionPolicy RemoteSigned -File .\scripts\run.ps1`。

### Linux / macOS

```bash
bash scripts/run.sh
```

访问 **http://localhost:8080/**。停止服务：终端按 `Ctrl+C`。

使用自己的数据和其他端口：

```powershell
.\scripts\run.ps1 -CsvPath "D:\课程 项目\数据 样本.csv" -Port 8081
```

```bash
bash scripts/run.sh "/path/with spaces/数据 样本.csv" 8081
```

显式传入的相对 CSV 路径按**调用者当前目录**解析；未传入时始终使用仓库示例。因此可从其他目录调用脚本：

```powershell
& 'D:\课程 项目\SocilaNetwork\scripts\run.ps1' -CsvPath '.\数据 样本.csv' -Port 8081
```

IntelliJ IDEA：打开项目根目录，在 **Project Structure → Project SDK** 选择本机完整 JDK 17+，语言级别选 17，模块 SDK 继承项目 SDK。共享配置已从 JDK 25 调整为 17；SDK 名称只是 IDEA 的本机引用，不会安装 JDK。运行 `src/WebMain.java`，Working directory 填 `$PROJECT_DIR$`（此字段不加引号），JRE 选 Project SDK。Program arguments 留空使用示例，或填写 `"D:\课程 项目\数据 样本.csv" 8081`。完整排查步骤见 [IDEA 与 Windows 检查](docs/project-guide.md#idea-与-windows-启动检查)。

`JAVA_HOME` 控制脚本，IDEA 的 Project SDK/JRE 控制 IDE 运行；两处请选同一个安装目录。

### 两种展示入口

- **后端分析版**：通过 Java 服务访问 `/`，使用 `app2.js` 请求 Java API。
- **离线前端演示**：直接打开 `offline_demo.html`，保留原有内嵌关系数据和前端算法，**不连接 Java**。也可通过服务访问 `/offline_demo.html`。它的内嵌数据与 Java 示例不是同一数据集，不应逐项比较结果。

Google Fonts 为可选外部字体；无法联网时使用系统字体，不影响本地 Java 分析。

## 数据格式与分析口径

`data/sample_edges.csv` 是本仓库人工构造的 **11 节点、13 条无向关系**小样本，便于复现路径与圈子结果，不是公开数据集的完整副本。

```csv
source,target,weight
jquery,css,1
css,photoshop,1
```

- 文件须为 UTF-8，首行是三列表头，每行包含两个非空节点名和一个权重。端点会去除首尾空白，空行会跳过。
- 当前是简单逗号分隔格式，不支持带逗号或换行的节点名、引号包裹字段。自环和空数据会报告错误。
- 权重须为有限正数，四舍五入后的整数须在 `[1, 0x3f3f3f3f)` 内。重复或反向重复关系合并，保留较小的整数权重。
- 图按无向图处理；`e` 是邻接矩阵内的双向记录数，展示的无向关系数为 `e / 2`。
- **最短路径指最少边数，不是最小权重总和。** 中间人数为 `max(0, 路径节点数 − 2)`。目前没有 Dijkstra 加权最短路。
- 两跳扩展排除本人和直接联系人；它是可达性分析，不是推荐模型。
- 分类按度数降序排名：前 `ceil(20% × n)` 个为核心，到 `ceil(70% × n)` 为活跃，其余为边缘。小样本的比例会受取整影响，同度节点可能被分在不同组。
- 坐标根据节点名生成可复现的 `[0, 100]` 模拟值。位置移动仅更新本次运行中的 Java 内存，重启后恢复初始模拟值。

## 结构

```text
src/GraphLoader.java       CSV校验、数组扩容、模拟坐标
src/MatGraphClass.java     邻接矩阵、无向重复边合并
src/PathFinder.java        BFS、前驱数组、路径还原
src/NetworkAnalyzer.java   圈子、跳数范围、位置查询、度数分类
src/ApiServer.java         HTTP API、静态页面服务
src/WebMain.java           网页版启动入口
src/Main.java              控制台交互入口
src/TestCase456.java       圈子、分类、位置移动演示
app2.js                   后端分析版的交互与SVG渲染
social_network_dashboard.html  后端分析版页面
offline_demo.html         原有离线前端演示
data/sample_edges.csv     可复现的小样本
tests/RegressionTests.java 算法、输入和HTTP回归检查
```

## 验证

```powershell
.\scripts\run.ps1 -Verify
```

```bash
bash scripts/run.sh --verify
```

检查覆盖最少跳数与加权路径的区别、断连／同节点路径、两跳排除直接联系人、重复边合并、数组扩容、UTF-8 和异常 CSV、带特殊字符的 API 节点名、非法半径、移动原子校验，以及主页是否接入真实 Java API。

可选的真实浏览器回归需要 Node.js 22+ 和 Chromium/Chrome，无需 npm 安装。先运行上面的 `-Verify` / `--verify` 编译 Java，再执行：

```bash
node tests/browser-flow.mjs
```

Linux 默认查找 `chromium`；Windows 请指定浏览器实际路径：

```powershell
$env:BROWSER_PATH = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
node tests/browser-flow.mjs
```

此检查自行启动临时 Java 服务和浏览器，验证真实鼠标／键盘交互、六类 Java API、延迟查询、保存失败与重试；退出后停止它启动的进程。截图和结果写入忽略的 `out/browser/`。最新实测环境、结果与 Windows 限制见 [验证记录](docs/verification.md)。

已有控制台演示可在编译后运行：

```bash
java -cp out Main
java -cp out TestCase456
```

完整演示步骤、API 参数和后续方向见 [项目说明](docs/project-guide.md)。

## 同步回已有 IDEA 项目

先在本地项目目录执行 `git status`，将未提交改动提交或暂存（需要包含未跟踪文件时使用 `git stash push -u`），再获取后续分支：

```bash
git fetch origin
git switch --track origin/codex/project-showcase-followup
```

如果本地已有该分支，用 `git switch codex/project-showcase-followup`，随后 `git pull --ff-only`。后续 PR 合并后可改用 `git switch main` 和 `git pull --ff-only`。不要把远端旧的 `codex/project-showcase` 当作本次修复分支。

回到 IDEA 重新加载外部修改，核对 Project SDK/JRE 和 `$PROJECT_DIR$` 工作目录，再执行脚本回归或运行 `WebMain`。若此前暂存了改动，恢复前先检查是否与这些文件冲突，保留本地修改。

## 当前边界

邻接矩阵占用 `O(V²)` 内存，BFS 扫描矩阵最坏为 `O(V²)`，CSV 端点线性查找为 `O(EV)`；度数排序也为 `O(V²)`。这是中小规模教学与演示原型，尚无大规模数据性能基准。

服务默认仅监听本机。当前没有用户登录、数据库持久化、生产部署或多用户并发协作机制。若后续面向更大网络，优先评估邻接表、节点索引和空间索引，并以实际测试衡量收益。
