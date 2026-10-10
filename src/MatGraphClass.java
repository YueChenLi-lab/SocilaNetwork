public class MatGraphClass{
    final int MAXV=500;//最大顶点数
    static final int INF=0x3f3f3f3f;
    int[][] edges;//邻接矩阵
    int n,e;//顶点 边数
    Vertex[] vexs;

    public MatGraphClass(){
        edges=new int[MAXV][MAXV];
        vexs=new Vertex[MAXV];
    }
    //建立矩阵① 创建数组
    //        ↓
    //② 统计顶点数、边数(单开了TupElem和Vertex类)
    //        ↓
    //③ 把顶点复制进图
    //        ↓
    //④ 邻接矩阵全部初始化
    //   对角线 = 0
    //   其他 = INF
    //        ↓
    //⑤ 根据每条边修改矩阵
    //        ↓
    //⑥ 图建立完成
    public MatGraphClass(Vertex[] v, TupElem[] tup){
        n=v.length;
        e=0; // Each unique undirected relation adds two matrix entries.
        vexs=new Vertex[n];
        edges=new int[n][n];
        for(int i=0;i<n;i++){
            vexs[i]=v[i];
        }
        for(int i=0;i<n;i++){
            for(int j=0;j<n;j++){
                if(i==j)
                    edges[i][j]=0;
                else
                    edges[i][j]=INF;
            }
        }
        for(int i=0;i<tup.length;i++){
            int r=tup[i].r;
            int c=tup[i].c;
            int d=tup[i].d;
            if (r == c || r < 0 || c < 0 || r >= n || c >= n || d <= 0 || d >= INF) {
                throw new IllegalArgumentException("边必须连接两个有效顶点，且权重在 [1, INF) 内");
            }
            if (edges[r][c] == INF) e += 2;
            edges[r][c]=Math.min(edges[r][c], d);
            edges[c][r]=edges[r][c];
        }
    }
    //(这一段是ai加的)用于支持"输入任意两个人的名字"这类需求，避免只能用硬编码下标
    //查找名字下标，找到了返回对应数组下标，找不到就返回-1
    public int findIndexByName(String name){
        for(int i=0;i<n;i++){
            // 遍历所有顶点，看看谁的名字和输入的 name 一致
            if(vexs[i].getName().equals(name)){
                return i;
            }
        }
        return -1;
    }
}

