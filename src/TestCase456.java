//针对需求(4)(5)(6)的演示类，默认使用仓库内人工构造的示例数据
//坐标部分做了"人为可控"的处理，保证结果可复现、有说服力
public class TestCase456 {

    public static void main(String[] args) throws Exception {

        MatGraphClass graph = GraphLoader.readCSV(args.length > 0 ? args[0] : "data/sample_edges.csv");

        System.out.println("# 需求(6)测试：交往圈子对比（枢纽 vs 边缘节点） #");

        //示例中 jquery 有5个直接联系人，photoshop 只有1个，便于比较。
        int jqueryIdx = graph.findIndexByName("jquery");
        int photoshopIdx = graph.findIndexByName("photoshop");
        if (jqueryIdx < 0 || photoshopIdx < 0 || graph.findIndexByName("drupal") < 0 || graph.findIndexByName("haskell") < 0) {
            System.out.println("此演示需要 jquery、photoshop、drupal、haskell；可使用 data/sample_edges.csv");
            return;
        }
        NetworkAnalyzer.printSocialCircle(graph, jqueryIdx);
        NetworkAnalyzer.printSocialCircle(graph, photoshopIdx);

        System.out.println();

        System.out.println("# 需求(5)测试：核心/活跃/边缘人物分类           #");

        NetworkAnalyzer.classifyPeople(graph);

        System.out.println();

        System.out.println("# 需求(4)下半测试：1个中间人扩展联络范围         #");

        //photoshop 直接联系 css，通过 css 新增触达 jquery 和 html；只说明图上的可达性。
        NetworkAnalyzer.printOneIntermediaryReachable(graph, photoshopIdx);

        System.out.println();

        System.out.println("# 需求(4)上半测试：位置动态变化 -> 附近的人重新计算 #");

        int refIdx = jqueryIdx; //以jquery的位置为参照点
        double refX = graph.vexs[refIdx].getX();
        double refY = graph.vexs[refIdx].getY();
        double radius = 15.0;

        System.out.println("【移动前】以 jquery 当前坐标(" + refX + "," + refY + ") 为中心，半径" + radius + "：");
        NetworkAnalyzer.printNearbyByLocation(graph, refX, refY, radius);

        //人为把两个人的坐标"搬"到jquery附近/远处，制造可控、可验证的场景
        int nearPersonIdx = graph.findIndexByName("drupal");
        int farPersonIdx = graph.findIndexByName("haskell");

        //把drupal搬到jquery附近（半径15以内）
        graph.vexs[nearPersonIdx].setX(Math.min(100, refX + 5));
        graph.vexs[nearPersonIdx].setY(Math.min(100, refY + 5));

        //把haskell搬到离jquery尽量远的角落（0~100坐标范围内，取跟(refX,refY)欧氏距离更大的那个对角）
        double farX = (refX < 50) ? 100 : 0;
        double farY = (refY < 50) ? 100 : 0;
        graph.vexs[farPersonIdx].setX(farX);
        graph.vexs[farPersonIdx].setY(farY);

        System.out.println();
        System.out.println("【模拟位置变化】drupal 移动到 (" + graph.vexs[nearPersonIdx].getX()
                + "," + graph.vexs[nearPersonIdx].getY() + ")  <- 移到了jquery附近");
        System.out.println("【模拟位置变化】haskell 移动到 (" + graph.vexs[farPersonIdx].getX()
                + "," + graph.vexs[farPersonIdx].getY() + ")  <- 移到了jquery的对角远处");

        System.out.println();
        System.out.println("【移动后】再次以 jquery 坐标(" + refX + "," + refY + ") 为中心，半径" + radius + "查询：");
        NetworkAnalyzer.printNearbyByLocation(graph, refX, refY, radius);

        System.out.println();
        System.out.println("结论：drupal搬家后进入了结果列表，haskell搬家后不会出现在结果列表中");
        System.out.println("证明程序确实是根据【当前】坐标动态计算，而不是缓存了固定结果");
    }
}
