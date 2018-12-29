import javax.swing.*;
import java.util.ArrayList;

/**
 * Created by 张伟 on 2016-06-08.
 */
public class MergeHelper {

    ArrayList<AMF> amfs;

    public MergeHelper(ArrayList<AMF> amfs){
        this.amfs=amfs;
    }

    public void run(){

        if(!Constants.floodingEnabled){

            JOptionPane.showMessageDialog(null, "错误", "尚未进行内部填充，运行出错！", JOptionPane.ERROR_MESSAGE);
            return;
        }

        //遍历每一个模型
        for(AMF amf:amfs){

            //我们需要计算一下当前的amf相对于整个坐标原点偏移了多少个体素
            int[] offset=VoxelOutput.GenerateOffset(amf);

            //遍历每一个节点
            for (int i = 0; i <= amf.voxelList.LxNum + 1; i++) {

                for (int j = 0; j <= amf.voxelList.LyNum + 1; j++) {

                    for (int k = 0; k <= amf.voxelList.LzNum + 1; k++) {

                        //如果碰到内部体素
                        if(VoxelOutput.judgeInnerVoxel(amf,i,j,k)){
                            //尝试扩展到更大的正方体
                            //无疑是从当前i,j,k到i+1,j+1,k+1，或者更多
                            ExpandVoxel(amf,i,j,k);

                        }
                    }
                }
            }
        }
    }

    /**
     * 从当前位置(i,j,k)向正方向扩展
     * @param amf
     * @param i
     * @param j
     * @param k
     */
    private void ExpandVoxel(AMF amf, int i, int j, int k) {

        //判断可否继续扩展
        int depth=1;
        boolean continueFlag=true;
        //标记为
        amf.voxels[i][j][k]=Constants.MODEL_MERGE;
        do {
            if (AbletoExpand(amf, i, j, k,depth)) {

                //将这一部分体素标记，它们将被视为不属于模型的部分
                AdjustAMFVoxels(amf,i,j,k,depth);

                //尝试对下一级进行判断
                depth++;
            }
            else {
                continueFlag=false;
            }
        }while (continueFlag);

        //最终，depth决定了合并了多少体素
        //depth=1  表示没有被合并
        MergeVoxel mergeVoxel=new MergeVoxel(i,j,k,depth);

        amf.mergeVoxels.add(mergeVoxel);

    }

    /**
     * 在体素合并之后，对amf里面对应的原有体素进行清除
     * @param amf
     * @param x
     * @param y
     * @param z
     * @param depth
     */
    private void AdjustAMFVoxels(AMF amf, int x, int y, int z, int depth) {

        for(int j=y;j<=y+depth;j++){
            for(int k=z;k<=z+depth;k++){
                //对体素进行标记
                amf.voxels[x+depth][j][k]=Constants.MODEL_MERGE;
            }
        }

        for(int i=x;i<=x+depth;i++){
            for(int k=z;k<=z+depth;k++){
                amf.voxels[i][y+depth][k]=Constants.MODEL_MERGE;
            }
        }

        for(int i=x;i<=x+depth;i++){
            for(int j=y;j<=y+depth;j++){
                amf.voxels[i][j][z+depth]=Constants.MODEL_MERGE;
            }
        }

    }

    /**
     * 判断从当前位置扩展depth宽度的一层，是否全部符合要求
     * @param amf
     * @param x
     * @param y
     * @param z
     * @param depth
     * @return
     */
    private boolean AbletoExpand(AMF amf, int x, int y, int z, int depth) {

        //x+depth,y,z
        //x,y+depth,z
        //x,y,z+depth
        //一共需要扩展3个面
        boolean flag=true;
        for(int j=y;j<=y+depth;j++){
            for(int k=z;k<=z+depth;k++){
                //当前体素不是是需要输出的
                if(!VoxelOutput.judgeInnerVoxel(amf,x+depth,j,k)){
                    flag=false;
                    break;
                }
            }
        }


        if(!flag) return false;

        for(int i=x;i<=x+depth;i++){
            for(int k=z;k<=z+depth;k++){
                if(!VoxelOutput.judgeInnerVoxel(amf,i,y+depth,k)){
                    flag=false;
                    break;
                }
            }
        }

        if(!flag) return false;

        for(int i=x;i<=x+depth;i++){
            for(int j=y;j<=y+depth;j++){
                if(!VoxelOutput.judgeInnerVoxel(amf,i,j,z+depth)){
                    flag=false;
                    break;
                }
            }
        }

        //只要flag为false，表示所扩展的面之中至少有一个体素不能被扩展
        if(!flag) return false;

//        System.out.println("xyz depth:"+x+","+y+","+z+","+depth);
        return true;
    }
}

/**
 * 合并后的体素
 */
class MergeVoxel{
    int i,j,k;
    int scale;

    public MergeVoxel(int i,int j,int k,int scale){
        this.i=i;
        this.j=j;
        this.k=k;
        this.scale=scale;
    }
}
