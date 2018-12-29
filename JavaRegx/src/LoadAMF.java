import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoadAMF {

	/**
	 我这里把读取和处理都放到这里来了方便调试
	 * @param module 模型
	 * @param file 文件对象
	 * @param finenessNum 精细度
	 * @param minLimitNum 最小限制
	 * */
	public void readFileAndSave(ArrayList<AMF> module, File file,
								float finenessNum,float minLimitNum) {

		Constants.addColor((float) (147/255.0), (float) (192/255.0),1f,1f);

		// 创建saxReader对象
		SAXReader reader = new SAXReader();
		// 通过read方法读取一个文件 转换成Document对象
		Document document = null;
		try {
			document = reader.read(file);
		} catch (DocumentException e) {
			e.printStackTrace();
		}
		//获取根节点元素对象
		Element node = document.getRootElement();
		List<Element> objects = node.elements("object");

		//读取每一个object
		for(Element object:objects){

			//每一个object，都应当是一个amf对象
			//我们的体素化应当针对每一个amf对象单独进行
			//amf应当持有一个VoxelList
			AMF amf=new AMF();

			//如果objColor不为空，那么它的color将附属于子元素
			Element objColor = object.element("color");

			//这个object有颜色信息
			if(objColor!=null){

				float[] rgba=LoadHelper.getColor(objColor);
				//将这个颜色加到颜色集中
				Constants.addColor(rgba[0],rgba[1],rgba[2],rgba[3]);
				//amf中的颜色只存储下标
				amf.setColor((short)Constants.colors.indexOf(new Color(rgba[0],rgba[1],rgba[2],rgba[3])));
			}

			Element mesh=object.element("mesh");

			//处理点,读入的点存入当前的amf
			LoadHelper.GenerateVertices(amf,mesh.element("vertices"));

			//处理三角面，读入的三角面存入当前的amf
			LoadHelper.GenerateVolume(amf,mesh.element("volume"));

			//读取完一个object，它应当被存入列表中
			module.add(amf);
		}

		Workbench.updateStatus("正在表面体素化...");

		//我们需要算出整个模型的体素大小
		LoadHelper.GenerateVoxelSize(module,finenessNum,minLimitNum);

		int globalIntAOffset=(int)Constants.amfa*4+10;

		//至此，我们读出了所有的object
		for(AMF amf:module){

			Workbench.updateStatus("请求内存...");
			//每一个amf对象的体素应当单独生成
			//然后生成的数组隶属于这个amf
			byte[][][] voxelNa=new byte[amf.voxelList.LxNum+globalIntAOffset][amf.voxelList.LyNum+globalIntAOffset][amf.voxelList.LzNum+globalIntAOffset];
			amf.colors=new short[amf.voxelList.LxNum+globalIntAOffset][amf.voxelList.LyNum+globalIntAOffset][amf.voxelList.LzNum+globalIntAOffset];
			Workbench.updateStatus("请求内存完毕,开始表面体素化...");
			amf.voxels=StartVoxelization(amf,voxelNa);

		}
	}

	/**
	 * 体素化开始，这个函数主要进行边界体素化
	 * @param amf
	 * @param voxelNa   @return
     */
	private byte[][][] StartVoxelization(AMF amf, byte[][][] voxelNa) {

		//以下，开始表面体素化
		long startTime = System.currentTimeMillis();    //获取开始时间

		//取出一些值
		double amfa=amf.a;
		//最小最大值应当从全局变量里面找
		float minx=amf.minV.x,
				miny=amf.minV.y,
				minz=amf.minV.z;


//		//检测那个函数
//		amf.testTriangle(new Coordinate(30,30,30));
//		amf.testTriangle(new Coordinate(0,40,0));
//		amf.testTriangle(new Coordinate(10,10,10));

		//转换坐标系，将整个模型的坐标系转到第一象限
		//我们需要将它多扩展一层，从而方便外部体素填充时候的BFS
		amf.Transform((float) (minx-amfa*2),(float) (miny-amfa*2),(float) (minz-amfa*2));

		//计算三角面的 平面方程 和 三角面所在的最小包围盒的两个顶点
		amf.generateTriangle();

		int process = 0;

		//对每一个三角面进行遍历
		for (Triangle t : amf.volume) {

//			System.out.println("Inside:"+
//					t.IntersectTriangle(amf,new Coordinate(322,322,322),new Coordinate(1,1,1)));

			//从三角形的最小点所在体素开始遍历
			//到三角形的最大点所在体素，遍历完成
			//首先要确定三角形所在最小包围盒的体素范围
			//我们仅仅存了三角形的最小点和最大点
			//我们需要先确定最小点和最大点所在的体素
			VoxelNum voxelMax = Voxel.getVoxelNum(t.maxV.x, t.maxV.y, t.maxV.z, amfa);
			VoxelNum voxelMin = Voxel.getVoxelNum(t.minV.x, t.minV.y, t.minV.z, amfa);

			//存入Triangle以复用
			t.minNum=voxelMin;
			t.maxNum=voxelMax;

			//我们现在求到了所谓的体素范围
			//ok开始遍历
			//事实证明这种方法只限于平面和坐标平面不平行的情况
			//所以在这里，我们需要判断一下平面和坐标面是否平行
			//存入Triangle
			t.type = t.getParallelNum();

			for (int i = voxelMin.x; i <= voxelMax.x; i++) {

				for (int j = voxelMin.y; j <= voxelMax.y; j++) {

					for (int k = voxelMin.z; k <= voxelMax.z; k++) {

						//检查这一点是否“离三角形很近”
						if(t.CheckCoordinateNearTriangle(amf,i * amfa, j * amfa, k * amfa,amfa,
								0.5*amfa)){

							if(voxelNa[i][j][k]==Constants.MODEL_EMPTY){

								//设为边界
								voxelNa[i][j][k]=Constants.MODEL_EDGE;

								amf.voxelList.surfaceNum++;
							}

							//三角形的颜色赋值
							if(t.color>0) {
								//color信息放到相同坐标下的color数组里面
								amf.colors[i][j][k] = t.color;
							}
						}
//						else if(t.CheckCoordinateNearTriangle(amf,i * amfa, j * amfa, k * amfa,amfa,
//								Math.sqrt(3)*0.5*amfa)){
//
//							if(voxelNa[i][j][k]==Constants.MODEL_EMPTY){
//
//								//设为边界
//								voxelNa[i][j][k]=Constants.MODEL_EDGE;
//								amf.colors[i][j][k]=1;
//							}
//						}
					}
				}
			}

			//我们每次处理一个三角面片
			//都应该去更新一下进度条
			process++;
			Workbench.updateProgress(process,amf.volume.size());
		}

		long endTime = System.currentTimeMillis();    //获取开始时间
		amf.voxelList.setSurfaceTime(endTime-startTime);
		//ok 至此我们遍历完了所有的三角形
		//并且将三角形所在的空间内所有距离比较近的体素，加入了列表中(不重复的)
		//下面是不是该考虑内部体素化了？

		//内部填充化未开启，在这里就应该返回了
		if(!Constants.floodingEnabled)
			return voxelNa;

		//内部体素化
		Workbench.updateStatus("正在进行Flooding填充");

		//确认使用何种Flooding方式
		if (Constants.innerFloodEnabled)
			return FloodingInside(amf.voxelList,voxelNa);
		else
			return FloodingOutside(amf.voxelList,voxelNa);

	}

	/**
	 * 开始使用内部Flooding算法进行外部体素遍历
	 * @param voxelList 存储将要显示出来的体素信息
	 * @param voxelNa 体素数组
	 */
	private byte[][][] FloodingOutside(VoxelList voxelList, byte[][][] voxelNa) {

		long startTime = System.currentTimeMillis();    //获取开始时间

		//由于我们使用了数组，所以不再需要哈希表
		//数组，用于进行遍历的时候方便地进行上下左右前后6邻域的查找
//		int[][][] voxelNa=new int[lxNum+10][lyNum+10][lzNum+10];
//
//		//填充上已经存在的边体素
//		Iterator<Voxel> iterator=voxelList.voxels.iterator();
//		while(iterator.hasNext()) {
//			Voxel voxel=iterator.next();
//			voxelNa[voxel.voxelNum.x][voxel.voxelNum.y][voxel.voxelNum.z]=1;
//		}

//		floodingInside(amf,voxelList,voxelNa);
		/*
*/

		int tempCount=0;
		//还是用BFS吧...
		Queue<VoxelNum> searchs = new LinkedList<VoxelNum>();
		VoxelNum start=new VoxelNum(0,0,0);
		//这一点一定在边外
		voxelNa[0][0][0]=Constants.MODEL_OUTFLOOD_OUTER;
		//方向向量
		int[][] dir={{1,-1,0,0,0,0},{0,0,1,-1,0,0},{0,0,0,0,1,-1}};
		searchs.add(start);
		while (!searchs.isEmpty()){

			//取出头元素，相当于C里面的Queue.pop()
			VoxelNum now=searchs.poll();

			for(int i=0;i<6;i++){

				//i,j,k分别表示行进的方向
				//首先需要有一个越界判断
				int target_x=now.x+dir[0][i];
				int target_y=now.y+dir[1][i];
				int target_z=now.z+dir[2][i];

				//不能够超过我们的边界
				//lxNum+1处理，也就是边界扩展1
				if(target_x<0 || target_x>voxelList.LxNum+1 ||
						target_y<0 || target_y>voxelList.LyNum+1 ||
						target_z<0 || target_z>voxelList.LzNum+1){
					continue;
				}

				//于是我们可以得出在不超过边界的情况下，当前的坐标点
				//如果是1，便是这个是表面体素
				//不能够继续加入队列中
				if(voxelNa[target_x][target_y][target_z]==Constants.MODEL_EDGE ||
						//如果已经确定了这个点是外部体素，那就没必要继续加入队列中了
						voxelNa[target_x][target_y][target_z]==Constants.MODEL_OUTFLOOD_OUTER){
					continue;
				}

				//于是我们应当能得出当前需要加入队列中的点了
				VoxelNum next=new VoxelNum(target_x,target_y,target_z);

				//标记为-1，即外部体素
				voxelNa[target_x][target_y][target_z]=Constants.MODEL_OUTFLOOD_OUTER;

				tempCount++;

				//将这个点加入队列中
				//这样在下一轮的遍历中，我们就可以将其取出，赋值
				searchs.add(next);
			}
		}

		voxelList.innerNum=(voxelList.LxNum+1)*(voxelList.LyNum+1)*(voxelList.LzNum+1)
				-voxelList.surfaceNum-tempCount;

		//到这里，我们已经可以确信剩余0的部分就是内部体素
		long endTime = System.currentTimeMillis();    //获取结束时间
		voxelList.setInnerTime(endTime-startTime);

		return voxelNa;
	}

	/**
	 * 内部体素填充式Flooding算法
	 * 内部填充的时候，我们将内部全部填充为2
	 * 整个算法结束后，内部是2，边界是1，外部是0
	 * @param voxelList Hash列表
	 * @param voxelNa 维护的矩阵
	 */
	private byte[][][] FloodingInside(VoxelList voxelList, byte[][][] voxelNa) {

		long startTime= System.currentTimeMillis();

		int lxNum=voxelList.LxNum;
		int lyNum=voxelList.LyNum;
		int lzNum=voxelList.LzNum;

		//我们尝试去寻找一个内部体素
		boolean searchFlag=false;
		int z_index=-1;
		//我们遍历模型最中央，那一条竖直的直线所经过的体素
		for(int z=0;z<lzNum;z++){
			//找到了边界体素
			if(voxelNa[lxNum/2][lyNum/2][z]>0){
				//到到了边界
				searchFlag=true;
			}
			else{
				//如果我们在已经经过边界的情况下，再遇到了0，表示我们遇到了内部体素
				//------------------------------------------------------
				//这种方法不严谨
				if(searchFlag){
					z_index=z;
					break;
				}
			}
		}

		//我们没有成功找到内部种子
		if(z_index<0){
			JOptionPane.showMessageDialog(null, "错误", "当前分辨率内部Flooding时无法找到采样点，" +
					"请重置和重新设置采样率", JOptionPane.ERROR_MESSAGE);
			return null;
		}

		//还是用BFS吧
		Queue<VoxelNum> searchs = new LinkedList<VoxelNum>();
		VoxelNum start=new VoxelNum(lxNum/2,lyNum/2,z_index);
		//这一点一定在模型内
		voxelNa[lxNum/2][lyNum/2][z_index]=Constants.MODEL_INFLOOD_INNER;
		//方向向量
		int[][] dir={{1,-1,0,0,0,0},{0,0,1,-1,0,0},{0,0,0,0,1,-1}};
		searchs.add(start);
		while (!searchs.isEmpty()){

			//取出头元素，相当于C里面的Queue.pop()
			VoxelNum now=searchs.poll();

			for(int i=0;i<6;i++){

				//i,j,k分别表示行进的方向
				//首先需要有一个越界判断
				int target_x=now.x+dir[0][i];
				int target_y=now.y+dir[1][i];
				int target_z=now.z+dir[2][i];

				//不能够超过我们的边界
				//lxNum+1处理，也就是边界扩展1
				if(target_x<0 || target_x>lxNum+1 ||
						target_y<0 || target_y>lyNum+1 ||
						target_z<0 || target_z>lzNum+1){
					continue;
				}

				//内部体素化中，我们需要得到是否是边界、内部体素的判别
				if(voxelNa[target_x][target_y][target_z] == Constants.MODEL_EDGE ||
						voxelNa[target_x][target_y][target_z] == Constants.MODEL_INFLOOD_INNER){
					continue;
				}

				//标记为内部填充模式下的，内部体素
				voxelNa[target_x][target_y][target_z]=Constants.MODEL_INFLOOD_INNER;

				//于是我们应当能得出当前需要加入队列中的点了
				VoxelNum next=new VoxelNum(target_x,target_y,target_z);
				//将这个点加入队列中
				//这样在下一轮的遍历中，我们就可以将其取出，赋值
				searchs.add(next);

				voxelList.innerNum++;
				//加入VoxelList中
//				voxelList.addVoxel(target_x,target_y,target_z, amf.a);

			}
		}
		//到这里，我们已经可以确信剩余0的部分就是内部体素
		long endTime = System.currentTimeMillis();    //获取结束时间
		voxelList.setInnerTime(endTime-startTime);

		return voxelNa;
	}

}
