import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {

    public static void main(String[] args)
            throws Exception {

        String fileName = args.length > 0 ? args[0] : "data/sample_edges.csv";
        MatGraphClass graph = GraphLoader.readCSV(fileName);
        //用BufferedReader读键盘输入，代替Scanner（不使用java.util）
        BufferedReader inputReader = new BufferedReader(new InputStreamReader(System.in));



        //3输入任意两个人的名字，找最短联系路径
        System.out.println("请输入第一个人的名字：");
        String nameA = inputReader.readLine();
        if (nameA == null) return;
        nameA = nameA.trim();
        System.out.println("请输入第二个人的名字：");
        String nameB = inputReader.readLine();
        if (nameB == null) return;
        nameB = nameB.trim();
        int indexA = graph.findIndexByName(nameA);
        int indexB = graph.findIndexByName(nameB);
        if (indexA == -1 || indexB == -1) {
            System.out.println("输入的名字在网络中不存在，请检查拼写");
            return;
        } else
            PathFinder.runShortestPath(graph, indexA, indexB);


        //6
        System.out.println();
        NetworkAnalyzer.printSocialCircle(graph, indexA);
        NetworkAnalyzer.printSocialCircle(graph, indexB);

        //4
        System.out.println();
        double x = graph.vexs[indexA].getX();
        double y = graph.vexs[indexA].getY();
        //搜索半径可根据实际坐标范围调整，这里坐标是0~100的随机数，取20作为示例
        NetworkAnalyzer.printNearbyByLocation(graph, x, y, 20);
        System.out.println();
        NetworkAnalyzer.printOneIntermediaryReachable(graph, indexA);


        //5
        System.out.println();
        NetworkAnalyzer.classifyPeople(graph);
        inputReader.close();


    }
}
