import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** UTF-8 edge-list loader using arrays and manual capacity growth. */
public class GraphLoader {
    private static final int INITIAL_CAPACITY = 16;

    public static MatGraphClass readCSV(String fileName) throws IOException {
        Vertex[] vertices = new Vertex[INITIAL_CAPACITY];
        TupElem[] edges = new TupElem[INITIAL_CAPACITY];
        int vertexCount = 0;
        int edgeCount = 0;
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(fileName), StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || header.split(",", -1).length != 3) {
                throw new IOException("CSV 必须包含三列表头：source,target,weight");
            }
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) continue;
                String[] fields = line.split(",", -1);
                if (fields.length != 3 || fields[0].trim().isEmpty() || fields[1].trim().isEmpty()) {
                    throw new IOException("CSV 第 " + lineNumber + " 行必须包含非空端点和权重（三列）");
                }
                String source = fields[0].trim();
                String target = fields[1].trim();
                if (source.equals(target)) throw new IOException("CSV 第 " + lineNumber + " 行不支持自环");
                int weight;
                try {
                    double value = Double.parseDouble(fields[2].trim());
                    long rounded = Math.round(value);
                    if (!Double.isFinite(value) || value <= 0 || rounded < 1 || rounded >= MatGraphClass.INF) {
                        throw new NumberFormatException();
                    }
                    weight = (int) rounded;
                } catch (NumberFormatException ex) {
                    throw new IOException("CSV 第 " + lineNumber + " 行权重须为有限正数，四舍五入后在 [1, INF) 内", ex);
                }
                int start = findVertexIndex(vertices, vertexCount, source);
                if (start == -1) {
                    if (vertexCount == vertices.length) vertices = growVertices(vertices);
                    start = vertexCount;
                    vertices[vertexCount++] = createVertex(source);
                }
                int end = findVertexIndex(vertices, vertexCount, target);
                if (end == -1) {
                    if (vertexCount == vertices.length) vertices = growVertices(vertices);
                    end = vertexCount;
                    vertices[vertexCount++] = createVertex(target);
                }
                if (edgeCount == edges.length) edges = growEdges(edges);
                edges[edgeCount++] = new TupElem(start, end, weight);
            }
        }
        if (edgeCount == 0) throw new IOException("CSV 至少需要一条有效关系");
        Vertex[] trimmedVertices = new Vertex[vertexCount];
        TupElem[] trimmedEdges = new TupElem[edgeCount];
        System.arraycopy(vertices, 0, trimmedVertices, 0, vertexCount);
        System.arraycopy(edges, 0, trimmedEdges, 0, edgeCount);
        return new MatGraphClass(trimmedVertices, trimmedEdges);
    }

    // Reproducible simulation coordinates, not real geographic data.
    private static Vertex createVertex(String name) {
        long hash = Integer.toUnsignedLong(name.hashCode());
        double x = ((hash * 1103515245L + 12345L) & 0x7fffffffL) % 10001 / 100.0;
        double y = ((hash * 1664525L + 1013904223L) & 0x7fffffffL) % 10001 / 100.0;
        return new Vertex(name, x, y);
    }

    private static int findVertexIndex(Vertex[] vertices, int count, String name) {
        for (int i = 0; i < count; i++) {
            if (vertices[i].getName().equals(name)) return i;
        }
        return -1;
    }
    private static Vertex[] growVertices(Vertex[] source) {
        Vertex[] result = new Vertex[source.length * 2];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }
    private static TupElem[] growEdges(TupElem[] source) {
        TupElem[] result = new TupElem[source.length * 2];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }
}
