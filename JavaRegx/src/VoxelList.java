import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * 体素结构
 *
 * 体素通过左下角，例如(0,2,3)来确定小正方形的位置
 * 通过a(边长)确认这个体素有多大
 */

public class VoxelList{

//    ArrayList<Voxel> voxels=new ArrayList<>();
    //使用集合存储，加快相同体素判定
    Set<Voxel> voxels=new HashSet<>();

    //体素模型的分辨率
    int LxNum= 0;
    int LyNum= 0;
    int LzNum= 0;

    long surfaceNum=0;       //表面体素数量
    long innerNum=0;         //内部体素数量
    long surfaceTime=0;     //表面体素化用时
    long innerTime=0;       //内部体素化用时

    public void addVoxel(Voxel voxel){
        //由于使用集合，我们已经不需要手动判重
        voxels.add(voxel);
    }

    public void setSurfaceTime(long surfaceTime) {
        this.surfaceTime = surfaceTime;
    }

    public void setInnerTime(long innerTime) {
        this.innerTime = innerTime;
    }
}

/**
 * 体素单元，存储
 */
class Voxel {

    Coordinate coordinate;  //当前体素左下前的坐标点
    double a;               //体素化的边长
    int filled=0;           //这个体素是否被填充,如果没有被填充，那么就没有必要继续对它进行加密
    Triangle triangle;        //这个体素十分靠近于哪一个平面，这个是计算
    ArrayList<Voxel> children=new ArrayList<>(); //存放子体素，这些体素会被判定为离三角面足够近的时候才会被加入
    //如果当前Voxel存在子体素，那么输出的时候应当继续遍历子体素，这一层不输出

    Set<Direction> directions=new HashSet<>(); //包含这个体素的哪些面是指向外侧的

    boolean root=false;//表示这个节点是root节点
    VoxelNum voxelNum;//只有在root节点存在的时候这里才会有值，保存在网格中的节点下标
    short color;//颜色
//    int featurePointNum=0;  //包含特征边上特征点的数量


    /**
     * 判断体素是否离自己所依附的三角形足够近
     * @return
     */
    public boolean IsNearAttachedTriangle(AMF amf){

        //判断三角形的类型
        if(triangle.type>0){

            //平面三角形,在三角形内
            if(triangle.coordinateInParallelTriangle(amf,
                    coordinate.x+a/2,coordinate.y+a/2,coordinate.z+a/2)){
                return true;
            }
        }
        //判断距离
        else {

            //于是,i,j,k分别是体素的编号
            //我们根据i,j,k可以得出该体素的中心点的位置
            //分别是i*a+0.5*a ,j*a+0.5*a, k*a+0.5*a
            //我们需要判断这个点和当前三角形的三角面片的关系
            //计算当前体素中心点到三角形的距离
            double distance = triangle.getDistance(coordinate.x+a/2,coordinate.y+a/2,coordinate.z+a/2);

            //如果距离在范围之内，这个体素将被标记上需要继续加密
            if(distance - Math.sqrt(3)*a*0.5 <=0){
                return true;
            }
        }

        return false;
    }


    public Voxel(double x,double y,double z,double a,Triangle triangle){
        this.coordinate=new Coordinate(x,y,z);
        this.a=a;
        this.triangle=triangle;
    }

    /**
     * 根据当前x y z坐标点，确定所在的体素的序号
     * @param x x 坐标
     * @param y y 坐标
     * @param z z 坐标
     * @return 应当返回所在的体素
     */
    public static VoxelNum getVoxelNum(float x,float y,float z,double a){
        VoxelNum vNum=new VoxelNum(
                (int) Math.floor(x/a),
                (int) Math.floor(y/a),
                (int) Math.floor(z/a)
        );

        return vNum;
    }

    /**
     * 添加子体素,每个子体素添加之前，都已经确认了它们的哪些方向都是外表面
     * @param voxel
     */
    public void addVoxel(Voxel voxel){

        //知道当前添加的是第几个子体素0-7
        int position=children.size();

        //子节点都是按照顺序添加的，一旦知道position就知道它所在的空间位置
        //也就可以知道它的几个方向是和外侧相邻的
        InheritDirection(voxel,position);

        //添加到子体素中
        children.add(voxel);

    }

    /**
     * 子元素从父亲体素节点上，继承和外界相邻的属性
     * @param voxel 传入的子节点
     * @param position 子节点的位置
     */
    private void InheritDirection(Voxel voxel, int position) {

        //传入voxel根据position，继承属性
        //比如传入的position=5，二进制是101
        int z,y,x;
        if(position/4>0){
            z=1;position%=4;
        }
        else {
            z=0;
        }
        if(position/2>0){
            y=1;position%=2;
        }
        else {
            y=0;
        }
        x=position;

        //经过上述简单的除二取余 position=5，二进制是101
        //zyx           x  y  z
        //000   ===>>  -1,-1,-1
        //001   ===>>   1,-1,-1
        //010   ===>>  -1, 1,-1
        //011   ===>>   1, 1,-1
        //100   ===>>  -1,-1, 1
        //101   ===>>   1,-1, 1
        //110   ===>>  -1, 1, 1
        //111   ===>>   1, 1, 1
        //可以看到，0-7的二进制排列刚好与其邻边有关系
        //关系是z,y,x ，并且如果是1则变为1，如果是0则变为-1
        x= x==1?1:-1;Direction direction1=new Direction(x,0,0);
        y= y==1?1:-1;Direction direction2=new Direction(0,y,0);
        z= z==1?1:-1;Direction direction3=new Direction(0,0,z);

        //这样就可以确认这个voxel要继承哪些direction
        if(directions.contains(direction1)){
            voxel.directions.add(direction1);
        }
        if(directions.contains(direction2)){
            voxel.directions.add(direction2);
        }
        if(directions.contains(direction3)){
            voxel.directions.add(direction3);
        }

        //这样，我就知道这个voxel的哪个方向上，邻接着外部空间
        //就可以知道，去除这个voxel之后，哪些voxel会受到影响
    }

    /**判断两元素相等，方法重写
     * 当出现hashCode相等的时候，程序会继续使用equals判断，如果此判断也通过
     * 证明这个体素已经存在过
     * 我们约定，两个体素相等，必须要求他们的编号相等，且对应的体素边长相等
     * 参考资料 http://blog.csdn.net/witsmakemen/article/details/7323604
     */
    public boolean equals(Object obj) {
        if (obj instanceof Voxel) {
            Voxel voxel = (Voxel) obj;
            return this.coordinate.x==voxel.coordinate.x &&
                    this.coordinate.y==voxel.coordinate.y &&
                    this.coordinate.z==voxel.coordinate.z &&
                    this.a == voxel.a;
        }
        return super.equals(obj);
    }

    /**
     * 重写hashcode 方法，返回的hashCode 不一样才认定为不同的对象
     * 通过调用voxelNum的生成哈希值的方法
     *
     * 理论上哈希值可以过滤掉绝大部分点的判断，速度提升近10倍
     */
    @Override
    public int hashCode() {
        return this.coordinate.hashCode();
    }

    public void get8Children() {

        //新体素的宽度
        double newa = this.a / 2;

        //生成8个子节点返回
        //讲道理，我们在生成8个方块的时候，
        // 应当已经可以通过父元素direction确认出这些子体素对应了哪些direction
        addVoxel(new Voxel(coordinate.x, coordinate.y, coordinate.z, newa, triangle));
        addVoxel(new Voxel(coordinate.x + newa, coordinate.y, coordinate.z, newa, triangle));
        addVoxel(new Voxel(coordinate.x, coordinate.y + newa, coordinate.z, newa, triangle));
        addVoxel(new Voxel(coordinate.x + newa, coordinate.y + newa, coordinate.z, newa, triangle));
        addVoxel(new Voxel(coordinate.x, coordinate.y, coordinate.z + newa, newa, triangle));
        addVoxel(new Voxel(coordinate.x + newa, coordinate.y, coordinate.z + newa, newa, triangle));
        addVoxel(new Voxel(coordinate.x, coordinate.y + newa, coordinate.z + newa, newa, triangle));
        addVoxel(new Voxel(coordinate.x + newa, coordinate.y + newa, coordinate.z + newa, newa, triangle));

//        ArrayList<Voxel> voxels=new ArrayList<>();
//
//        voxels.add(new Voxel(coordinate.x,coordinate.y,coordinate.z,newa,triangle));
//        voxels.add(new Voxel(coordinate.x+newa,coordinate.y,coordinate.z,newa,triangle));
//        voxels.add(new Voxel(coordinate.x,coordinate.y+newa,coordinate.z,newa,triangle));
//        voxels.add(new Voxel(coordinate.x+newa,coordinate.y+newa,coordinate.z,newa,triangle));
//        voxels.add(new Voxel(coordinate.x,coordinate.y,coordinate.z+newa,newa,triangle));
//        voxels.add(new Voxel(coordinate.x+newa,coordinate.y,coordinate.z+newa,newa,triangle));
//        voxels.add(new Voxel(coordinate.x,coordinate.y+newa,coordinate.z+newa,newa,triangle));
//        voxels.add(new Voxel(coordinate.x+newa,coordinate.y+newa,coordinate.z+newa,newa,triangle));
//        return voxels;

    }

    /**
     * 判断一个点是否在这个体素之内
     * @param x
     * @param y
     * @param z
     * @return
     */
    public boolean inVoxel(float x,float y,float z){
        return false;
    }

    /**
     * 调整voxel的方向，调用这个方法的voxel是加密的父元素
     * @param position 父元素的position处的子元素被删除
     *                 所以需要确定这一点删除(设置为-1)对其他voxel的影响
     */
    public void adjustDirection(int position) {

        //先确认一下，position从0-7 分别会影响那些voxel
        //               z    y    x    ||    z    y    x   ||    z    y    x
        //000    ===>>   1    0    0          0    1    0         0    0    1
        //001    ===>>   1    0    1          0    0    0         0    1    1
        //010    ===>>   1    1    0          0    0    0         0    1    1
        //011    ===>>   1    1    1          0    1    0         0    0    1
        //100    ===>>   0    0    0
        //可以看到，是每一位分别取反
        //取反过后呢~要把当前的direction给其他的voxel
        //如果是对z取反，也就是取到上方或者下方的体素，
        //显然应当把当前的(0,0,1)或者(0,0,-1)给当前的体素
        //如果对z取反之后是0，则应该改(0,0,1),否则应该给(0,0,-1)
        //换句话说，其实不取反即可以看到，需要给哪些体素，比如0,0,0对应的肯定是三个-1,
        //这三个-1对应三个坐标轴，向哪个坐标轴取反，只要把取反之前的数，对应的direction给它就好了
        int z,y,x;
        if(position/4>0){
            z=1;position%=4;
        }
        else {
            z=0;
        }
        if(position/2>0){
            y=1;position%=2;
        }
        else {
            y=0;
        }
        x=position;

        //得出了当前xyz对应的01显示,获取当前位置的direction
        //获取到三个邻居
        int neighbor1=z*4+y*2+(x>0?0:1);
        int neighbor2=z*4+(y>0?0:1)*2+x;
        int neighbor3=(z>0?0:1)*4+y*2+x;

        Direction direction1=new Direction(x==0?-1:1,0,0);
        Direction direction2=new Direction(0,y==0?-1:1,0);
        Direction direction3=new Direction(0,0,z==0?-1:1);

        //neighbor123将分别受到来自direction123的影响(前提是当前voxel有该direction)
        if(children.get(position).directions.contains(direction1)){
            children.get(neighbor1).directions.add(direction1);
        }
        if(children.get(position).directions.contains(direction2)){
            children.get(neighbor2).directions.add(direction2);
        }
        if(children.get(position).directions.contains(direction3)){
            children.get(neighbor3).directions.add(direction3);
        }

        //当前节点去除之后产生的direction变化，已经移交到与它相邻的voxel里面

    }
}

/**
 * 体素的三个序号
 */
class VoxelNum{
    int x,y,z;

    public VoxelNum(int x,int y,int z){
        this.x=x;
        this.y=y;
        this.z=z;
    }

    /**
     * 针对当前体素生成一个它的哈希值
     * @return 返回哈希值
     */
    @Override
    public int hashCode() {
        return x*31*31+y*31+z;
    }
}

/**
 * 方向
 */
class Direction{
    byte x=0,y=0,z=0;

    public Direction(){

    }

    public Direction(byte x,byte y,byte z){
        this.x=x;
        this.y=y;
        this.z=z;
    }

    public Direction(int x, int y, int z) {
        this.x= (byte) x;
        this.y= (byte) y;
        this.z= (byte) z;
    }

    public byte[] convert(byte dir){
        byte[] se=new byte[2];//开始和结束
        if(dir>0){
            se[0]=se[1]=0;
        }
        if(dir<0){
            se[0]=se[1]=1;
        }
        if(dir==0){
            se[0]=0;
            se[1]=1;
        }
        return se;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Direction) {
            Direction direction = (Direction) obj;
            return this.x==direction.x &&
                    this.y==direction.y &&
                    this.z==direction.z ;
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return x*49+y*7+z;
    }
}

//class Increase{
//    double x=0,y=0,z=0;
//
//    public Increase(double x,double y,double z){
//        this.x=x;
//        this.y=y;
//        this.z=z;
//    }
//}