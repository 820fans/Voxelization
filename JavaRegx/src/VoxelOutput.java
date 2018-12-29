import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.sql.BatchUpdateException;
import java.util.ArrayList;

/**
 * 专门用于输出到文件
 */
public class VoxelOutput {

    ArrayList<AMF> module;
    BufferedWriter bw;
    File file;

    public VoxelOutput(BufferedWriter bw, ArrayList<AMF> module, File file){
        this.bw=bw;
        this.module=module;
        this.file=file;
    }

    public void output() throws IOException {

        //计算输出了多少体素
        Constants.VoxelCount=0;

        //写入颜色信息
        WriteSCADColorInfo(bw);

        //写入a的值
        bw.write("standard_a="+Constants.amfa+";\r\n");

        //写入划线函数信息
        //WriteDrawLineFunc(bw);

        //写入三角形的信息，构造三角面片模型
        WriteTriangleInfo(bw,module);

        //每一个object对应的amf单独输出
        if(Constants.innerMerged) {
            WriteArrayVoxelsByType(bw, module, Constants.OUTPUT_SURFACE);

            //写入合并的内部体素
            WriteMergeVoxels(bw,module);

            //输出加密信息点
            WriteAllDenseVoxels(bw, module);
        }
        else {
            WriteArrayVoxelsByType(bw, module, Constants.OUTPUT_ALL);

            //输出加密信息点
            WriteAllDenseVoxels(bw, module);
        }
    }

    private static void WriteTriangleInfo(BufferedWriter bw,ArrayList<AMF> module) throws IOException{
        //遍历amf
        for(AMF amf:module){

            bw.write("");
        }
    }

    /**
     * 挖洞的函数
     * @param x x
     * @param y y
     * @param z z
     * @param offset 偏移量
     * @return 返回是否需要挖洞
     */
    private static boolean DigHole(double x,double y,double z,int[] offset){
        return DigHole(x+offset[0]*Constants.amfa,
                y+offset[1]*Constants.amfa,
                z+offset[2]*Constants.amfa);
    }
    private static boolean DigHole(int i,int j,int k,int[] offset){
        return DigHole((i + offset[0])*Constants.amfa,
                (j + offset[1])*Constants.amfa,
                (k + offset[2])*Constants.amfa);
    }
    private static boolean DigHole(int i,int j,int k,int[] offset,double customA){
        return DigHole((i + offset[0])*customA,
                (j + offset[1])*customA,
                (k + offset[2])*customA);
    }
    private static boolean DigHole(double targetX,double targetY,double targetZ){
//        return targetX < 100 && targetY > 20 && targetZ < 100;
        return false;
    }

    /**
     * 写入合并的内部体素
     * @param bw
     * @param module
     * @throws IOException
     */
    public static void WriteMergeVoxels(BufferedWriter bw, ArrayList<AMF> module) throws IOException {

        //每一个节点
        for(AMF amf:module) {

            int tempCount = 0;

            //我们需要计算一下当前的amf相对于整个坐标原点偏移了多少个体素
            int[] offset=GenerateOffset(amf);

            //每一个amf对象的内部体素集合
            for(MergeVoxel mergeVoxel:amf.mergeVoxels){

                //挖洞
//                if(DigHole(mergeVoxel.i,mergeVoxel.j,mergeVoxel.k,offset,Constants.amfa*mergeVoxel.scale))
//                    continue;

                //如果这是一个新的数组
                if(tempCount==0){
                    bw.write("voxelpart_"+Constants.SCAD_PART_COUNT+"=[");
                }

                bw.write("[" + (mergeVoxel.i + offset[0]) + ","
                        + (mergeVoxel.j+offset[1]) + ","
                        + (mergeVoxel.k+offset[2]) + ","
                        + (amf.color-1) + ","
                        + mergeVoxel.scale + "]");

                Constants.VoxelCount++;
                Constants.VoxelInnerCount++;
                tempCount++;

                if(tempCount>=Constants.WRITE_ARRAY_SIZE_LIMIT){

                    Write9kMergeVoxels(bw,Constants.SCAD_PART_COUNT,1);
                    Constants.SCAD_PART_COUNT++;
                    tempCount=0;
                }
                else {
                    bw.write(",");
                }
            }

            //剩下的最后一部分没有到9k
            if(tempCount>0){
                Write9kMergeVoxels(bw,Constants.SCAD_PART_COUNT,2);
                Constants.SCAD_PART_COUNT++;
            }
        }
    }

    /**
     * 写出一个amf模型中所有的加密点
     * @param bw
     * @param module
     * @throws IOException
     */
    public static void WriteAllDenseVoxels(BufferedWriter bw, ArrayList<AMF> module) throws IOException {
        //每一个节点
        for(AMF amf:module) {

            int tempCount=0;

            //我们需要计算一下当前的amf相对于整个坐标原点偏移了多少个体素
            int[] offset=GenerateOffset(amf);

            //遍历每一个表面节点
            for(Voxel voxel:amf.voxelList.voxels) {

                ArrayList<Voxel> children = new ArrayList<>();

                //获取到表面节点的所有叶子
                DenseHelper.getVoxelsByDFS(children, voxel);

                //输出每一个符合条件的children
                for(Voxel child:children){

                    //挖洞
                    if(DigHole(child.coordinate.x,child.coordinate.y,child.coordinate.z,offset))
                        continue;

                    //如果这是一个新的数组
                    if(tempCount==0){
                        bw.write("voxelpart_"+Constants.SCAD_PART_COUNT+"=[");
                    }

                    bw.write("[" + child.coordinate.x + ","
                            + child.coordinate.y + ","
                            + child.coordinate.z + ","
                            + (child.triangle.color-1) + ","//0
                            + offset[0] + ","
                            + offset[1] + ","
                            + offset[2] + ","
                            + child.a + "]");

                    Constants.VoxelCount++;
                    Constants.VoxelDenseCount++;
                    Constants.DENSE_NUM++;
                    tempCount++;

                    if(tempCount>=Constants.WRITE_ARRAY_SIZE_LIMIT){

                        Write9kDenseVoxels(bw,Constants.SCAD_PART_COUNT,1);
                        tempCount=0;
                        Constants.SCAD_PART_COUNT++;
                    }
                    else {
                        bw.write(",");
                    }

                }
            }

            if(tempCount>0){
                Write9kDenseVoxels(bw,Constants.SCAD_PART_COUNT,2);
                Constants.SCAD_PART_COUNT++;
            }
        }


    }

    /**
     * 将数组中的内容输出到文件内
     * @param bw
     * @param module
     * @param outputType 输出表面还是全部
     * @throws IOException
     */
    public static void WriteArrayVoxelsByType(BufferedWriter bw, ArrayList<AMF> module, int outputType) throws IOException {

        for(AMF amf:module) {

            int tempCount=0;
            //我们需要计算一下当前的amf相对于整个坐标原点偏移了多少个体素
            int[] offset=GenerateOffset(amf);

            for (int i = 0; i <= amf.voxelList.LxNum + 1; i++) {

                for (int j = 0; j <= amf.voxelList.LyNum + 1; j++) {

                    for (int k = 0; k <= amf.voxelList.LzNum + 1; k++) {

                        //挖洞
                        if(DigHole(i,j,k,offset)) continue;

                        //判断是否需要输出
                        if (judgeNeedOutput(amf,i,j,k,outputType)) {

                            //如果这是一个新的数组
                            if (tempCount == 0) {
                                bw.write("voxelpart_" + Constants.SCAD_PART_COUNT + "=[");
                            }

                            WriteSimpleArrayItem(bw, i + offset[0], j + offset[1], k + offset[2],
                                    (getColor(amf, i, j, k) - 1));

                            Constants.VoxelCount++;
                            tempCount++;

                            if (tempCount >= Constants.WRITE_ARRAY_SIZE_LIMIT) {

                                Write9kArrayLoop(bw, Constants.SCAD_PART_COUNT, 1);
                                tempCount = 0;
                                Constants.SCAD_PART_COUNT++;
                            } else {
                                bw.write(",");
                            }
                        }

                    }
                }
            }

            //剩下的最后一部分没有到9k
            if(tempCount>0){
                Write9kArrayLoop(bw,Constants.SCAD_PART_COUNT,2);
                Constants.SCAD_PART_COUNT++;
            }
        }


    }

    /**
     * 判断体素是否需要被输出
     * @param amf
     * @param i
     * @param j
     * @param k
     * @param outputType
     * @return
     */
    public static boolean judgeNeedOutput(AMF amf, int i, int j, int k,int outputType) {

        //需要判断这个点是否需要输出
        if(outputType==Constants.OUTPUT_ALL) {
            return judgeMeshVoxels(amf, i, j, k);
        }
        else if(outputType==Constants.OUTPUT_SURFACE){
            return judgeSurfaceVoxel(amf, i, j, k);
        }

        return false;
    }

    /**
     * 写入单个数组元素
     * @param bw
     * @param i
     * @param j
     * @param k
     * @param color
     * @throws IOException
     */
    public static void WriteSimpleArrayItem(BufferedWriter bw, int i, int j, int k, int color) throws IOException {
        bw.write("[" + i + ","
                + j + ","
                + k  + ","
                + color + "]");
    }


    /**
     * 写入9k的融合体素
     * @param bw
     * @param partCount
     * @param offset
     * @throws IOException
     */
    private static void Write9kMergeVoxels(BufferedWriter bw, int partCount, int offset) throws IOException {

        if(offset==2)
            bw.write("[]];\r\n");
        else if(offset==1)
            bw.write("];\r\n");

        bw.write("for(i = [0:len(voxelpart_"+partCount+")-"+offset+"])\n{\n" +
                "    color(color_vec[voxelpart_"+partCount+"[i][3]])translate(" +
                "[voxelpart_"+partCount+"[i][0]*standard_a," +
                "voxelpart_"+partCount+"[i][1]*standard_a," +
                "voxelpart_"+partCount+"[i][2]*standard_a]){" +
                "cube([standard_a*voxelpart_"+partCount+"[i][4]," +
                "standard_a*voxelpart_"+partCount+"[i][4]," +
                "standard_a*voxelpart_"+partCount+"[i][4]]);}\n" +
                "}\r\n");

    }

    /**
     * 写入9k加密体素
     * @param bw
     * @param partCount
     * @param offset
     * @throws IOException
     */
    public static void Write9kDenseVoxels(BufferedWriter bw, int partCount, int offset) throws IOException {

        if(offset==2)
            bw.write("[]];\r\n");
        else if(offset==1)
            bw.write("];\r\n");

        bw.write("for(i = [0:len(voxelpart_"+partCount+")-"+offset+"])\n{\n" +
                "    color(color_vec[voxelpart_"+partCount+"[i][3]])translate(" +
                "[voxelpart_"+partCount+"[i][0]+voxelpart_"+partCount+"[i][4]*standard_a," +
                "voxelpart_"+partCount+"[i][1]+voxelpart_"+partCount+"[i][5]*standard_a," +
                "voxelpart_"+partCount+"[i][2]+voxelpart_"+partCount+"[i][6]*standard_a]){" +
                "cube([voxelpart_"+partCount+"[i][7],voxelpart_"+
                partCount+"[i][7],voxelpart_"+partCount+"[i][7]]);}\n" +
                "}");
    }

    /**
     * 写入9k个体素，这些体素来自数组，而非加密或者合并的
     * @param bw
     * @param partCount
     * @param offset
     * @throws IOException
     */
    public static void Write9kArrayLoop(BufferedWriter bw, int partCount, int offset) throws IOException {

        if(offset==2)
            bw.write("[]];\r\n");
        else if(offset==1)
            bw.write("];\r\n");

        bw.write("for(i = [0:len(voxelpart_"+partCount+")-"+offset+"])\n{\n" +
                "    color(color_vec[voxelpart_"+partCount+"[i][3]])translate(" +
                "[voxelpart_"+partCount+"[i][0]*standard_a," +
                "voxelpart_"+partCount+"[i][1]*standard_a," +
                "voxelpart_"+partCount+"[i][2]*standard_a]){" +
                "cube([standard_a,standard_a,standard_a]);}\n" +
                "}\r\n");

    }

    /**
     * 将所有颜色信息输出到scad文件中成为一个数组
     * @param bw
     * @throws IOException
     */
    public static void WriteSCADColorInfo(BufferedWriter bw) throws IOException {

        //写入颜色数组
        bw.write("color_vec=[");
        for(int i=0;i<Constants.colors.size();i++){
            bw.write("[" + Constants.colors.get(i).r + ","
                    + Constants.colors.get(i).g + ","
                    + Constants.colors.get(i).b + ","
                    + Constants.colors.get(i).a +"]");
            if(i!= Constants.colors.size()-1){
                bw.write(",");
            }
        }
        bw.write("];\r\n");
    }

    /**
     * 判断是否是边界体素
     * @param amf
     * @param i
     * @param j
     * @param k
     * @return
     */
    public static boolean judgeSurfaceVoxel(AMF amf,int i,int j,int k){

        if(amf.voxels[i][j][k]==Constants.MODEL_EDGE){
            Constants.VoxelSurfaceCount++;
            return true;
        }

        return false;
    }

    /**
     * 判断是否是内部体素
     * @param amf
     * @param i
     * @param j
     * @param k
     * @return
     */
    public static boolean judgeInnerVoxel(AMF amf,int i,int j,int k){

        //如果开启了内部填充，我们需要判断Flooding的方式
        if(Constants.innerFloodEnabled){

            //如果用户选择了内部Flooding填充
            if(amf.voxels[i][j][k]==Constants.MODEL_INFLOOD_INNER){
                return true;
            }
        }
        else {
            //如果用户选择了外部Flooding填充
            if(amf.voxels[i][j][k]==Constants.MODEL_OUTFLOOD_INNER){
                return true;
            }
        }
        return false;

    }


    /**
     * 判断当前的体素是否需要输出
     * @param amf
     * @param i
     * @param j
     * @param k
     * @return
     */
    public static boolean judgeMeshVoxels(AMF amf,int i,int j,int k){

        //首先需要判断，用户是否进行了内部填充
        if(!Constants.floodingEnabled){

            //如果没有进行内部填充，只需要判断边界体素就可以了
            if(amf.voxels[i][j][k]==Constants.MODEL_EDGE){
                Constants.VoxelSurfaceCount++;
                return true;
            }

        }
        else {

            //如果开启了内部填充，我们需要判断Flooding的方式
            if(Constants.innerFloodEnabled){

                //如果用户选择了内部Flooding填充
                //即由内向外栅格法
                //需要输出的是
                if(amf.voxels[i][j][k]==Constants.MODEL_EDGE){
                    Constants.VoxelSurfaceCount++;
                    return true;
                }
                else if(amf.voxels[i][j][k]==Constants.MODEL_INFLOOD_INNER){
                    Constants.VoxelInnerCount++;
                    return true;
                }
            }
            else {
                //如果用户选择了外部Flooding填充
                //即由外向内栅格法
                //需要输出的是边界和内部
                if( amf.voxels[i][j][k]==Constants.MODEL_EDGE){
                    Constants.VoxelSurfaceCount++;
                    return true;
                }
                else if(amf.voxels[i][j][k]==Constants.MODEL_OUTFLOOD_INNER){
                    Constants.VoxelInnerCount++;
                    return true;
                }

            }
        }

        return false;
    }


    /**
     * 获取某一点核心网格体素的颜色
     * @param amf
     * @param i
     * @param j
     * @param k
     * @return
     */
    public static short getColor(AMF amf, int i, int j, int k){

        if(amf.colors[i][j][k]>0){
            return amf.colors[i][j][k];
        }
        //否则，需要判断一下是否是有颜色的内部体素
        else{
            //这个体素自己没有带颜色，那么要看一下amf是否带颜色
            if(amf.color>=0){
                return amf.color;
            }
            else {
                return -1;
            }
        }

    }

    /**
     * 计算一下当前amf相对于整个模型的原点偏移了多少
     * @param amf
     * @return
     */
    public static int[] GenerateOffset(AMF amf) {

        Coordinate ori=Constants.minV;
        Coordinate dst=amf.minV;

        int[] target=new int[3];
        target[0]= (int) ((dst.x-ori.x)/amf.a);
        target[1]= (int) ((dst.y-ori.y)/amf.a);
        target[2]= (int) ((dst.z-ori.z)/amf.a);

        return target;
    }

//    /**
//     * 是否限制要输出成OpenSCAD能读的文件-----弃用
//     * @return
//     */
//    private boolean StopByLimit(int count) {
//
//        //如果体素数量较大，并且我们开启了显示的flag
//        if(count>19980 && Constants.showInSCAD){
//            return true;
//        }
//
//        return false;
//    }
//
//    /**
//     * 输出加密后的体素
//     * @param voxel 体素
//     * @param offset 当前amf的偏移量
//     * @param bw 写入
//     * @throws IOException
//     */
//    public void WriteDenseVoxel(Voxel voxel, int[] offset, BufferedWriter bw) throws IOException {
//        String color="";
//        if(voxel.triangle.color>0) {
//            color = "color([" + Constants.colors.get(voxel.triangle.color).r
//                    + "," + Constants.colors.get(voxel.triangle.color).g
//                    + "," + Constants.colors.get(voxel.triangle.color).b
//                    + "," + Constants.colors.get(voxel.triangle.color).a + "])";
//        }
//
//        //这个注释是用于生成截面的
////        if(voxel.coordinate.y > 10) {
//            String targetStr = color + "translate(["
//                    + (voxel.coordinate.x + offset[0]*Constants.amfa) + ","
//                    + (voxel.coordinate.y + offset[1]*Constants.amfa)  + ","
//                    + (voxel.coordinate.z + offset[2]*Constants.amfa)  + "]){cube(["
//                    + voxel.a + "," + voxel.a + "," + voxel.a + "]);}\r\n";
//
//            bw.write(targetStr);
////        }
//    }
//
//    /**
//     * 将体素信息写入文件
//     * @param amf
//     * @param offset
//     *@param i
//     * @param j
//     * @param k
//     * @param bw     @throws IOException
//     */
//    private static void WriteVoxel(AMF amf, int[] offset, int i, int j, int k, BufferedWriter bw) throws IOException {
//        String color = "";
//
//        //如果这一点的颜色不为空，那么表示一定是表面体素
//        //因为无论是何种填充方式，能够进入WriteVoxel的一定是需要显示的体素
//        //这个条件通过，则是表面体素，我们的表面体素一定是会有颜色的
//        if(amf.colors[i][j][k]>0){
//            color="color(["+Constants.colors.get(amf.colors[i][j][k]).r
//                    +","+Constants.colors.get(amf.colors[i][j][k]).g
//                    +","+Constants.colors.get(amf.colors[i][j][k]).b
//                    +","+Constants.colors.get(amf.colors[i][j][k]).a+"])";
//        }
//        //否则，需要判断一下是否是有颜色的内部体素
//        else{
//            //这个体素自己没有带颜色，那么要看一下amf是否带颜色
//            if(amf.color>=0){
//                color="color(["+Constants.colors.get(amf.color).r
//                        +","+Constants.colors.get(amf.color).g
//                        +","+Constants.colors.get(amf.color).b
//                        +","+Constants.colors.get(amf.color).a+"])";
//            }
//            else {
//                color="";
//            }
//        }
//
////        if((j+offset[1])*amf.a>10) {
//        String targetStr = color + "translate(["
//                + (i + offset[0]) * amf.a + ","
//                + (j + offset[1]) * amf.a + ","
//                + (k + offset[2]) * amf.a + "]){cube(["
//                + amf.a + "," + amf.a + "," + amf.a + "]);}\r\n";
//
//            bw.write(targetStr);
////        }
//    }

}
