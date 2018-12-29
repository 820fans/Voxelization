import com.sun.corba.se.spi.ior.WriteContents;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 *
 */
public class VoxOutput {

    private Set<VoxOut> voxOuts=new HashSet<>();
    ArrayList<AMF> module;
    DataOutputStream dos;
    File file;
    public VoxOutput(DataOutputStream dos, ArrayList<AMF> module, File file){
        this.dos=dos;
        this.module=module;
        this.file=file;
    }

    public void output() throws IOException {

        //求取点的极值,将各数值初始化
        int maxx = Integer.MIN_VALUE, maxy = Integer.MIN_VALUE, maxz = Integer.MIN_VALUE;

        for (AMF amf : module) {

            //我们需要计算一下当前的amf相对于整个坐标原点偏移了多少个体素
            int[] offset = VoxelOutput.GenerateOffset(amf);

            for (int i = 0; i <= amf.voxelList.LxNum + 1; i++) {

                for (int j = 0; j <= amf.voxelList.LyNum + 1; j++) {

                    for (int k = 0; k <= amf.voxelList.LzNum + 1; k++) {

                        //需要判断这个点是否需要输出
                        if (VoxelOutput.judgeMeshVoxels(amf, i, j, k)) {

                            //需要输出，那么需要计算实际的坐标
                            VoxOut outputVoxel = new VoxOut(
                                    (i + offset[0]), (j + offset[1]), (k + offset[2]), amf.colors[i][j][k]
                            );

                            if(i+offset[0] > maxx) maxx=i+offset[0];
                            if(j+offset[1] > maxy) maxy=j+offset[1];
                            if(k+offset[2] > maxz) maxz=k+offset[2];

                            voxOuts.add(outputVoxel);
                        }
                    }
                }
            }
        }

        //需要输出的体素数量
        int mainChildrenSize=24+16+4*voxOuts.size()+12+4*(Constants.colors.size()-1);

        //输出头部文件
        Write4Bytes("VOX ");
        Write4Bytes(150);
        Write4Bytes("MAIN");
        Write4Bytes(0);
        Write4Bytes(mainChildrenSize);

        //开始写Size
        WriteHeader("SIZE",12,0);
        Write4Bytes(maxx);
        Write4Bytes(maxy);
        Write4Bytes(maxz);

        WriteHeader("XYZI",4+4*voxOuts.size(),0);
        Write4Bytes(voxOuts.size());
        //写入点的信息
        for(VoxOut voxOut:voxOuts){
            WriteVoxel(voxOut.x,voxOut.y,voxOut.z,voxOut.colorIndex);
        }

        //写入PALETTE颜色信息
        WriteHeader("RGBA",4*(Constants.colors.size()-1),0);
        for(int i=1;i<Constants.colors.size();i++) {
            Color color=Constants.colors.get(i);
            WriteColor(color.r,color.g,color.b,color.a);
        }

    }

    public void Clear(){
        this.voxOuts.clear();
    }

    private void WriteColor(float r, float g, float b, float a) throws IOException {

        byte[] bytes=new byte[4];

        bytes[0]=new Integer((int) (r*255)).byteValue();
        bytes[1]=new Integer((int) (g*255)).byteValue();
        bytes[2]=new Integer((int) (b*255)).byteValue();
        bytes[3]=new Integer((int) (a*255)).byteValue();

        dos.write(bytes);
    }

    private void WriteVoxel(int x,int y,int z,int colorIndex) throws IOException {

        byte[] b=new byte[4];

        b[0]=new Integer(x).byteValue();
        b[1]=new Integer(y).byteValue();
        b[2]=new Integer(z).byteValue();
        b[3]=new Integer(colorIndex).byteValue();

        dos.write(b);
    }

    /**
     * 写入文件头
     * @param id
     * @param size
     * @param childrenSize
     * @throws IOException
     */
    public void WriteHeader(String id,int size,int childrenSize) throws IOException{
//        4 bytes : chunk id
//        4 bytes : size of chunk contents ( n )
//        4 bytes : total size of children chunks ( m )
        Write4Bytes(id);
        Write4Bytes(size);
        Write4Bytes(childrenSize);
    }

    public void Write4Bytes(String s) throws IOException {
        dos.writeBytes(s);
    }

    public void Write4Bytes(int a) throws IOException {

        byte[] b=new byte[4];

        Integer b3=0;
        Integer b2=0;
        Integer b1=0;
        Integer b0=0;

        b3=a/(256*256*256);
        a-=b3*(256*256*256);
        b2=a/(256*256);
        a-=b2*(256*256);
        b1=a/256;
        a-=b1*256;
        b0=a;

        b[0]=b0.byteValue();
        b[1]=b1.byteValue();
        b[2]=b2.byteValue();
        b[3]=b3.byteValue();
        dos.write(b);
    }

    public void WriteByte(){

    }
}

class VoxOut{
    int x,y,z,colorIndex;

    public VoxOut(int x,int y,int z,int colorIndex){
        this.x=x;
        this.y=y;
        this.z=z;
        this.colorIndex=colorIndex;
    }

    @Override
    public int hashCode() {
        return x*31*31+y*31+z*7+colorIndex;
    }

    @Override
    public boolean equals(Object obj) {

        if(obj instanceof VoxOut){
            VoxOut voxOut=(VoxOut) obj;
            return this.x==voxOut.x &&
                    this.y==voxOut.y &&
                    this.z==voxOut.z &&
                    this.colorIndex==voxOut.colorIndex;
        }

        return super.equals(obj);
    }
}
