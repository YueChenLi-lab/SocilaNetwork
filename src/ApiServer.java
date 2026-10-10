import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URLDecoder;

public class ApiServer {
    private HttpServer server;
    private MatGraphClass graph;
    private int[] degrees;
    private String[] nodeClasses; // 缓存每个节点的分类（core/active/edge）

    public ApiServer(int port, MatGraphClass graph, int[] degrees) throws IOException {
        this.graph = graph;
        this.degrees = degrees;

        // 1. 初始化并计算出所有节点的分类（需求5和图初始化需要）
        this.nodeClasses = new String[graph.n];
        int[] sortedIndex = NetworkAnalyzer.sortIndexByDegreeDesc(degrees);
        int coreEnd = (int) Math.ceil(graph.n * 0.2);
        int activeEnd = (int) Math.ceil(graph.n * 0.7);
        for (int i = 0; i < coreEnd; i++) nodeClasses[sortedIndex[i]] = "core";
        for (int i = coreEnd; i < activeEnd; i++) nodeClasses[sortedIndex[i]] = "active";
        for (int i = activeEnd; i < graph.n; i++) nodeClasses[sortedIndex[i]] = "edge";

        // 2. 创建 HTTP 服务器
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);

        // 3. 绑定所有路由
        this.server.createContext("/", new StaticFileHandler());
        register("/api/graph", new GraphApiHandler());       // 需求(1)(2)：全量图数据
        register("/api/path", new PathApiHandler());         // 需求(3)：最短路径
        register("/api/circle", new CircleApiHandler());     // 需求(6)：直接交往圈子
        register("/api/nearby", new NearbyApiHandler());     // 需求(4)：附近信号及一跳可达
        register("/api/classify", new ClassifyApiHandler()); // 需求(5)：人物分类统计
        register("/api/move", new MoveApiHandler());         // 坐标移动更新

        this.server.setExecutor(null);
    }

    public void start() {
        server.start();
    }

    public int getPort() { return server.getAddress().getPort(); }

    public void stop() { server.stop(0); }

    // Validate a request before invoking its route, and return useful client errors.
    private void register(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            if (!path.equals(exchange.getRequestURI().getPath())) {
                sendJson(exchange, 404, "{\"error\":\"not_found\"}");
                return;
            }
            String method = path.equals("/api/move") ? "POST" : "GET";
            if (!method.equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", method);
                sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }
            try {
                handler.handle(exchange);
            } catch (IllegalArgumentException ex) {
                sendJson(exchange, 400, "{\"error\":\"invalid_parameter\",\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        });
    }

    private double finiteNumber(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " is required");
        double result = Double.parseDouble(value);
        if (!Double.isFinite(result)) throw new IllegalArgumentException(name + " must be finite");
        return result;
    }

    private double coordinate(String value, String name) {
        double result = finiteNumber(value, name);
        if (result < 0 || result > 100) throw new IllegalArgumentException(name + " must be in [0, 100]");
        return result;
    }

    private String escapeJson(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\"' || ch == '\\') result.append('\\').append(ch);
            else if (ch < 0x20) {
                result.append("\\u");
                String hex = Integer.toHexString(ch);
                for (int j = hex.length(); j < 4; j++) result.append('0');
                result.append(hex);
            } else result.append(ch);
        }
        return result.toString();
    }

    // =========================================================
    // 工具方法：解析 URL 里的 Query 参数 (如 a=1&b=2)
    // =========================================================
    private String getParam(String query, String key) {
        if (query == null) return null;
        String[] pairs = query.split("&");
        for (int i = 0; i < pairs.length; i++) {
            String[] kv = pairs[i].split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                try {
                    return URLDecoder.decode(kv[1], "UTF-8");
                } catch (java.io.UnsupportedEncodingException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return null;
    }

    // =========================================================
    // 工具方法：统一发送 JSON 响应
    // =========================================================
    private void sendJson(HttpExchange exchange, String json) throws IOException {
        sendJson(exchange, 200, json);
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        byte[] bytes = json.getBytes("UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    // =========================================================
    // 0. 静态页面服务
    // =========================================================
    class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/social_network_dashboard.html";
            // Only serve dashboard assets, never the repository's source or .git files.
            if (!path.equals("/social_network_dashboard.html") && !path.equals("/app2.js") && !path.equals("/offline_demo.html")) {
                sendJson(exchange, 404, "{\"error\":\"not_found\"}");
                return;
            }
            Path file = Paths.get(path.substring(1));
            if (!Files.isRegularFile(file)) {
                sendJson(exchange, 404, "{\"error\":\"not_found\"}");
                return;
            }
            byte[] fileBytes = Files.readAllBytes(file);
            if (path.endsWith(".js")) exchange.getResponseHeaders().set("Content-Type", "application/javascript; charset=UTF-8");
            else exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, fileBytes.length);
            exchange.getResponseBody().write(fileBytes);
            exchange.getResponseBody().close();
        }
    }

    // =========================================================
    // 1. 获取整张图数据：/api/graph
    // =========================================================
    class GraphApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"nodes\":[");
            for (int i = 0; i < graph.n; i++) {
                sb.append("{\"id\":").append(i)
                        .append(",\"name\":\"").append(escapeJson(graph.vexs[i].getName())).append("\"")
                        .append(",\"degree\":").append(degrees[i])
                        .append(",\"cls\":\"").append(nodeClasses[i]).append("\"")
                        .append(",\"x\":").append(graph.vexs[i].getX())
                        .append(",\"y\":").append(graph.vexs[i].getY())
                        .append("}");
                if (i < graph.n - 1) sb.append(",");
            }
            sb.append("],\"edges\":[");
            boolean firstEdge = true;
            for (int i = 0; i < graph.n; i++) {
                for (int j = i + 1; j < graph.n; j++) {
                    if (graph.edges[i][j] != 0 && graph.edges[i][j] != graph.INF) {
                        if (!firstEdge) sb.append(",");
                        sb.append("{\"a\":").append(i).append(",\"b\":").append(j).append("}");
                        firstEdge = false;
                    }
                }
            }
            sb.append("]}");
            sendJson(exchange, sb.toString());
        }
    }

    // =========================================================
    // 2. 最短路径查询：/api/path?a=xx&b=yy
    // =========================================================
    class PathApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getRawQuery();
            String nameA = getParam(query, "a");
            String nameB = getParam(query, "b");

            int idxA = graph.findIndexByName(nameA);
            int idxB = graph.findIndexByName(nameB);

            if (idxA == -1 || idxB == -1) {
                sendJson(exchange, "{\"found\": false, \"reason\": \"name_not_found\"}");
                return;
            }

            int[] pre = PathFinder.findShortestPath(graph, idxA, idxB);
            if (pre == null) {
                sendJson(exchange, "{\"found\": false, \"reason\": \"no_path\"}");
                return;
            }

            // 利用栈还原路径顺序
            int[] stack = new int[graph.n];
            int top = -1;
            int cur = idxB;
            while (cur != -1) {
                stack[++top] = cur;
                cur = pre[cur];
            }

            int nodeCount = top + 1;
            int intermediaries = Math.max(0, nodeCount - 2);

            StringBuilder sb = new StringBuilder();
            sb.append("{\"found\": true, \"intermediaries\": ").append(intermediaries).append(", \"path\": [");
            boolean first = true;
            while (top >= 0) {
                if (!first) sb.append(", ");
                sb.append("\"").append(escapeJson(graph.vexs[stack[top--]].getName())).append("\"");
                first = false;
            }
            sb.append("]}");
            sendJson(exchange, sb.toString());
        }
    }

    // =========================================================
    // 3. 交往圈子：/api/circle?name=xx
    // =========================================================
    class CircleApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String targetName = getParam(exchange.getRequestURI().getRawQuery(), "name");
            int idx = graph.findIndexByName(targetName);

            if (idx == -1) {
                sendJson(exchange, "{\"found\": false}");
                return;
            }

            int[] circle = NetworkAnalyzer.findDirectCircle(graph, idx);
            StringBuilder sb = new StringBuilder();
            sb.append("{\"found\": true, \"circle\": [");
            for (int i = 0; i < circle.length; i++) {
                sb.append("\"").append(escapeJson(graph.vexs[circle[i]].getName())).append("\"");
                if (i < circle.length - 1) sb.append(", ");
            }
            sb.append("]}");
            sendJson(exchange, sb.toString());
        }
    }

    // =========================================================
    // 4. 附近信号与一跳可达：/api/nearby?name=xx&radius=xx
    // =========================================================
    class NearbyApiHandler implements HttpHandler {

        // 内部辅助方法，复现 NetworkAnalyzer 里的 contains 判断
        private boolean containsValue(int[] arr, int value) {
            for (int i = 0; i < arr.length; i++) {
                if (arr[i] == value) return true;
            }
            return false;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getRawQuery();
            String name = getParam(query, "name");
            String radStr = getParam(query, "radius");

            int idx = graph.findIndexByName(name);
            if (idx == -1 || radStr == null) {
                sendJson(exchange, "{\"found\": false}");
                return;
            }

            double radius = finiteNumber(radStr, "radius");
            if (radius < 0) throw new IllegalArgumentException("radius must be nonnegative");
            double x = graph.vexs[idx].getX();
            double y = graph.vexs[idx].getY();

            int[] nearby = NetworkAnalyzer.findNearbyByLocation(graph, x, y, radius);
            int[] direct = NetworkAnalyzer.findDirectCircle(graph, idx);
            int[] reachable2 = NetworkAnalyzer.findReachableWithinHops(graph, idx, 2);

            StringBuilder sb = new StringBuilder();
            sb.append("{\"found\": true, \"nearby\": [");
            // 附近的人（去除自己）
            boolean firstN = true;
            for (int i = 0; i < nearby.length; i++) {
                if (nearby[i] == idx) continue;
                if (!firstN) sb.append(", ");
                sb.append("\"").append(escapeJson(graph.vexs[nearby[i]].getName())).append("\"");
                firstN = false;
            }

            sb.append("], \"oneIntermediary\": [");
            // 经过一个中间人可达的人（2跳到达，且排除直接联系人）
            boolean firstO = true;
            for (int i = 0; i < reachable2.length; i++) {
                int targetIdx = reachable2[i];
                if (!containsValue(direct, targetIdx)) {
                    if (!firstO) sb.append(", ");
                    sb.append("\"").append(escapeJson(graph.vexs[targetIdx].getName())).append("\"");
                    firstO = false;
                }
            }
            sb.append("]}");

            sendJson(exchange, sb.toString());
        }
    }

    // =========================================================
    // 5. 核心/活跃/边缘人物统计：/api/classify
    // =========================================================
    class ClassifyApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            int[] sortedIndex = NetworkAnalyzer.sortIndexByDegreeDesc(degrees);
            int coreEnd = (int) Math.ceil(graph.n * 0.2);
            int activeEnd = (int) Math.ceil(graph.n * 0.7);

            StringBuilder sb = new StringBuilder();
            sb.append("{\"core\": [");
            for (int i = 0; i < coreEnd; i++) {
                sb.append("{\"name\": \"").append(escapeJson(graph.vexs[sortedIndex[i]].getName())).append("\"}");
                if (i < coreEnd - 1) sb.append(",");
            }
            sb.append("], \"active\": [");
            for (int i = coreEnd; i < activeEnd; i++) {
                sb.append("{\"name\": \"").append(escapeJson(graph.vexs[sortedIndex[i]].getName())).append("\"}");
                if (i < activeEnd - 1) sb.append(",");
            }
            sb.append("], \"edge\": [");
            for (int i = activeEnd; i < graph.n; i++) {
                sb.append("{\"name\": \"").append(escapeJson(graph.vexs[sortedIndex[i]].getName())).append("\"}");
                if (i < graph.n - 1) sb.append(",");
            }
            sb.append("]}");

            sendJson(exchange, sb.toString());
        }
    }

    // =========================================================
    // 6. 坐标移动同步：/api/move?name=xx&x=10&y=20
    // =========================================================
    class MoveApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getRawQuery();
            String name = getParam(query, "name");
            String xStr = getParam(query, "x");
            String yStr = getParam(query, "y");

            int idx = graph.findIndexByName(name);
            if (idx == -1) {
                sendJson(exchange, 404, "{\"success\":false,\"reason\":\"name_not_found\"}");
                return;
            }
            // Validate both values before changing either coordinate.
            double x = coordinate(xStr, "x");
            double y = coordinate(yStr, "y");
            graph.vexs[idx].setX(x);
            graph.vexs[idx].setY(y);
            sendJson(exchange, "{\"success\": true}");
        }
    }
}