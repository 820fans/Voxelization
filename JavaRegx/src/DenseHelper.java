
import java.util.ArrayList;

/**
 * 加密助手类，用于完成加密
 */
public class DenseHelper {

    ArrayList<AMF> amfs;

    public DenseHelper(ArrayList<AMF> amfs){
        this.amfs=amfs;
    }

    //传入模型，进行加密操作
    public void run(){

//        long startTime=System.currentTimeMillis();
//        //先应该计算哪些边是需要加密的
//        for(AMF amf:amfs){
//            //统计边集,并计算出边对应的三角形夹角
//            for(int i=0;i<amf.volume.size();i++){
//                amf.generateFeatureEdge(i);
//            }
//
//            if(amf.volume.size()>amf.featureTriangles.size()){
//                System.out.println("Valuable : "+(amf.volume.size()-amf.featureTriangles.size()));
//            }
//
//            System.out.println("feature:"+amf.featureTriangles.size());
//        }
//
//        long endTime=System.currentTimeMillis();
//        System.out.println("start after:"+(endTime-startTime));

        if(Constants.DENSE_DEPTH<=0) return;

        //读入一些全局数据
        double amfa=Constants.amfa;

        //每一个Object对象都应当进行加密
        for(AMF amf:amfs){

            //我们需要计算一下当前的amf相对于整个坐标原点偏移了多少个体素
//            int[] offset=VoxelOutput.GenerateOffset(amf);

            //对每一个Triangle进行加密
            for (Triangle t : amf.volume) {

                //对每一个体素进行遍历
                for (int i = t.minNum.x; i <= t.maxNum.x; i++) {

                    for (int j = t.minNum.y; j <= t.maxNum.y; j++) {

                        for (int k = t.minNum.z; k <= t.maxNum.z; k++) {

                            //检查这一点是否“离三角形很近”
                            if (t.CheckCoordinateNearTriangle(amf,
                                    i * amfa, j * amfa, k * amfa, amfa, amfa / 2)) {

//                                //老版测试
//                                Voxel voxel = new Voxel(i * amfa, j * amfa, k * amfa, amfa, t);
//                                StartDFS(amf,voxel,t,0);
//                                amf.voxels[i][j][k] = Constants.MODEL_DENSE;

                                //确认是紧贴外部的边界
                                //第一步应当找出所有的root节点,确定root节点邻接向量数组
                                Voxel voxel = new Voxel(i * amfa, j * amfa, k * amfa, amfa, t);

                                //离得很近的这一点，是靠近外部的边界否？
                                if (amf.IsOutsideEdgeVoxel(voxel, i, j, k)) {

                                    //这一点的体素标记
                                    amf.voxels[i][j][k] = Constants.MODEL_DENSE;

                                    //设置根节点属性
                                    voxel.root=true;
                                    voxel.voxelNum=new VoxelNum(i,j,k);

                                    amf.voxelList.addVoxel(voxel);
                                }
                            }
                        }
                    }
                }
            }

            //至此取出了当前amf对象中所有想进行加密的体素，均存放于amf.voxelList.voxels里
            //需要确认进行几层加密
            for(int i=0;i<Constants.DENSE_DEPTH;i++){

                //第一次遍历的时候，都是root节点
                //遍历的时候，需要确定这个root节点的filled，
                // 如果是0表示这个点尚未探索过，是需要在这一轮进行剖分的
                // 如果是1表示这个点被剖分过，我们需要寻找其子节点，直至找到
                // 如果是-1，表示这个点已经因为距离太远被我们删除了
                for(Voxel voxel:amf.voxelList.voxels){

                    //通过递归，层层加密
                    getAndCreateVoxelsByDFS(amf,voxel);

                }
            }

            //修改和合并未拆分的体素
            amf.voxelList.voxels.stream().filter(voxel -> generateAndMergeByDFS(voxel) == 8).forEach(
                    voxel -> amf.voxels[voxel.voxelNum.x][voxel.voxelNum.y][voxel.voxelNum.z]
                            = Constants.MODEL_EDGE);
//            for(Voxel voxel:amf.voxelList.voxels){
//                if(generateAndMergeByDFS(voxel)==8){
//                    amf.voxels[voxel.voxelNum.x][voxel.voxelNum.y][voxel.voxelNum.z]=Constants.MODEL_EDGE;
//                }
//            }
//            System.out.println("总计："+count+" 未剖分");
        }
    }

    /**
     * 将加密树由底向上合并
     * @param voxel
     * @return
     */
    private int generateAndMergeByDFS(Voxel voxel){
        if(voxel.filled==0 || (voxel.filled==-1 && voxel.directions.size()==0)){
            return 1;
        }
        else{
            int count=0;
            for(Voxel voxel1:voxel.children){
                count+=generateAndMergeByDFS(voxel1);
            }
            if(count==8){
                //保留了8个
                Constants.RootRemain++;
                if(!voxel.root){
                    //清空子体素
                    voxel.filled=0;
                    voxel.children.clear();
                    return 1;//对于已经合并子体素的子体素来说，需要向上汇报1
                }
                else{
                    voxel.filled=-2;//永远不要输出这个体素
                    voxel.children.clear();
                    return 8;//从根节点返回8，表示这个地方的加密体素需要删除，并恢复原有边界体素
                }
            }
            //如果子体素有被删除的，那么对于父体素来说，这个便不可用合并
            return 0;
        }
    }

    /**
     * 根据当前的voxel，寻找到它的所有待加密的children
     * 其实也就是获取一棵树的叶子节点，只不过它的叶子节点有个属性就是，filled=0
     * @param amf 集合
     * @param voxel
     * @return
     */
    public static void getAndCreateVoxelsByDFS(AMF amf,Voxel voxel){

        //找到为0的了，并且它是边界，就找出它的孩子，返回
        //filled=0的时候，它还没有生成子节点，所以directions.size()必然为0
        if(voxel.filled==0){

            voxel.filled=1;

            //默认情况下，我们在声明一个Voxel的时候不会生成其8个子节点
            //所以当我们找出来了没有进行加密的点的时候，它还处在未剖分的状态
            //需要获取到它的孩子
            voxel.get8Children();

            //遍历孩子节点，确认有无找到合适的点
            for(int i=0;i<voxel.children.size();i++){

                Voxel voxel1=voxel.children.get(i);

                //体素自己判断一下，和三角形的距离是否符合要求
                if (!voxel1.IsNearAttachedTriangle(amf)){

                    //默认为0，也就是说我们找到叶子节点，输出filled=0的即可
                    //而符合目前语句意味着距离比较远，那就设置为-1
                    //这意味着，在之后也不会继续在这个地方深入加密了
                    voxel1.filled = -1;

                    //被设置成了-1相当于要被清除出去~
                    //我们需要判断将这个voxel1删除过后，其他的voxel的direction的变化情况
                    //这个判定似乎相当复杂
                    voxel.adjustDirection(i);
                }
            }
            return;
        }
        //说明这个节点被剖分过了
        else if(voxel.filled==1){

            //在它的children里面找
            for(Voxel voxel1:voxel.children){
                //每一个children都需要找
                getAndCreateVoxelsByDFS(amf,voxel1);
            }
        }
        //说明这个点被加密过并且因为距离三角形太远
        else if(voxel.filled==-1 ){
            return;
        }


    }



    /**
     * 仅仅是查找
     * @param voxels
     * @param voxel
     */
    public static void getVoxelsByDFS(ArrayList<Voxel> voxels,Voxel voxel){

        //找到为0的了，就找出它的孩子，返回
        if(voxel.filled==0){
            voxels.add(voxel);
            return;
        }
        //说明这个节点被剖分过了
        else if(voxel.filled==1){
            //在它的children里面找
            for(Voxel voxel1:voxel.children){

                //每一个children都需要找
                getVoxelsByDFS(voxels,voxel1);
            }
        }
        //说明这个点被加密过并且离三角形太远,但是它的directions为0(说明了它在内部)
        else if(voxel.filled==-1 && voxel.directions.size()<=0){
            voxels.add(voxel);
            return;
        }
        else{
            return;
        }

    }


    /**
    调用方法：
     * 设置为空的体素，将由新生的小体素来填充
     * StartDFS(amf, voxel, t, 0);
     * 对体素 voxel 进行加密
     * @param amf 加密的结果应当存入amf
     * @param voxel 需要加密的体素
     * @param t     当前体素需要根据哪个三角形判断距离
     * @param depth 当前加密的深度
     *
    private void StartDFS(AMF amf, Voxel voxel, Triangle t, int depth) {

        //记录深度
        depth++;

        //当前已经到了加密的极限了
        if(depth==2){

            //需要判定这个体素是否应当被加入到amf里面
            if(t.CheckCoordinateNearTriangle(amf,voxel.coordinate.x,
                    voxel.coordinate.y,voxel.coordinate.z,voxel.a,voxel.a/2)){

                //是我们所需要的体素
                //将这个体素加入amf的voxelList集合
                amf.voxelList.addVoxel(voxel);
            }
            //否则，需要判断它是否在模型内
            else{

                //如果它在模型内部
                if(amf.testTriangle(new Coordinate(voxel.coordinate.x+voxel.a/2,
                        voxel.coordinate.y+voxel.a/2,voxel.coordinate.z+voxel.a/2))){

                    //加入内部的体素
                    voxel.color=amf.color;
                    amf.voxelList.addVoxel(voxel);
                }
            }

        }
        else{

            //如果距离三角形平面比较远
            if(!t.CheckCoordinateNearTriangle(amf,voxel.coordinate.x,
                    voxel.coordinate.y,voxel.coordinate.z,voxel.a,voxel.a/2)){

                //如果它在模型内部
                if(amf.testTriangle(new Coordinate(voxel.coordinate.x+voxel.a/2,
                        voxel.coordinate.y+voxel.a/2,voxel.coordinate.z+voxel.a/2))){

                    // 模型内部的体素需要加入列表，并标记
                    //另外需要更改一下颜色为amf的颜色
                    voxel.color=amf.color;
                    amf.voxelList.addVoxel(voxel);
                    //同时，应当停止向下DFS
                    return;
                }
                //否则，这是个是属于外部的，删了
                else{
                    return;
                }
            }

            //符合要求需要继续递归
            //那么这个voxel应当被加密
            //获取当前voxel的8个孩子
            ArrayList<Voxel> voxels=voxel.get8Children();

            //对于每一个孩子节点
            for(Voxel v:voxels){

                //深入递归
                StartDFS(amf,v,t,depth);
            }

        }
    }*/

}
