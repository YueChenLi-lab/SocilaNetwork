//此文件中涉及变量名众多，严格按照阿里巴巴 Java 开发手册要求定义变量名
//readCSV()
//
//        │
//
//手动查重(线性遍历)
//
//        │
//
//Vertex[]（数组手动扩容）
//
//        │
//
//TupElem[]（数组手动扩容）
//
//        │
//
//new MatGraphClass(vexs,tup)
//
//说明：本文件不使用java.util里的集合框架(ArrayList/HashMap等)，
//     顶点表、边表都用普通数组实现，容量不够时手动扩容(新建两倍大小的数组再逐个拷贝)
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
//因为代码过于复杂所以先调用ArrayList打个底再第二次修改时换成普通数据结构
public class GraphLoader {

    //初始容量，不够会自动翻倍扩容
    private static final int INITIAL_CAPACITY = 16;

    //readCSV()
    public static MatGraphClass readCSV(String fileName)
    //防止IO错误程序崩溃
            throws IOException {

        //BufferedReader 是对 FileReader 的封装，通过缓冲区减少 I/O 操作次数，提高读取效率，是 Java IO 流中的典型装饰器结构
        BufferedReader bufferedReader =
                new BufferedReader(new FileReader(fileName));

        //顶点建表：用普通数组代替ArrayList，vertexCount记录已用了多少个位置
        Vertex[] vertexArray = new Vertex[INITIAL_CAPACITY];
        int vertexCount = 0;

        //边建表：同样用普通数组代替ArrayList
        TupElem[] edgeArray = new TupElem[INITIAL_CAPACITY];
        int edgeCount = 0;

        String line;

        //注意：表头不要读
        bufferedReader.readLine();

        while ((line = bufferedReader.readLine()) != null) {

            //拆成数组
            String[] fields = line.split(",");

            //取出一条边两个端点
            String sourceVertex = fields[0];
            String targetVertex = fields[1];

            //csv读出来的是字符串，转换成带一位小数的数值
            double weightValue = Double.parseDouble(fields[2]);

            //在已有顶点数组里线性查找名字是否已经出现过（代替HashMap查重）
            //sourceVertex 未出现？
            //        ↓
            //数组满了就扩容
            //        ↓
            //生成坐标 (x,y)
            //        ↓
            //加入 vertexArray，vertexCount++
            int startIndex = findVertexIndex(vertexArray, vertexCount, sourceVertex);

            if (startIndex == -1) {

                //数组满了先扩容
                if (vertexCount == vertexArray.length) {
                    vertexArray = growVertexArray(vertexArray);
                }

                double xCoordinate = Math.random() * 100;

                double yCoordinate = Math.random() * 100;

                vertexArray[vertexCount] = new Vertex(sourceVertex, xCoordinate, yCoordinate);

                startIndex = vertexCount;
                vertexCount++;
            }

            // target节点同样处理（必须补，否则会下标找不到）
            int endIndex = findVertexIndex(vertexArray, vertexCount, targetVertex);

            if (endIndex == -1) {

                if (vertexCount == vertexArray.length)
                    vertexArray = growVertexArray(vertexArray);


                double xCoordinate = Math.random() * 100;

                double yCoordinate = Math.random() * 100;

                vertexArray[vertexCount] = new Vertex(targetVertex, xCoordinate, yCoordinate);
                endIndex = vertexCount;
                vertexCount++;
            }

            int weight = (int) Math.round(weightValue);

            //边数组满了先扩容
            if (edgeCount == edgeArray.length)
                edgeArray = growEdgeArray(edgeArray);

            edgeArray[edgeCount] = new TupElem(startIndex, endIndex, weight);
            edgeCount++;
        }

        //close 必须在 while 外面
        bufferedReader.close();

        //按实际使用个数裁剪成刚好大小的数组，交给MatGraphClass
        Vertex[] trimmedVertexArray = trimVertexArray(vertexArray, vertexCount);

        TupElem[] trimmedEdgeArray = trimEdgeArray(edgeArray, edgeCount);

        return new MatGraphClass(trimmedVertexArray, trimmedEdgeArray);
    }

    //在vertexArray的前vertexCount个有效位置里线性查找名字，找到返回下标，找不到返回-1
    //代替HashMap的查重功能
    private static int findVertexIndex(
            Vertex[] vertexArray, int vertexCount, String name) {

        for (int i = 0; i < vertexCount; i++) {
            if (vertexArray[i].getName().equals(name))
                return i;

        }
        return -1;
    }

    //顶点数组扩容：新建一个两倍大小的数组，把原来的内容逐个拷贝过去
    private static Vertex[] growVertexArray(Vertex[] oldArray) {

        Vertex[] newArray = new Vertex[oldArray.length * 2];

        for (int i = 0; i < oldArray.length; i++)
            newArray[i] = oldArray[i];

        return newArray;
    }

    //边数组扩容：同理
    private static TupElem[] growEdgeArray(TupElem[] oldArray) {

        TupElem[] newArray = new TupElem[oldArray.length * 2];

        for (int i = 0; i < oldArray.length; i++)
            newArray[i] = oldArray[i];

        return newArray;
    }

    //把顶点数组按实际个数裁剪，去掉扩容后多余的空位置
    private static Vertex[] trimVertexArray(Vertex[] source, int count) {

        Vertex[] result = new Vertex[count];

        for (int i = 0; i < count; i++)
            result[i] = source[i];

        return result;
    }

    //把边数组按实际个数裁剪
    private static TupElem[] trimEdgeArray(TupElem[] source, int count) {

        TupElem[] result = new TupElem[count];

        for (int i = 0; i < count; i++)
            result[i] = source[i];

        return result;
    }

}