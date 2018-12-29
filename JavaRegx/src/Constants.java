
import java.util.ArrayList;

/**
 * 存取一些全局数据
 */
public class Constants {

    //分辨率信息，由全局变量维护
    static Coordinate minV,maxV;
    static int lxNum,lyNum,lzNum;
    static double amfa;
    static int surfaceNum,innerNum;
    static long surfaceTime,innerTime;

    /**
     * 输入模型的长宽高和初始网格边长
     * @param Lx 长
     * @param Ly 宽
     * @param Lz 高
     * @param a 边长
     */
    public static void addResolution(float Lx,float Ly,float Lz,double a){
        lxNum= (int) Math.ceil((Lx)/a);
        lyNum= (int) Math.ceil((Ly)/a);
        lzNum= (int) Math.ceil((Lz)/a);
        System.out.println("数量："+lxNum+","+lyNum+","+lzNum);
        amfa=a;
    }

    public static void addMinMaxCoordinate(float minx,float miny,float minz,
                                           float maxx,float maxy,float maxz){
        minV=new Coordinate(minx,miny,minz);
        maxV=new Coordinate(maxx,maxy,maxz);
    }

    //颜色信息，由全局变量来维护，而不是交由amf对象
    public static ArrayList<Color> colors=new ArrayList<>();

    public static void addColor(float r,float g,float b,float a){
        Color color=new Color(r, g, b, a);
        if(!colors.contains(color)){
            colors.add(color);
        }
    }

    //表示想输出边界还是所有的
    public final static int OUTPUT_SURFACE=0;
    public final static int OUTPUT_ALL=1;

    //表示内部体素是否合并过
    public static boolean innerMerged=false;

    //表示输出到SCAD的分片数量
    public static int SCAD_PART_COUNT=0;

    //计算输出体素数量
    public static int VoxelCount=0;
    public static int VoxelInnerCount=0;
    public static int VoxelSurfaceCount=0;
    public static int VoxelDenseCount=0;

    //计算剖分了却8个子体素都保留的root节点
    public static int RootRemain=0;

//    //是否要求限制体素数量以显示到OpenSCAD中
//    public static boolean showInSCAD=true;

    //是否开启内部填充
    public static boolean floodingEnabled=false;

    //是否开启内部Flooding式填充
    public static boolean innerFloodEnabled=false;

    //是否开启体素大小限制
    public static boolean minLimitEnabled=false;

    //约定的加密的层级，默认是四级加密，也就是通过八叉树的方式，最多深入4级进行加密
    //假设原来是a，一级加密之后为a/2,二级加密之后为a/4...四级加密之后为a/16
    public static int DENSE_DEPTH = 2;

    //输出到OpenSCAD单层循环所允许的循环数量上限
    public final static int WRITE_ARRAY_SIZE_LIMIT=9000;

    //加密部分所产生的体素
    public static int DENSE_NUM=0;

    public final static float EPSILON=0.0001f;

    //我们约定填充之前，所有的体素都是0
    public final static int MODEL_EMPTY=0;

    //我们约定无论以何种方式填充，其边界体素都为1
    public final static int MODEL_EDGE=1;

    //约定，内部FLooding的时候，其边界为1，外部为0，内部为2
    public final static int MODEL_INFLOOD_INNER=2;
    public final static int MODEL_INFLOOD_OUTER=0;

    //约定，外部Flooding的时候，其边界为1，外部为-1，内部为0
    public final static int MODEL_OUTFLOOD_INNER=0;
    public final static int MODEL_OUTFLOOD_OUTER=-1;

    //约定，加密的时候，被加密的体素在体素数组里都将被替换成-2
    public final static int MODEL_DENSE=-2;

    //约定，合并体素的时候，被合并的(包括单个体素也会被标记)
    public final static int MODEL_MERGE=-3;
}
