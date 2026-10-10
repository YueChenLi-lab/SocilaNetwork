import java.io.IOException;

/** Start from the repository root: WebMain [csv-path] [port]. */
public class WebMain {
    public static void main(String[] args) {
        try {
            String fileName = args.length > 0 ? args[0] : "data/sample_edges.csv";
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
            MatGraphClass graph = GraphLoader.readCSV(fileName);
            ApiServer server = new ApiServer(port, graph, NetworkAnalyzer.computeDegree(graph));
            server.start();
            System.out.println("SOCIAL·NET 服务器已启动！");
            System.out.println("数据：" + fileName + "；节点：" + graph.n + "；无向关系：" + graph.e / 2);
            System.out.println("坐标为可复现的模拟值，拖动修改仅保存在本次运行内。");
            System.out.println("请在浏览器中访问: http://localhost:" + server.getPort() + "/");
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("启动失败：" + e.getMessage());
            System.err.println("用法：java -cp out WebMain [CSV路径] [端口]（在项目根目录运行）");
            System.exit(1);
        }
    }
}
