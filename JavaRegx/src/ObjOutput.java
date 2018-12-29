import com.sun.corba.se.impl.orbutil.ObjectUtility;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLClientInfoException;
import java.util.*;

/**
 * Created by 张伟 on 2016-06-01.
 */
public class ObjOutput {

    //obj文件所用到的所有小正方体
    Set<OutputVoxel> outputVoxels=new HashSet<>();
    //而需要输出到mtl的颜色信息存放于Constants.colors里面
    //存储所有的点，这些点是有顺序的
    ArrayList<Coordinate> coordinates=new ArrayList<>();
//    //存储所有用到的正方形的边
//    Set<Square> squares=new HashSet<>();

    ArrayList<AMF> module;
    BufferedWriter bw;
    BufferedWriter colorWriter;
    File file;

    public ObjOutput(BufferedWriter bw, ArrayList<AMF> module, File file){
        this.bw=bw;
        this.module=module;
        this.file=file;
    }

    public void output() throws IOException {

        //计算输出了多少体素
        Constants.VoxelCount=0;

        //首先，应该构造一个所有点集，和所有面集
        for(AMF amf:module) {

            //我们需要计算一下当前的amf相对于整个坐标原点偏移了多少个体素
            int[] offset = VoxelOutput.GenerateOffset(amf);

            //遍历
            for (int i = 0; i <= amf.voxelList.LxNum + 1; i++) {

                for (int j = 0; j <= amf.voxelList.LyNum + 1; j++) {

                    for (int k = 0; k <= amf.voxelList.LzNum + 1; k++) {

                        //需要判断这个体素是否需要输出
                        if (VoxelOutput.judgeMeshVoxels(amf, i, j, k)) {

                            //需要输出，那么需要计算实际的坐标
                            OutputVoxel outputVoxel = new OutputVoxel(
                                    (i + offset[0]) * amf.a, (j + offset[1]) * amf.a, (k + offset[2]) * amf.a,
                                    amf.a,amf.colors[i][j][k]
                            );

                            outputVoxels.add(outputVoxel);
                        }

                    }
                }
            }

            //寻找加密的节点
            for (Voxel voxel : amf.voxelList.voxels) {

                ArrayList<Voxel> children = new ArrayList<>();

                //获取到所有叶子
                DenseHelper.getVoxelsByDFS(children, voxel);

                //输出每一个符合条件的children
                for(Voxel child:children){

                    //需要输出，那么需要计算实际的坐标
                    OutputVoxel outputVoxel = new OutputVoxel(
                            child.coordinate.x + offset[0] * amf.a,
                            child.coordinate.y + offset[1] * amf.a,
                            child.coordinate.z + offset[2] * amf.a,
                            child.a, child.triangle.color
                    );

                    outputVoxels.add(outputVoxel);
                }
            }
        }
        //至此读出了所有正方形小体素
        //根据这些体素，就可以读出当前需要渲染哪一个正方体，这个正方体的宽度和颜色
        String fileName=file.getName();
        String name= fileName.substring(0,fileName.lastIndexOf('.'))+".mtl";
        colorWriter=new BufferedWriter(new FileWriter(new File(file.getParent()+"\\"+name)));

        //写入颜色吧
        for(int i=0;i<Constants.colors.size();i++){
            WriteColor(colorWriter,Constants.colors.get(i),i);
        }
        colorWriter.close();

        //写obj文件吧
        bw.write("mtllib "+name+"\r\n");

        //收集点
        for(OutputVoxel outputVoxel:outputVoxels){
            //获取这个体素的8个点
            Add8Coordinate(outputVoxel);
        }

        //写入点
        for(Coordinate coordinate:coordinates){

            WriteCoordinate(coordinate,bw);
        }

        //写入正方形面
        int voxelCount=0;
        for(OutputVoxel outputVoxel:outputVoxels){

            //需要注意写入的顺序
            //1号面   000   ===>    100   101   001
            //2号面   010   ===>    011   111   110
            //3号面   010   ===>    000   001   011
            //4号面   100   ===>    110   111   101
            //5号面   000   ===>    010   110   100
            //6号面   001   ===>    101   111   011

            //也许应该从对角点入手
            //000   100   101   001
            //000   010   110   100
            //000   001   011   010
            //这种，每次右移(循环的)并且取反
            //---------------------
            //111   011   001   101
            //111   101   100   110
            //111   110   010   011
            //这种，每次左移并且取反
            //---------------------
            bw.write("g cube"+(voxelCount++)+"\r\n");
            bw.write("usemtl color"+outputVoxel.color+"\r\n");
            WriteSquare(outputVoxel,0,bw);
            WriteSquare(outputVoxel,1,bw);
        }

        //于是，得到了一堆正方体的面，和它们的颜色
//        int squareCount=0;
//        for(Square square:squares){
//
//            bw.write("g cube"+(squareCount++)+"\r\n");
//            bw.write("usemtl color"+square.color+"\r\n");
//            bw.write("f "+square.v1+" "+square.v2+" "+square.v3+" "+square.v4+"\r\n");
//        }

    }

    public void Clear(){
        outputVoxels.clear();
    }

    /**
     * 向文件写入颜色
     * @param colorWriter
     * @param color
     * @param pos
     * @throws IOException
     */
    private void WriteColor(BufferedWriter colorWriter, Color color, int pos) throws IOException {
        //格式样例
//        newmtl color1
//        Kd 1.0 0 0
//        d 1
//        illum 1
        colorWriter.write("newmtl color"+pos+"\r\n");
        colorWriter.write("Kd "+color.r+" "+color.g+" "+color.b+"\r\n");
        colorWriter.write("d "+color.a+"\r\n");
        colorWriter.write("illum 1\r\n");
    }

    //输出8个点到Coordinate数组
    public void Add8Coordinate(OutputVoxel outputVoxel){

        //8个顶点
        AddCoordinate(new Coordinate(outputVoxel.x,              outputVoxel.y,              outputVoxel.z));
        AddCoordinate(new Coordinate(outputVoxel.x+outputVoxel.a,outputVoxel.y,              outputVoxel.z));
        AddCoordinate(new Coordinate(outputVoxel.x,              outputVoxel.y+outputVoxel.a,outputVoxel.z));
        AddCoordinate(new Coordinate(outputVoxel.x+outputVoxel.a,outputVoxel.y+outputVoxel.a,outputVoxel.z));
        AddCoordinate(new Coordinate(outputVoxel.x,              outputVoxel.y,              outputVoxel.z+outputVoxel.a));
        AddCoordinate(new Coordinate(outputVoxel.x+outputVoxel.a,outputVoxel.y,              outputVoxel.z+outputVoxel.a));
        AddCoordinate(new Coordinate(outputVoxel.x,              outputVoxel.y+outputVoxel.a,outputVoxel.z+outputVoxel.a));
        AddCoordinate(new Coordinate(outputVoxel.x+outputVoxel.a,outputVoxel.y+outputVoxel.a,outputVoxel.z+outputVoxel.a));
    }

    public void AddCoordinate(Coordinate coordinate){

        if(!coordinates.contains(coordinate)){
            coordinates.add(coordinate);
        }
    }

    /**
     * 写入4个点
     * @param type 是从000还是111开始，0=>000  1=>111
     */
    private void WriteSquare(OutputVoxel outputVoxel,int type,BufferedWriter bw) throws IOException {

        int x=type,y=type,z=type;

        OutputNum outputNum=new OutputNum(x,y,z);

        //首先应该打印出这一点
        int ori=getIndexByCoordinate(outputVoxel,outputNum.x,outputNum.y,outputNum.z);

        //出发点是000或者111
        for(int i=0;i<3;i++) {

            //每次需要先reset一下
            outputNum.Reset(type);
            //每次将不同位置设置成1
            outputNum.setDifferent(i);

            //存储另外三个点
            int[] coordinateNums=new int[3];
            coordinateNums[0]=getIndexByCoordinate(outputVoxel,outputNum.x,outputNum.y,outputNum.z);

            for(int j=1;j<3;j++) {

                //根据type，将xyz左移或者右移
                outputNum = GenerateMove(outputNum, type);

                coordinateNums[j]=getIndexByCoordinate(outputVoxel, outputNum.x,
                              outputNum.y, outputNum.z);
            }

            bw.write("f "+ori+" "+coordinateNums[0]+" "+coordinateNums[1]+" "+coordinateNums[2]+"\r\n");

        }

    }

    /**
     * outputNum里面的xyz整体位移并且取反
     * @param outputNum
     * @param type 0是右移1是左移
     * @return
     */
    private OutputNum GenerateMove(OutputNum outputNum, int type) {

        //右移
        if(type==0){
            int temp=outputNum.x;
            outputNum.x=outputNum.z;
            outputNum.z=outputNum.y;
            outputNum.y=temp;
            outputNum.x^=0x1;
            outputNum.y^=0x1;
            outputNum.z^=0x1;
            return outputNum;
        }
        //左移
        else {
            int temp=outputNum.x;
            outputNum.x=outputNum.y;
            outputNum.y=outputNum.z;
            outputNum.z=temp;
            outputNum.x^=0x1;
            outputNum.y^=0x1;
            outputNum.z^=0x1;
            return outputNum;
        }
    }

    /**
     * 获取到当前一点的下标,这一点下标会被+1，以符合obj文件格式
     * @param outputVoxel
     * @param x
     * @param y
     * @param z
     * @return
     */
    public int getIndexByCoordinate(OutputVoxel outputVoxel,int x,int y,int z) {
        return coordinates.indexOf(new Coordinate(outputVoxel.x + x * outputVoxel.a,
                outputVoxel.y + y * outputVoxel.a, outputVoxel.z + z * outputVoxel.a))+1;
    }

    /**
     * 输出坐标点到文件
     * @param coordinate
     * @param bw
     * @throws IOException
     */
    public void WriteCoordinate(Coordinate coordinate,BufferedWriter bw) throws IOException {
        String targetStr="v "+coordinate.x+" "+coordinate.y+" "+coordinate.z+"\r\n";
        bw.write(targetStr);
    }

}

class OutputNum{
    int x,y,z;
    public OutputNum(int x,int y,int z){
        this.x=x;
        this.y=y;
        this.z=z;
    }

    /**
     * 按照xyz的位置，将pos转换成与其他数据不一样的
     * @param pos
     */
    public void setDifferent(int pos){
        if(pos==0){
            x^=0x1;
        }
        else if(pos==1){
            y^=0x1;
        }
        else if(pos==2){
            z^=0x1;
        }
    }

    public void Reset(int value){
        x=y=z=value;
    }
}

class Square{
    int v1,v2,v3,v4;
    short color;
    public Square(int v1,int v2,int v3,int v4, short color){
        this.v1=v1;
        this.v2=v2;
        this.v3=v3;
        this.v4=v4;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Square){
            Square square=(Square)obj;
            return this.v1==square.v1 &&
                    this.v2==square.v2 &&
                    this.v3==square.v3 &&
                    this.v4==square.v4;
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return (int) (v1*31*31+v2*31+v3*13+v4);
    }

}


class OutputVoxel implements Comparator<OutputVoxel>{
    double x,y,z;//左下角点的坐标
    double a;//边长
    short color;//颜色

    public OutputVoxel(double x,double y,double z,double a,short color){
        this.x=x;
        this.y=y;
        this.z=z;
        this.a=a;
        this.color=color;
    }

    @Override
    public int compare(OutputVoxel o1, OutputVoxel o2) {
        if(o1.x!=o2.x){
            return (int) (o1.x-o2.x);
        }
        else if(o1.y!=o2.y){
            return (int) (o1.y-o2.y);
        }
        else if(o1.z!=o2.z){
            return (int) (o1.x-o2.z);
        }
        else {
            return (int) (o1.x-o2.z);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof OutputVoxel){
            OutputVoxel outputVoxel=(OutputVoxel)obj;
            return this.x==outputVoxel.x &&
                    this.y==outputVoxel.y &&
                    this.z==outputVoxel.z ;
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return (int) (x*31*31+y*31+z);
    }
}
