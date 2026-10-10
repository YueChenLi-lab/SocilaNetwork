
//Web 服务的启动入口
import java.io.IOException;

public class WebMain {
    public static void main(String[] args) {
        try {
            // 1. 初始化你的图数据 (保留你原来的逻辑)
            String fileName = "D:/Desktop/stackoverflow_edges.csv";
            MatGraphClass graph = GraphLoader.readCSV(fileName);

            // 2. 提前计算度数分类 (供前端需求5使用)
            int[] degrees = NetworkAnalyzer.computeDegree(graph);

            // 3. 启动 API 服务器，监听 8080 端口
            ApiServer server = new ApiServer(8080, graph, degrees);
            server.start();

            System.out.println("SOCIAL·NET 服务器已启动！");
            System.out.println("请在浏览器中访问: http://localhost:8080/");

        } catch (IOException e) {
            System.out.println("数据加载失败或端口被占用：" + e.getMessage());
        }
    }
}