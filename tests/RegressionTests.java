import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free regression checks for algorithms, CSV input and HTTP integration. */
public class RegressionTests {
    private static int checks;

    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }

    private static boolean contains(int[] values, int expected) {
        for (int value : values) if (value == expected) return true;
        return false;
    }

    private static MatGraphClass graph() {
        Vertex[] vertices = {
            new Vertex("A&B=+%", 0, 0), new Vertex("B", 3, 4),
            new Vertex("q\"\\node", 50, 50), new Vertex("D", 60, 60),
            new Vertex("孤立", 100, 100)
        };
        return new MatGraphClass(vertices, new TupElem[] {
            new TupElem(0, 1, 10), new TupElem(1, 0, 8),
            new TupElem(1, 2, 1), new TupElem(0, 2, 20), new TupElem(2, 3, 1)
        });
    }

    private static void algorithms() {
        MatGraphClass g = graph();
        check(g.e == 8 && g.edges[0][1] == 8, "Duplicate undirected edges must merge and keep the smaller weight");
        check(PathFinder.findShortestPath(g, 0, 2)[2] == 0, "BFS minimizes hops, not summed weight");
        int[] pre = PathFinder.findShortestPath(g, 0, 3);
        check(pre[3] == 2 && pre[2] == 0, "Two-hop path must be reconstructed correctly");
        check(PathFinder.findShortestPath(g, 0, 4) == null, "Disconnected nodes have no path");
        check(PathFinder.findShortestPath(g, 0, 0)[0] == -1, "Same-node path has no predecessor");
        int[] direct = NetworkAnalyzer.findDirectCircle(g, 0);
        check(direct.length == 2 && contains(direct, 1) && contains(direct, 2), "Direct circle excludes self and duplicates");
        int[] reachable = NetworkAnalyzer.findReachableWithinHops(g, 0, 2);
        check(reachable.length == 3 && contains(reachable, 3) && !contains(reachable, 0), "Two-hop reach excludes self");
        check(NetworkAnalyzer.findReachableWithinHops(g, 0, 0).length == 0, "Zero hops reaches nobody else");
        check(NetworkAnalyzer.computeDegree(g)[0] == 2 && NetworkAnalyzer.computeDegree(g)[4] == 0, "Degrees must match topology");
        check(contains(NetworkAnalyzer.findNearbyByLocation(g, 0, 0, 5), 1), "Radius includes exact boundary");
        g.vexs[1].setX(20);
        check(!contains(NetworkAnalyzer.findNearbyByLocation(g, 0, 0, 5), 1), "Nearby query must use updated coordinates");
        try {
            PathFinder.findShortestPath(g, -1, 0);
            throw new AssertionError("Invalid index should fail");
        } catch (IllegalArgumentException expected) { checks++; }
    }

    private static void csv() throws Exception {
        Path file = Files.createTempFile("social-network-test", ".csv");
        try {
            Files.writeString(file, "source,target,weight\n 张三 ,李四,1.6\n\n李四,王五,2\n李四,张三,4\n");
            MatGraphClass first = GraphLoader.readCSV(file.toString());
            MatGraphClass second = GraphLoader.readCSV(file.toString());
            check(first.n == 3 && first.e == 4 && first.edges[0][1] == 2, "UTF-8, whitespace, blank lines, rounding and duplicate handling");
            check(first.vexs[0].getX() == second.vexs[0].getX() && first.vexs[0].getY() == second.vexs[0].getY(), "Coordinates must be reproducible");
            StringBuilder large = new StringBuilder("source,target,weight\n");
            for (int i = 0; i < 40; i++) large.append("v").append(i).append(",v").append(i + 1).append(",1\n");
            Files.writeString(file, large);
            check(GraphLoader.readCSV(file.toString()).n == 41, "Vertex and edge arrays must grow beyond initial capacity");
            String[] badRows = { "A,B,0", "A,B,-1", "A,B,0.1", "A,B,NaN", "A,B,Infinity", "A,B,9999999999999", "A,A,1", "A,,1", "A,B", "A,B,1,extra" };
            for (String row : badRows) {
                Files.writeString(file, "source,target,weight\n" + row + "\n");
                try {
                    GraphLoader.readCSV(file.toString());
                    throw new AssertionError("Malformed CSV accepted: " + row);
                } catch (IOException expected) {
                    check(expected.getMessage().contains("2"), "CSV error should identify the input line");
                }
            }
            Files.writeString(file, "source,target,weight\n");
            try { GraphLoader.readCSV(file.toString()); throw new AssertionError("Empty graph accepted"); }
            catch (IOException expected) { checks++; }
        } finally { Files.deleteIfExists(file); }
    }

    private static HttpResponse<String> request(HttpClient client, String base, String path, String method) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path))
                .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void http() throws Exception {
        MatGraphClass g = graph();
        ApiServer server = new ApiServer(0, g, NetworkAnalyzer.computeDegree(g));
        server.start();
        String base = "http://127.0.0.1:" + server.getPort();
        HttpClient client = HttpClient.newHttpClient();
        String name = URLEncoder.encode(g.vexs[0].getName(), StandardCharsets.UTF_8);
        try {
            HttpResponse<String> page = request(client, base, "/", "GET");
            check(page.statusCode() == 200 && page.body().contains("src=\"app2.js\"") && !page.body().contains("EDGE_RAW"), "Dashboard must load real Java API client");
            check(request(client, base, "/app2.js", "GET").statusCode() == 200, "API client asset must be available");
            check(request(client, base, "/offline_demo.html", "GET").statusCode() == 200, "Standalone demo remains available");
            String body = request(client, base, "/api/graph", "GET").body();
            check(body.contains("q\\\"\\\\node"), "JSON must escape quotes and backslashes");
            check(request(client, base, "/api/path?a=" + name + "&b=D", "GET").body().contains("\"intermediaries\": 1"), "Names containing query delimiters must be decoded exactly once");
            check(request(client, base, "/api/path?a=" + name + "&b=" + name, "GET").body().contains("\"intermediaries\": 0"), "Same-node path should have zero intermediaries");
            check(request(client, base, "/api/path?a=B&b=" + URLEncoder.encode("孤立", StandardCharsets.UTF_8), "GET").body().contains("\"reason\": \"no_path\""), "Disconnected nodes report no_path through HTTP");
            check(request(client, base, "/api/circle?name=" + name, "GET").body().contains("\"found\": true"), "Circle endpoint supports encoded names");
            check(request(client, base, "/api/nearby?name=" + name + "&radius=5", "GET").body().contains("\"oneIntermediary\": [\"D\"]"), "Two-hop expansion excludes direct contacts");
            check(request(client, base, "/api/classify", "GET").body().contains("\"core\""), "Classification endpoint is available");
            for (String radius : new String[] { "bad", "NaN", "Infinity", "-1" }) {
                check(request(client, base, "/api/nearby?name=" + name + "&radius=" + radius, "GET").statusCode() == 400, "Invalid radius must be a client error");
            }
            check(request(client, base, "/api/nearby?name=" + name, "GET").statusCode() == 400, "Known-node nearby query requires a radius");
            check(request(client, base, "/api/move?name=B&x=2&y=NaN", "POST").statusCode() == 400 && g.vexs[1].getX() == 3, "Invalid second coordinate must not partly update first");
            check(request(client, base, "/api/move?name=missing&x=2&y=3", "POST").statusCode() == 404, "Missing node must not report movement success");
            check(request(client, base, "/api/move?name=B&x=2&y=3", "GET").statusCode() == 405, "Moving coordinates requires POST");
            check(request(client, base, "/api/move?name=B&x=1000&y=3", "POST").statusCode() == 400, "Coordinate bounds are enforced");
            check(request(client, base, "/api/move?name=B&x=2&y=3", "POST").statusCode() == 200 && g.vexs[1].getX() == 2 && g.vexs[1].getY() == 3, "Valid movement updates Java vertices");
            check(request(client, base, "/api/graph/extra", "GET").statusCode() == 404, "API routing must match exact paths");
            check(request(client, base, "/.git/config", "GET").statusCode() == 404, "Static server must not serve repository internals");
            check(request(client, base, "/src/Main.java", "GET").statusCode() == 404, "Static server only serves dashboard assets");
        } finally { server.stop(); }
    }

    public static void main(String[] args) throws Exception {
        algorithms();
        csv();
        http();
        System.out.println("PASS: " + checks + " regression checks");
    }
}
