public class PathFinder {

    //BFS找最短路径
    public static int[] findShortestPath(MatGraphClass g,int start,int end){
        int[] visited=new int[g.n];//防止重复走
        int[] pre=new int[g.n];//记录上一个走过的是啥元素的数组，靠这个反推整条路
        //用数组模拟队列，每个顶点最多入队一次，因此长度为g.n
        int[] queue=new int[g.n];
        int front=0,rear=0;
        //假定所有结点都么有被走过
        for(int i=0;i<g.n;i++){
            visited[i]=0;
            pre[i]=-1;
        }
        visited[start]=1;//起点入队列
        queue[rear++]=start;//不断往后塞
        while(front!=rear){
            //队列出队
            int v=queue[front++];
            //BFS第一次到终点就是最短路径
            if(v==end)
                break;
            for(int w=0;w<g.n;w++){
               //规定visited[w]==0就是要保证只有没有访问过的顶点，才允许入队，防止假溢出
                //条件：不是自己，真的有边，前面没有遍历过
               if(g.edges[v][w]!=0 && g.edges[v][w]!=g.INF && visited[w]==0){
                    //标记来过了
                   visited[w]=1;
                   //上一个点记录一下后面要溯源
                   pre[w]=v;
                   //w入队，再访问相邻结点
                   queue[rear++]=w;
                }
            }
        }
        if(visited[end]==0)
            return null;
        return pre;
    }
    //输出路径
    public static void printPath(int start,int end,int[] pre,Vertex[] vexs){
        int[] stack=new int[vexs.length];
        int top=-1;
        int cur=end;
        while(cur!=-1){//确定路径
            stack[++top]=cur;
            cur=pre[cur];
        }
        //统计具体路径信息
        int nodeCount=top+1;
        int middleCount=Math.max(0,nodeCount-2);
        System.out.println("最少经过中间人：" + middleCount + " 人");
        System.out.println("最短路径：");
        while(top>=0){
            int idx=stack[top--];
            System.out.print(vexs[idx].getName() + "(" + vexs[idx].getX() + "," + vexs[idx].getY() + ")");
            if (top >= 0) System.out.print(" -> ");
        }
        System.out.println();
    }
    //调用入口
    public static void runShortestPath(MatGraphClass g, int start, int end){
        int[] pre=findShortestPath(g,start,end);
        if(pre==null){
            System.out.println("两个人一点关系没有");
            return;
        }
        printPath(start,end,pre,g.vexs);
    }
}


