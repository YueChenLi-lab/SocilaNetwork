//定义每个节点的基本内容，包含
public class Vertex {
    //这些变量都要私有化是为了防止测试数据污染
    private String name;
    private double x;
    private double y;

    public Vertex(){//我也不知道有啥用先写着吧
    }

    public Vertex(String name,double x,double y){
        this.name=name;
        this.x=x;
        this.y=y;
    }
    //按照实际开发的标准，不能直接把这些变量都写成public，利用Getter/Setter 来访问，方便加入后续检查
    //要找特定结点的人名调用这个方法
    public String getName(){
        return name;
    }
    //重置人名调用这个
    public void setName(String name){
        this.name=name;
    }

    //x和y是坐标参数，get()函数是获取参数，set()函数是改参数
    public double getX(){
        return x;
    }
    public void setX(double x){
        this.x=x;
    }
    public double getY(){
        return y;
    }
    public void setY(double y){
        this.y=y;
    }

    public String toString(){

        return "Vertex{" + "name='" + name + '\'' + ", x=" + x + ", y=" + y + '}';
    }
}
