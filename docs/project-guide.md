# 项目说明与展示路线

## 可以怎样介绍这个项目

“我做的是一个关系网络分析与可视化原型。先用 Java 把 CSV 关系数据建成无向图，再通过 BFS 和邻接矩阵分析最少中间人路径、直接圈子、两跳扩展与度数排名。浏览器既能查看关系结构，也能调整模拟位置并重新查询附近节点。项目把数据结构算法、HTTP 接口和交互界面连成了一条可以实际演示的流程。”

展示价值在于：算法过程可以解释、查询结果可以核对、交互行为能够调用真实后端。技术标签只是图节点的一种输入；如果替换为经授权的人员关系数据，算法仍按节点和关系工作，但业务解释需要随数据变化。

## 三分钟演示

使用 `data/sample_edges.csv` 启动 Java 服务后：

| 步骤 | 操作 | 可以讲的内容与预期 |
| --- | --- | --- |
| 1. 建图 | 打开主页 | 11 个节点、13 条无向关系；所有分析数据从 Java API 获取 |
| 2. 最短联系路径 | 输入 `photoshop` 和 `jquery` | 路径 `photoshop → css → jquery`，只需 1 个中间人；讲解 BFS 按层搜索 |
| 3. 两跳扩展 | 点击 `photoshop` | 直接联系人只有 `css`，经过一个中间人新增可达 `jquery`、`html`，不把 `css` 重复计入 |
| 4. 人物分类 | 查看分类面板 | 核心 3、活跃 5、边缘 3；依据度数排名和向上取整，而非社区发现 |
| 5. 位置变化 | 选中 `jquery`，切到“模拟位置”，将 `drupal` 拖到它附近，松开后查看保存反馈 | 半径 15 的附近列表出现 `drupal`，直接圈子、度数及路径不变；网络面板可观察 POST `/api/move` 后的 GET `/api/nearby` |
| 6. 视图操作 | 点击放大／缩小，滚轮缩放，拖动画布空白处平移，再切回拓扑 | 缩放数值立即更新；拓扑拖动只改变显示排列，不修改 Java 中的模拟坐标 |
| 7. 可复现 | 重启 Java 后刷新页面，再查同一条路径 | 初始模拟坐标和分析结果恢复；浏览器重新刷新本身不会清除服务器内存中的已保存移动 |

对比 `jquery` 的 5 个直接联系人与 `photoshop` 的 1 个直接联系人，可以解释“关系结构不同，直接与间接触达范围也不同”。不能据此声称系统预测了真实影响力。

## 请求链路

1. `WebMain` 加载 CSV，`GraphLoader` 建立顶点／边数组，`MatGraphClass` 构建邻接矩阵。
2. `ApiServer` 启动本机 HTTP 服务并计算度数分组。
3. 主页加载 `app2.js`，调用 `/api/graph` 取得节点、边与模拟坐标。
4. 用户发起查询时，前端请求相应 API；Java 调用 `PathFinder` 或 `NetworkAnalyzer` 后返回 JSON。
5. SVG 和侧边面板渲染结果；位置视图的拖动通过 POST 更新 Java 顶点，后续查询使用更新值。

力导向布局只调整视觉排列，不能解释为真实空间坐标。位置视图的范围以同一模拟坐标系计算；因横纵映射比例不同，范围在 SVG 中显示为椭圆。模拟空间距离没有公里、米或 GPS 含义。

## 算法如何讲清楚

以 `photoshop → jquery` 为例，BFS 从 `photoshop` 入队，第一层访问 `css`，第二层访问 `jquery`；`pre[jquery] = css`、`pre[css] = photoshop`。按前驱倒序入栈再输出即可得到路径。两条边意味着一个中间人，而非把三个人都计为中间人。

| 展示重点 | 代码依据 | 可核对操作 |
| --- | --- | --- |
| 手写数据结构 | `GraphLoader` 的 `findVertexIndex/growVertices/growEdges`、`MatGraphClass.edges`、`PathFinder` 的 `queue/front/rear/pre` | IDEA 对队列入队与 `pre[w] = v` 下断点，沿上述两层路径观察数组变化 |
| 两跳扩展 | `NetworkAnalyzer.findReachableWithinHops(..., 2)`，`ApiServer.NearbyApiHandler` 排除直接圈子 | `photoshop` 直接圈子为 `css`，新增两跳节点为 `jquery`、`html`，不含本人和 `css` |
| 位置与拓扑分离 | `app2.js` 中 `mode/geoToSvg/startNodeDrag` 与 `/api/move`；`Vertex` 坐标不参与 BFS | 移动 `drupal` 前后对比 `/api/path`、`/api/graph` 中的边与 degree，只改变位置及附近结果 |
| Java/网页贯通 | `loadGraph`、路径／圈子／附近／分类 API 调用；`ApiServer` 调用 `PathFinder/NetworkAnalyzer` | 网络面板看真实请求；暂停 Java 后查询应出现失败反馈，不会伪造本地后端结果 |
| 结果可复现 | `GraphLoader.createVertex` 按节点名生成模拟坐标；`data/sample_edges.csv` 与 `RegressionTests` | 两次启动默认样本比较坐标与 11/13、3/5/3、路径和圈子；同最少跳数的多条路径依顶点顺序取其中一条 |

BFS 的矩阵实现扫描每个出队顶点的一整行，最坏 `O(V²)`。权重虽保存在矩阵中，BFS 只判断是否有边；例如一条权重很大的直接边仍比两条低权重边有更少跳数。这个区别在 `RegressionTests.algorithms` 中有明确断言。

## 交互反馈与可核对状态

- 输入起点／终点可按 Enter 查询；空输入和未知节点都有提示，同节点为 0 个中间人。
- 改输入、选其他节点或按 Escape 会作废尚未返回的旧路径查询。圈子和附近查询也检查请求序号，旧结果及旧失败不会覆盖当前选择。
- 模拟位置在拖动时只预览，松开后保存；同一个节点保存期间暂不接受新的拖动，避免写入乱序。保存成功才刷新附近列表。
- 保存失败会回退到上次确认的显示坐标并提示重试。若服务器已收到写入但响应丢失，页面无法断言服务器未修改；刷新页面重新读取 `/api/graph` 才能核对实际状态。
- “刷新附近列表 / 重试”会重新查询当前节点与半径。初次加载失败时提供遮罩和重试按钮，查询失败在对应面板显示；请求超时为 10 秒。
- 模拟位置仅保存在 Java 进程内存中；重启恢复初始值，页面刷新不会重启服务器。未实现保存到 CSV 或数据库。

## IDEA 与 Windows 启动检查

1. 在 IDEA 中打开含 `src`、`data`、`scripts` 的根目录，而非仅打开 `src`。Project SDK 选择完整 JDK 17+，语言级别选择 17；Modules → Dependencies 使用 Project SDK，`src` 应为 Sources Root。
2. 为 `WebMain` 创建 Application 运行配置，Main class 为 `WebMain`，模块为 `SocilaNetwork`，JRE 为 Project SDK。Working directory 填 `$PROJECT_DIR$`，无需给该字段额外添加引号。
3. Program arguments 留空运行内置样本；自定义数据按 `"D:\课程 项目\数据 样本.csv" 8081` 填写。脚本和 IDEA 使用不同的 JDK 选择机制；让 `JAVA_HOME` 与 Project SDK 指向同一安装目录。
4. 在 PowerShell 中运行 `& "$env:JAVA_HOME\bin\java.exe" -version`，确认选择的是 17+。`run.ps1` 用 JDK 编译模块编译，兼容 `javac` 未在 PATH 中的完整 JDK；仅 JRE 会在编译时失败。
5. 执行 `.\scripts\run.ps1 -Verify`，随后 `.\scripts\run.ps1`；检查启动日志显示 11 节点、13 关系，并在浏览器完成前述演示。若端口已占用，使用 `-Port 8081`，打开实际日志中的端口。
6. 将仓库放在带中文与空格的路径，例如 `D:\课程 项目\SocilaNetwork`；从另一个目录执行 `& 'D:\课程 项目\SocilaNetwork\scripts\run.ps1' -CsvPath '.\数据 样本.csv' -Port 8081`。显式相对 CSV 路径按调用者目录解析；默认数据按项目根目录解析。程序结束后 PowerShell 当前目录应恢复。

本次云端对 PowerShell 7/Linux 做了启动与中文、空格、方括号路径实测；Windows PowerShell 5.1/Windows 文件系统和 IDEA 图形界面仍需按上述步骤复核，不能视为已实测通过。更多证据见 [验证记录](verification.md)。

## API

所有节点名都要进行 URL 编码。只读查询使用 GET，位置修改使用 POST。JSON 为 UTF-8。

| 方法 | 路径 | 参数 | 主要返回字段 |
| --- | --- | --- | --- |
| GET | `/api/graph` | 无 | `nodes`：id/name/degree/cls/x/y；`edges`：a/b |
| GET | `/api/path` | `a`、`b`：节点名 | `found`、`intermediaries`、`path`，失败时有 `reason` |
| GET | `/api/circle` | `name` | `found`、`circle` |
| GET | `/api/nearby` | `name`、`radius`：有限非负数 | `found`、`nearby`、`oneIntermediary` |
| GET | `/api/classify` | 无 | `core`、`active`、`edge` 三组节点名 |
| POST | `/api/move` | URL查询参数 `name`、`x`、`y`；坐标范围 `[0,100]` | `success`；未知节点为 404，非法坐标为 400 |

节点查询中不存在的名字保留原有 `found:false` 行为。已知节点的非法数值参数返回 400；方法不匹配为 405，未定义路径为 404。移动必须先校验两个坐标，再一起更新，不会因为第二个参数失败而只改第一个。

## 已实现、局限与计划

| 状态 | 内容 |
| --- | --- |
| 已实现 | CSV 建图、BFS 最少跳数、路径还原、圈子与两跳范围、度数分组、模拟空间查询、Java API、SVG 交互 |
| 本次补齐 | 真浏览器回归、旧请求作废、移动保存／失败回退与重试、同步缩放状态、无旧动画的视图切换、JAVA_HOME 与 Java 17 编译目标、路径与 IDEA 说明 |
| 尚未实现 | 加权最短路径、社区发现、真实位置导入、位置持久化、身份与权限、多用户协作、大规模性能验证 |
| 优先改进方向 | 用邻接表与节点索引降低稀疏图开销；增加真实坐标输入与持久化；为每项性能优化先建立对照数据与指标 |

原有离线页面的关系数据来源未随仓库附带说明。本次没有给它补写未经核实的来源，也没有把它当作真实用户社交数据。
