# SOCIAL·NET：关系网络分析与可视化

用 Java 从 CSV 构建无向关系图，以浏览器界面展示**最少中间人路径、直接交往圈、两跳关系扩展、度数排名和动态位置查询**。

这是一个适合数据结构课程展示与进一步改进的分析原型：核心算法采用普通数组、邻接矩阵和手写 BFS 队列；界面提供拓扑／模拟位置两种视图，查询由 Java 后端计算。

> 仓库名保留为 `SocilaNetwork`。示例中的节点是技术标签，用于模拟关系网络，不是实际用户。位置为模拟坐标，“核心／活跃／边缘”是度数排名分组，不代表真实影响力或社区识别。

## 项目优势：从算法到交互演示

| 能力 | 实现与价值 | 如何验证 |
| --- | --- | --- |
| 数据结构可解释 | 数组手动扩容、邻接矩阵、队列与前驱数组直接实现，便于追踪算法执行 | 阅读 `GraphLoader`、`MatGraphClass`、`PathFinder` |
| 最少转介路径 | BFS 找到最少边数的路径，同时展示路径节点和中间人数 | 查询 `photoshop → jquery`，中间人为 `css` |
| 关系扩展 | 区分直接联系人与经一个中间人可达的新增节点，避免重复算入已有联系人 | 选中 `photoshop`：直接联系 `css`；两跳新增 `jquery`、`html` |
| 拓扑与位置分开 | 力导向布局帮助观察关系；位置视图根据模拟坐标进行半径查询 | 拖动位置后重新查询附近节点，关系边保持不变 |
| Java 与网页连通 | 同源 HTTP 服务提供六类 API；前端负责交互和渲染，后端负责分析 | 浏览器网络面板检查 `/api/path`、`/api/nearby` |
| 易于复现 | JDK 自带 HTTP 服务，无第三方 Java 依赖；附启动脚本、示例数据和回归检查 | 克隆后运行脚本，无需寻找本机桌面上的 CSV |

## 快速启动

需要 **JDK 17 或更新版本**，并确保 `java` 在 PATH 中。端口默认 `8080`。无需 Maven、Gradle 或 npm。

### Windows PowerShell

在项目根目录执行：

```powershell
.\scripts\run.ps1
```

### Linux / macOS

```bash
bash scripts/run.sh
```

访问 **http://localhost:8080/**。停止服务：终端按 `Ctrl+C`。

使用自己的数据和其他端口：

```powershell
.\scripts\run.ps1 -CsvPath "D:\Desktop\stackoverflow_edges.csv" -Port 8081
```

```bash
bash scripts/run.sh /path/to/edges.csv 8081
```

在 IntelliJ IDEA 中，可直接运行 `src/WebMain.java`，工作目录设为项目根目录，程序参数填写 `[CSV路径] [端口]`。不填参数则使用仓库示例。IDEA 项目原有 JDK 配置为 25；本项目源码在 JDK 17 上通过验证，可在本机项目设置中选择已安装的 JDK。

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

已有控制台演示可在编译后运行：

```bash
java -cp out Main
java -cp out TestCase456
```

完整演示步骤、API 参数和后续方向见 [项目说明](docs/project-guide.md)。

## 当前边界

邻接矩阵占用 `O(V²)` 内存，BFS 扫描矩阵最坏为 `O(V²)`，CSV 端点线性查找为 `O(EV)`；度数排序也为 `O(V²)`。这是中小规模教学与演示原型，尚无大规模数据性能基准。

服务默认仅监听本机。当前没有用户登录、数据库持久化、生产部署或多用户并发协作机制。若后续面向更大网络，优先评估邻接表、节点索引和空间索引，并以实际测试衡量收益。
