//对应456
//此文件中涉及变量名众多，严格按照阿里巴巴 Java 开发手册要求定义变量名
//这段代码本来有ArrayList和HashSet辅助保证先运行成功再删掉换成普通线性表
public class NetworkAnalyzer {
    //（这一段是ai加的）因为不阉割数组就会把空余位置加上0，影响BFS的判断
//也不许public,否则多组测试数据变潮湿导致结果发霉
    //初始化大数组
    //    ↓
    //填充有效数据
    //    ↓
    //count记录有效长度
    //    ↓
    //trimArray压缩
    //    ↓
    //返回干净结果
 //因为输出数组所以每个方法处理完都要调用一次防止扰乱结果
  private static int[] trimArray(int[] source,int count){
        int[] result=new int[count];
        for(int i=0;i<count;i++)
            result[i]=source[i];
        return result;
    }
//原来的arr.contains(value)改编的部分，功能不变
    //线性集合查找替换原来直接定位的set.contains(value)
    //因为第4问的要求是跳一次才能联系到的人，所以要把直接边的删掉，所以要写个函数检查是不是相邻的结点
//findReachableWithinHops → reachable数组（2跳的点的数组）
//findDirectCircle        → direct数组（直接相邻的要被斩杀的结点）
//        ↓
//for each reachable:
//        ↓
//containsValue(direct, 当前节点)
//        ↓
//true  → 排除
//false → 保留
    private static boolean containsValue(int[] arr,int value){
      for(int i=0;i<arr.length;i++){
          if(arr[i]==value)
              return true;
      }
      return false;
      }
//此刻我想斩杀作业!!!!!!!!


    //终于开始正经写问题了
    //ai改进点：由于4和6代码大段重复故先写6再写4，4还能调6
    //第六问：查找某人的直接交往圈子  说人话就是在我已经定义好的图对象里挖出邻接矩阵，再收集与其直接相连的所有顶点
    public static int[] findDirectCircle(MatGraphClass g,int personIndex){//对应direct数组
      int[] temp=new int[g.n];
        int count=0;
        for(int j=0;j<g.n;j++){
            if(j==personIndex)
                continue;
            //利用邻接矩阵判断两点之间是否有边
            //          A   B   C   D
            //A         0   1  INF  1
            //B         1   0   1  INF
            //C        INF  1   0   1
            //D         1  INF  1   0  用一行写就行
            if(g.edges[personIndex][j]!=g.INF){
                temp[count]=j;//有边就收编
                count++;
            }
        }
        return trimArray(temp,count);
    }

    public static void printSocialCircle(MatGraphClass g,int personIndex){
      int[] circle=findDirectCircle(g,personIndex);
      //上次失败vexs[i].name 是因为name是内部变量。没法直接访问，所以要用getName调用
        System.out.println(g.vexs[personIndex].getName()+" 的交往圈子（共" + circle.length + "人）：");
        //
        for (int i = 0; i < circle.length; i++) {
            int idx = circle[i];
            System.out.print(g.vexs[idx].getName()+" ");
        }
        System.out.println();
    }

    //第四问  要变动位置就写新坐标，返回一跳两跳的人
    //第一部分空间搜索
    public static int[] findNearbyByLocation(MatGraphClass g,double x,double y,double radius){
        int[] temp=new int[g.n];
        int count=0;
     for(int i=0;i<g.n;i++){
         //两点之间距离公式  √((x1-x2)^2+(y1-y2)^2)
         double dx=g.vexs[i].getX()-x;
         double dy=g.vexs[i].getY()-y;
        double distance=Math.sqrt(dx * dx+dy * dy);
        if(distance<=radius){
            temp[count]=i;
            count++;
        }
     }
     return trimArray(temp,count);
    }
    public static void printNearbyByLocation(MatGraphClass g, double x, double y, double radius) {
      int[] nearby=findNearbyByLocation(g,x,y,radius);
        System.out.println("坐标(" + x + "," + y + ")附近" + radius + "范围内共"
                + nearby.length + "人：");

        for (int i = 0; i < nearby.length; i++) {
            int idx = nearby[i];
            System.out.println("  " + g.vexs[idx].getName() + "(" + g.vexs[idx].getX() + "," + g.vexs[idx].getY() + ")");
        }
    }
    //通过k跳可以联系的人
    //因为BFS原理是按层拓展
    public static int[] findReachableWithinHops(MatGraphClass g, int start, int hopLimit){
        int[] visited = new int[g.n];
        int[] hopCount = new int[g.n];
        int[] queue = new int[g.n];
        int front = 0, rear = 0;
        for (int i = 0; i < g.n; i++) {
            visited[i] = 0;//0是没访问，1是访问过
            hopCount[i] = -1;//记录这是第几跳
        }
        //起点入队
        visited[start] = 1;
        hopCount[start] = 0;
        queue[rear++] = start;
        //开始BFS循环
        //从节点 v 出发，遍历所有节点 w：
        //如果 v → w 有边 且 w 没访问过：
        //    标记 w 已访问
        //    计算 w 的跳数 = v 的跳数 + 1
        //    w 入队
        while (front != rear) {
            int v = queue[front++];
            //超过跳数限制就不再扩展
            if (hopCount[v] >= hopLimit) continue;
            for (int w = 0; w < g.n; w++) {
                if (g.edges[v][w] != g.INF && g.edges[v][w] != 0 && visited[w] == 0) {
                    visited[w] = 1;
                    hopCount[w] = hopCount[v] + 1;
                    queue[rear++] = w;
                }
            }
        }
        int[] temp = new int[g.n];
        int count = 0;

        for (int i = 0; i < g.n; i++) {
            if (i != start && hopCount[i] != -1 && hopCount[i] <= hopLimit) {
                temp[count] = i;
                count++;
            }
        }
        return trimArray(temp, count);
    }
    public static void printOneIntermediaryReachable(MatGraphClass g, int start) {
        int[] reachable = findReachableWithinHops(g, start, 2);
        int[] direct = findDirectCircle(g, start);
        System.out.println(g.vexs[start].getName() + " 通过至多1个中间人可联络到的人（不含直接联系人）：");
        int count = 0;
        for (int i = 0; i < reachable.length; i++) {
            int idx = reachable[i];
            if (!containsValue(direct, idx)) {
                System.out.println("  " + g.vexs[idx].getName());
                count++;
            }
        }
        if (count == 0)
            System.out.println("（无，可能都已是直接联系人，或网络中不存在这样的人）");

    }


    //第五问  利用度找核心活跃边缘人物
    //度数计算
    //┌──────────────┐
    //│   开始       │
    //└──────┬───────┘
    //       ↓
    //┌──────────────────────┐
    //│ 输入图 g             │
    //│ n = 节点数量         │
    //│ degree数组初始化     │
    //└────────┬─────────────┘
    //         ↓
    //┌──────────────────────┐
    //│ i = 0                │
    //└────────┬─────────────┘
    //         ↓
    //┌──────────────循环──────────────┐
    //│ i < n ?                       │
    //└──────┬───────────────┬────────┘
    //       │是              │否
    //       ↓                ↓
    //┌──────────────────┐   ┌──────────┐
    //│ count = 0        │   │ 结束     │
    //│ j = 0            │   └──────────┘
    //└──────┬───────────┘
    //       ↓
    //┌──────────────循环──────────────┐
    //│ j < n ?                       │
    //└──────┬───────────────┬────────┘
    //       │是              │否
    //       ↓                ↓
    //┌────────────────────────────┐
    //│ 判断：                     │
    //│ i != j                    │
    //│ AND edges[i][j] != INF   │
    //└──────┬─────────────────────┘
    //       ↓
    //   ┌──────────────┐
    //   │ count++      │
    //   └──────┬───────┘
    //          ↓
    //      j = j + 1
    //          ↓
    //   （回到 j循环）
    //
    //       ↓（j结束）
    //┌──────────────────────┐
    //│ degree[i] = count    │
    //└────────┬─────────────┘
    //         ↓
    //      i = i + 1
    //         ↓
    //   （回到 i循环）
    public static int[] computeDegree(MatGraphClass g) {
        int[] degree = new int[g.n];
        for (int i = 0; i < g.n; i++) {
            int count = 0;
            for (int j = 0; j < g.n; j++) {
                if (i != j && g.edges[i][j] != g.INF) {
                    count++;
                }
            }
            degree[i] = count;
        }
        return degree;
    }

    //就排序号
    //┌──────────────┐
    //│   开始       │
    //└──────┬───────┘
    //       ↓
    //┌────────────────────────┐
    //│ 输入 degree 数组         │
    //│ n = length             │
    //└────────┬───────────────┘
    //         ↓
    //┌────────────────────────┐
    //│ 初始化 index数组         │
    //│ index[i] = i           │
    //└────────┬───────────────┘
    //         ↓
    //┌────────────────────────┐
    //│ i = 0                  │
    //└────────┬───────────────┘
    //         ↓
    //┌────────────外层循环────────────┐
    //│ i < n-1 ?                   │
    //└──────┬───────────────┬──────┘
    //       │是              │否
    //       ↓                ↓
    //┌──────────────────────┐   ┌──────────┐
    //│ maxPos = i           │   │ 输出index│
    //└──────┬───────────────┘   └──────────┘
    //       ↓
    //┌────────────────────────┐
    //│ j = i + 1              │
    //└────────┬───────────────┘
    //         ↓
    //┌────────────内层循环────────────┐
    //│ j < n ?                     │
    //└──────┬───────────────┬──────┘
    //       │是              │否
    //       ↓                ↓
    //┌────────────────────────────┐
    //│ 比较：                     │
    //│ degree[index[j]]          │
    //│ > degree[index[maxPos]]   │
    //└──────┬─────────────────────┘
    //       ↓
    //┌──────────────┐
    //│ maxPos = j   │
    //└──────┬───────┘
    //       ↓
    //   j = j + 1
    //       ↓
    //   （回到 j循环）
    //
    //       ↓（j结束）
    //┌────────────────────────┐
    //│ 交换 index[i]         │
    //│ 和 index[maxPos]      │
    //└────────┬───────────────┘
    //         ↓
    //      i = i + 1
    //         ↓
    //   （回到 i循环）
    public static int[] sortIndexByDegreeDesc(int[] degree) {
        int n = degree.length;
        int[] index = new int[n];
        for (int i = 0; i < n; i++)
            index[i] = i;//第 i 个“候选节点编号”

        for (int i = 0; i < n - 1; i++) {
            int maxPos = i;//当前找到的“度数最大节点编号”
            for (int j = i + 1; j < n; j++) {
                if (degree[index[j]] > degree[index[maxPos]]) {
                    maxPos = j;
                }
            }
            int temp = index[i];
            index[i] = index[maxPos];
            index[maxPos] = temp;
        }
        return index;
    }

    //分类规则：按度数排名，前20%为核心人物，中间50%为活跃人物，后30%为边缘人物
    public static void classifyPeople(MatGraphClass g) {
        int[] degree = computeDegree(g);
        int[] sortedIndex = sortIndexByDegreeDesc(degree);
        int n = g.n;
        int coreEnd = (int) Math.ceil(n * 0.2);
        int activeEnd = (int) Math.ceil(n * 0.7);
        System.out.println("核心人物（度数最高的前20%）");
        for (int i = 0; i < coreEnd; i++) {
            int idx = sortedIndex[i];
            System.out.println("  " + g.vexs[idx].getName() + "  度数=" + degree[idx]);
        }

        System.out.println("活跃人物（中间50%）");
        for (int i = coreEnd; i < activeEnd; i++) {
            int idx = sortedIndex[i];
            System.out.println("  " + g.vexs[idx].getName() + "  度数=" + degree[idx]);
        }

        System.out.println("边缘人物（度数最低的后30%）");
        for (int i = activeEnd; i < n; i++) {
            int idx = sortedIndex[i];
            System.out.println("  " + g.vexs[idx].getName() + "  度数=" + degree[idx]);
        }
    }


    }

