
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class AMF {
	ArrayList<Vertex> vertices; //所有点的集合--不重复
	ArrayList<Triangle> volume; //由三角面片构成的一个体
	ArrayList<Edge> edges;
	double a;//用于表示体素(小正方形的长)
	float Lx,Ly,Lz;      //三边长
	Coordinate maxV,minV;//用于表示最大，最小顶点

	//amf持有一个VoxelList对象，这个对象将在加密的时候用到
	VoxelList voxelList=new VoxelList();

	//每一个Object(amf)有可能会有颜色
	short color=-1;

	//每个Object(amf)持有一个自己核心网格的对象
	byte[][][] voxels;
	//颜色信息单独存放于colors三维数组
	short[][][] colors;

	//用于存放特征边所链接的三角形-----弃用
	Set<Triangle> featureTriangles=new HashSet<>();

	//用于存放被合并后的体素
	Set<MergeVoxel> mergeVoxels=new HashSet<>();

	public AMF(){		//为什么AMF前面不写void，public之类的
		//解释上面的疑问：1、这是构造方法，构造方法和普通方法不同，不具备返回值
		//public是权限标识，可以加也可以不加。如果你不加，编译器在编译的时候会自动给你加上friendly标识
		vertices = new ArrayList<Vertex>();
		volume = new ArrayList<Triangle>();
		edges=new ArrayList<Edge>();

	}

	public void setColor(short color){
		this.color=color;
	}

	/**
	 * 加入某个点(不可能重复的)
	 * @param c 点，包含x y z
     */
	void addVertex(Coordinate c){

		Vertex v =new Vertex();
		v.coordinate=c;
		vertices.add(v);
	}

	/**
	 * 计算三角面的 平面方程 和 三角面所在的最小包围盒的两个顶点
	 */
	public void generateTriangle() {

		//整理出平面和点
		for (Triangle t:volume ) {

			//计算三角形所在平面
			Coordinate p1=vertices.get(t.v1).coordinate;
			Coordinate p2=vertices.get(t.v2).coordinate;
			Coordinate p3=vertices.get(t.v3).coordinate;
			//Ax+By+Cz+D=0 的四个参数
			t.A = ( (p2.y-p1.y)*(p3.z-p1.z)-(p2.z-p1.z)*(p3.y-p1.y) );
			t.B = ( (p2.z-p1.z)*(p3.x-p1.x)-(p2.x-p1.x)*(p3.z-p1.z) );
			t.C = ( (p2.x-p1.x)*(p3.y-p1.y)-(p2.y-p1.y)*(p3.x-p1.x) );
			t.D = ( 0-(t.A*p1.x+t.B*p1.y+t.C*p1.z) );

			//计算三角形三个顶点所形成的最小包围盒，所以需要求出最小、最大的点
			t.getMinMaxCoordinate(p1,p2,p3);
		}
	}

	/**
	 * 判断一点在在模型内、外、上
	 * @param ori 要判断的点
	 * @return
     */
	public boolean testTriangle(Coordinate ori){

		int count1=0,count2=0;
		//检查每个三角形
		for(Triangle t:volume){

			//一条射线
			if(t.IntersectTriangle(this,ori,new Coordinate(1,1,1))){
				count1++;
			}

			//另一条射线
			if(t.IntersectTriangle(this,ori,new Coordinate(-1,-1,-1))){
				count2++;
			}
		}

		if(count1%2>0 && count2%2 >0){
//			System.out.println("模型内");
			return true;
		}
		else if(count1%2>0 || count2%2 >0){
//			System.out.println("模型上");
			return true;
		}
		else{
//			System.out.println("模型外");
			return false;
		}
	}

	/**
	 * New Method
	 * 判断这个位置的体素是不是靠近外边界的边界体素
	 * @param i
	 * @param j
	 * @param k
     * @return
     */
	public boolean IsOutsideEdgeVoxel(Voxel voxel,int i,int j,int k){
		//新生成一个
		voxel.directions=new HashSet<>();

		//首先它需要是一个边界体素
		if(voxels[i][j][k]!=Constants.MODEL_EDGE && voxels[i][j][k]!=Constants.MODEL_DENSE)
			return false;

		//方向向量
		int[][] dir={{1,-1,0,0,0,0},{0,0,1,-1,0,0},{0,0,0,0,1,-1}};

		for(int d=0;d<6;d++){

			//i,j,k分别表示行进的方向
			//首先需要有一个越界判断
			int target_x=i+dir[0][d];
			int target_y=j+dir[1][d];
			int target_z=k+dir[2][d];


			//不能够超过我们的边界
			//lxNum+1处理，也就是边界扩展1
			if(target_x<0 || target_x>voxelList.LxNum+1 ||
					target_y<0 || target_y>voxelList.LyNum+1 ||
					target_z<0 || target_z>voxelList.LzNum+1){
				continue;
			}

			//判断是否为边界靠外面一层
			//内部填充
			if(Constants.innerFloodEnabled){

				//与外部体素相连
				if(voxels[target_x][target_y][target_z]==Constants.MODEL_INFLOOD_OUTER){

					//与外部相邻的方向，应当被存下来
					voxel.directions.add(new Direction(dir[0][d],dir[1][d],dir[2][d]));
				}

			}
			//外部填充
			else {

				//与外部体素相连
				if(voxels[target_x][target_y][target_z]==Constants.MODEL_OUTFLOOD_OUTER){

					//与外部相邻的方向，应当被存下来
					voxel.directions.add(new Direction(dir[0][d],dir[1][d],dir[2][d]));
				}
			}
		}

		//经过6个方向的判断，可以确定是否是近邻外部的体素
		if(voxel.directions.size()>0) return true;

		return false;
	}

	//定义Vertex的属性，目前只含有coordinate
	class Vertex{
		Coordinate coordinate= new Coordinate();
	}

	/**
	 * 每加入一个三角形,会将这个三角形所在的三条边不重复地加入边集中
	 * 这条边包含的信息有两个顶点和两个三角形编号
	 *
	 * 可以证明，每次要添加的三角形的编号就是添加三角形之前的volume.size();
	 *
	 * 如果这个边包含于边集中，但是这个三角形并不存在于改边所链接的两个三角形之一
	 * 则将当前三角形编号加入到 边的 三角形链接中
	 *
	 * 所有三角形添加完毕的时候，不出意外的话，边集构建完毕，边集里面每个边对应链接的两个三角形也链接完毕
	 *
	 * 20160518 加入计算三角形所在平面的功能，存入ABCD
	 *
	 * @param t 要添加的三角形
	 */
	public void addTriangle(Triangle t) {

		//注释掉的部分，是用于对于特征边加密的
		//由于我们的加密没有用到特征边，所以这部分暂时注释

		//三角面片t包含3个顶点，将这三个顶点传入getNormal，将获得法向量
		//这个法向量属于这个Triangle，故赋值
//		t.fn=getNormal(vertices.get(t.v1).coordinate,
//				vertices.get(t.v2).coordinate,
//				vertices.get(t.v3).coordinate);


		//加入颜色的时候,我们需要判定amf是否存在颜色
		//amf如果有颜色，而且triangle无颜色，那么triangle的颜色应当继承于amf的
		if(t.color<0 && this.color>0){
			t.color=this.color;
		}

		volume.add(t);

//		//由三角形，提取出所有的Edge
//		Edge e1=new Edge(t.v1,t.v2);
//		Edge e2=new Edge(t.v1,t.v3);
//		Edge e3=new Edge(t.v2,t.v3);
//
//		//将三条边加入边集，同时附上这条边对应的三角形编号
//		int tnum=volume.size()-1;
//		System.out.println("Triangle:"+t.v1+","+t.v2);
//		addEdge(e1,tnum);
//		addEdge(e2,tnum);
//		addEdge(e3,tnum);

	}

	/**
	 * 构造边集，用于特征边识别
	 * @param tnum
     */
	public void generateFeatureEdge(int tnum){
		//三角面片t包含3个顶点，将这三个顶点传入getNormal，将获得法向量
		//这个法向量属于这个Triangle，故赋值
		Triangle t=volume.get(tnum);
		t.fn=getNormal(vertices.get(t.v1).coordinate,
				vertices.get(t.v2).coordinate,
				vertices.get(t.v3).coordinate);

		//由三角形，提取出所有的Edge
		Edge e1=new Edge(t.v1,t.v2);
		Edge e2=new Edge(t.v1,t.v3);
		Edge e3=new Edge(t.v2,t.v3);

		//将三条边加入边集，同时附上这条边对应的三角形编号
		addEdge(e1,tnum);
		addEdge(e2,tnum);
		addEdge(e3,tnum);
	}

	/**
	 * 用于特征边判断---------暂时不启用
	 * 添加边，和三角形的编号
	 * @param e
	 * @param tnum
	 */
	public void addEdge(Edge e,int tnum){
		//查找这个边所在边集里面的下标
		//这个边存在于边集，那么下标>=0
		int index=getEdgeIndex(e);

		//表明这个边存在于边集中
		if(index>=0){
			//既然存在于列表中，那么它的t1位是必然有数据的
			//我们只需要检查这个边的三角形标记位 t2是否存在

			//如果这t2边比0小，说明这一位还没有链接过
			//由于先来后到的关系(我们每次存的第一个链接t1必然是搜索到的比较小的三角形的编号)
			//我们的t1<t2始终满足
			if(edges.get(index).t2 < 0 ){
				//加入链接
				edges.get(index).t2=tnum;
				//可以算角度了
				edges.get(index).setAngle(this);
			}

			System.out.println("index"+index);
		}
		//这个边不存在于边集中
		else{
			//既然不存在，那么可以将当前的tnum给t1
			//再将e加入到边集里面
			e.t1=tnum;

			edges.add(e);
		}
	}

	private int getEdgeIndex(Edge e) {

		for(int i=0;i<edges.size();i++){
			Edge edge=edges.get(i);
			if(edge.p1==e.p1 && edge.p2==e.p2){
				return i;
			}
		}

		return -1;
	}

	/**
	 * 用于判断特征边====================暂时不启用
	 * 传入3个节点，分别为A B C,求出向量a=BC  b=BA ,法向量为a*b
	 * a2b3-a3b2)i  +  a3b1-a1b3)j  + a1b2-a2b1)k
	 * @param c1
	 * @param c2
	 * @param c3
	 * @return
	 */
	public Coordinate getNormal(Coordinate c1,Coordinate c2,Coordinate c3){

		//求出两个向量
		Coordinate a=new Coordinate(c3.x-c2.x, c3.y-c2.y, c3.z-c2.z);
		Coordinate b=new Coordinate(c1.x-c2.x, c1.y-c2.y, c1.z-c2.z);

		//a*b=(a2b3-a3b2)i  +  (a3b1-a1b3)j  + (a1b2-a2b1)k
		double i=a.y*b.z-a.z*b.y;
		double j=a.z*b.x-a.x*b.z;
		double k=a.x*b.y-a.y*b.x;
		//法向量长度
		double length=Math.sqrt(i*i+j*j+k*k);
		//法向量
		Coordinate c=new Coordinate((float)(i/length),(float)(j/length),(float)(k/length));

		return c;
	}

	/**
	 * 变换坐标系,目标点是(x,y,z)
	 * @param x 目标点x
	 * @param y 目标点y
     * @param z 目标点z
     */
	public void Transform(float x,float y,float z){
		//变换矩阵
		float[][] t={{1,0,0,-x},{0,1,0,-y},{0,0,1,-z},{0,0,0,1}};

		//对每一个节点
		for(int pos=0;pos<vertices.size();pos++){

			//用于和变换矩阵t相乘
			float[] b={vertices.get(pos).coordinate.x,
					vertices.get(pos).coordinate.y,
					vertices.get(pos).coordinate.z,1};

			//存储矩阵运算结果
			float[] result=new float[4];

			//每个节点进行坐标平移变换
			for(int i = 0;i < 4 ;i++)
			{
				float temp=0;
				for(int j=0;j<4;j++){

					temp+=t[i][j]*b[j];
				}
				result[i]=temp;
			}

			//将矩阵运算结果存储回列表
			vertices.get(pos).coordinate.setCoordinate(result[0],result[1],result[2]);
		}
	}
}


//读入三角形的时候，先将所有边处理出来
//每个边对应了两个三角形
//对每一个三角形，它的三条边必然都存在于边集里面
//由于每个边集对应着两个三角形，可以直接找出另一个三角形
class Edge{
	int p1,p2;//一条边包含的两个顶点
	int t1=-1,t2=-1;//一条边衔接的两个三角形，默认是-1，表示还没有与三角形的编号相链接
	double angle;//两个面的夹角

	/**
	 * 边构造方法，每个边由p1 p2两个顶点表示
	 * 这两个顶点经过处理，保证p1 <=p2 也就是每条边确保是 小编号点p1  大编号点p2
	 * @param p1
	 * @param p2
	 */
	public Edge(int p1,int p2){
		if(p1<=p2){
			this.p1=p1;
			this.p2=p2;
		}
		else{
			this.p1=p2;
			this.p2=p1;
		}
	}

	/**
	 * 判断是否为特征边
	 * @return
     */
	public boolean isFeatureEdge(){
		if(angle>((5/180.0f)*Math.PI)){
			return true;
		}
		else return false;
	}

	/**
	 * 计算两个三角形的夹角
	 * @param amf
     */
	public void setAngle(AMF amf){

		Coordinate fn1=amf.volume.get(t1).fn;
		Coordinate fn2=amf.volume.get(t2).fn;

		angle=Math.acos((fn1.dot(fn2))/(fn1.length()*fn2.length())) / Math.PI;

		System.out.println("INF:"+angle);
		if(angle>0.1f){
			amf.featureTriangles.add(amf.volume.get(t1));
			amf.featureTriangles.add(amf.volume.get(t2));
		}

	}

	/**判断两元素相等，方法重写
	 * 参考资料 http://blog.csdn.net/witsmakemen/article/details/7323604
	 */
	public boolean equals(Object obj) {
		if (obj instanceof Edge) {
			Edge e = (Edge) obj;
			return this.p1==e.p1 && this.p2==e.p2;
		}
		return super.equals(obj);
	}
}

/**
 * 三角边集合
 */
class Triangle{
	int v1,v2,v3; //三个顶点的编号
	double A,B,C,D; //三角形所在平面Ax+By+Cz+D=0的四个参数
	Coordinate minV,maxV;//包围盒的最小最大点
	VoxelNum minNum,maxNum; //包围盒在Constants.amfa体素大小之下的体素范围 体素的三个序号
	short color=-1;//三角形的颜色
	int type;//存储三角形所在平面是否是一个与坐标平面平行的
	//这个是该三角面片的法向量
	Coordinate fn=new Coordinate();

	public void setColor(short color){
		this.color=color;
	}

	//原版搬运
	//地址 http://www.cnblogs.com/graphics/archive/2010/08/09/1795348.html
	//https://en.wikipedia.org/wiki/Möller–Trumbore_intersection_algorithm
	// DirectX SDK的demo
	//------------------------------------------------------
	// Determine whether a ray intersect with a triangle
	// Parameters
	// orig: origin of the ray
	// dir: direction of the ray
	// v0, v1, v2: vertices of triangle
	// t(out): weight of the intersection for the ray
	// u(out), v(out): barycentric coordinate of intersection

	/**
	 * 判断一条射线是否经过一个三角面
	 * 这部分将用于点在模型内部的判断
	 * 由于我们不再使用点在模型内部的判断(testTriangle())，这部分在实际代码不再起作用
	 * @param amf
	 * @param orig
	 * @param dir
     * @return
     */
	boolean IntersectTriangle(AMF amf,Coordinate orig, Coordinate dir){
		float t,u,v;
		Coordinate cv0=amf.vertices.get(v1).coordinate;
		Coordinate cv1=amf.vertices.get(v2).coordinate;
		Coordinate cv2=amf.vertices.get(v3).coordinate;

		// E1
		Coordinate E1 = cv1.sub(cv0);

		// E2
		Coordinate E2 = cv2.sub(cv0);

		// P
		Coordinate P = dir.cross(E2);

		// determinant
		float det = E1.dot(P);

		//NOT CULLING
		if(det > -Constants.EPSILON && det < Constants.EPSILON)
			return false;

		float inv_det = 1.f / det;

		//calculate distance from V1 to ray origin
		Coordinate T = orig.sub(cv0);

		//Calculate u parameter and test bound
		u = T.dot(P) * inv_det;

		//The intersection lies outside of the triangle
		if(u < 0.f || u > 1.f)
			return false;

		//Prepare Q to test v parameter
		Coordinate Q = T.cross(E1);

		//Calculate V parameter and test bound
		v = dir.dot(Q) * inv_det;

		//The intersection lies outside of the triangle
		if(v < 0.f || u + v  > 1.f)
			return false;

		t = E2.dot(Q) * inv_det;

		if(t > Constants.EPSILON) { //ray intersection
//			*out = t;
			return true;
		}

		// No hit, no win
		return false;
	}

	/**
	 * 获取当前三角形所在的面片是否垂直于
	 * xOy  yOz   xOz   平面
	 * 如果平行于xOy平面，那么方程可以表示为Cz+D=0 也就是说和x,y的变化无关了
	 * @return 0,1,2,3 如果返回0表示这个平面不与任何一个坐标平面平行
     */
	public int getParallelNum(){

		//xOy平面
		if(A==0 && B==0){
			return 1;
		}
		//xOz平面
		else if(A==0 && C==0){
			return 2;
		}
		//yOz平面
		else if(B==0 && C==0){
			return 3;
		}

		return 0;

	}

	/**
	 * 求取三角形的最小包围盒
	 */
	public void getMinMaxCoordinate(Coordinate p1,Coordinate p2,Coordinate p3) {

		//求取点的极值,将各数值初始化
		float maxx=Integer.MIN_VALUE,maxy=Integer.MIN_VALUE,maxz=Integer.MIN_VALUE;
		float minx=Integer.MAX_VALUE,miny=Integer.MAX_VALUE,minz=Integer.MAX_VALUE;

		//记录极值
		maxx=getMax(p1.x,
				p2.x,
				p3.x);

		maxy=getMax(p1.y,
				p2.y,
				p3.y);

		maxz=getMax(p1.z,
				p2.z,
				p3.z);

		maxV=new Coordinate(maxx,maxy,maxz);
		//------------------------------------------

		minx=getMin(p1.x,
				p2.x,
				p3.x);

		miny=getMin(p1.y,
				p2.y,
				p3.y);

		minz=getMin(p1.z,
				p2.z,
				p3.z);

		minV=new Coordinate(minx,miny,minz);
	}

	/**
	 * 判断一个点的投影是否位于当前三角形内
	 * 要求当前三角形type>0
	 * @param amf 三角形属于哪一个amf
	 * @param x x坐标
	 * @param y y坐标
	 * @param z z坐标
     * @return
     */
	public boolean coordinateInParallelTriangle(AMF amf,double x,double y,double z){

		//OK我们先得出三角形三个点和P点的坐标
		//额考虑到P点如果这样取的话是有可能出现不封闭的平面的
		//讲道理应该计算当前体素映射到坐标平面的4个点是否在三角形内
		Coordinate A = amf.vertices.get(v1).coordinate;
		Coordinate B = amf.vertices.get(v2).coordinate;
		Coordinate C = amf.vertices.get(v3).coordinate;
		Coordinate P = new Coordinate(x,y,z);

		Vector2 v2A = Vector2.Coordinate2Vector2(A, type);
		Vector2 v2B = Vector2.Coordinate2Vector2(B, type);
		Vector2 v2C = Vector2.Coordinate2Vector2(C, type);
		Vector2 v2P = Vector2.Coordinate2Vector2(P, type);

		//我们通过构造一个实例(其实我们丝毫没有用到这个实例的数据(我是指x或y等))
		//只是为了利用他的一个IsPointInAngle功能
		Vector2 vector2 = new Vector2();

		//如果这个点在二维空间内的映射满足我们所需要的条件
		if (vector2.IsPointInTriangle(v2A, v2B, v2C, v2P)) {
			return true;
		}

		return false;
	}

	/**
	 * 检验当前体素和三角形的关系
	 * @param amf
	 * @param x
	 * @param y
	 * @param z
     * @param a
     * @return
     */
	public boolean CheckCoordinateNearTriangle(AMF amf,double x,double y,double z,double a,double d){

		//判断三角形的类型
		if(type>0){

			//平面三角形,在三角形内
			if(coordinateInParallelTriangle(amf,x+a/2,y+a/2,z+a/2)){
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
			double distance = getDistance(x+a/2,y+a/2,z+a/2);

			//如果距离在范围之内，这个体素将被标记上需要继续加密
			if(distance - d <=0){
				return true;
			}
		}

		return false;
	}

	//点到面的距离，设点坐标为P(x, y, z)，平面方程为ax+by+cz+d=0
	public double getDistance(double x,double y,double z)
	{
		return Math.abs(A*x+B*y+C*z+D) / Math.sqrt(A*A+B*B+C*C);
	}


	//获取三个数最大值
	public float getMax(float num1,float num2,float num3){

		float a=num1>num2?num1:num2;
		return a>num3?a:num3;
	}

	//获取三个数最小值
	public float getMin(float num1,float num2,float num3){

		float a=num1<num2?num1:num2;
		return a<num3?a:num3;
	}

	@Override
	public int hashCode() {
		return v1*31*31+v2*31+v3;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Triangle){
			Triangle triangle=(Triangle) obj;
			return this.v1==triangle.v1 &&
					this.v2==triangle.v2 &&
					this.v3==triangle.v3;
		}
		return super.equals(obj);
	}
}

/**
 * 顶点或者向量的集合
 */
class Coordinate{
	float x,y,z;

	Coordinate(){

	}

	public Coordinate(float x,float y,float z){
		this.x=x;
		this.y=y;
		this.z=z;
	}

	public Coordinate(double x,double y,double z){
		this.x=(float)x;
		this.y=(float)y;
		this.z=(float)z;
	}

	public void setCoordinate(float x,float y,float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	/**
	 * 点乘
	 * @param coordinate
	 * @return
     */
	public float dot(Coordinate coordinate){
		return this.x*coordinate.x+this.y*coordinate.y+this.z*coordinate.z;
	}

	/**
	 * 叉乘
	 * @param coordinate
	 * @return
     */
	public Coordinate cross(Coordinate coordinate){
		//a*b=(a2b3-a3b2)i  +  (a3b1-a1b3)j  + (a1b2-a2b1)k
		return new Coordinate(this.y*coordinate.z-this.z*coordinate.y,
				this.z*coordinate.x-this.x*coordinate.z,
				this.x*coordinate.y-this.y*coordinate.x);


	}

	/**
	 * 相减
	 * @param coordinate
	 * @return
     */
	public Coordinate sub(Coordinate coordinate){
		return new Coordinate(this.x-coordinate.x,
				this.y-coordinate.y,this.z-coordinate.z);
	}
	/**
	 * 当coordinate为向量时，求取长度
	 * @return
	 */
	public double length(){
		return Math.sqrt(x*x+y*y+z*z);
	}

	@Override
	public int hashCode() {
		return (int) (x*31*31+y*31+z);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Coordinate) {
			Coordinate coordinate = (Coordinate) obj;
			return this.x==coordinate.x &&
					this.y==coordinate.y &&
					this.z==coordinate.z ;
		}
		return super.equals(obj);
	}

	@Override
	public String toString() {
		return x+","+y+","+z;
	}
}

/**
 * 用于计算二维向量的
 */
class Vector2{
	float x,y;

	public Vector2(){

	}

	public Vector2(float x,float y){
		this.x=x;
		this.y=y;
	}

	public Vector2 add(Vector2 vector2){
		return new Vector2(this.x+vector2.x,
				this.y+vector2.y);
	}

	public Vector2 sub(Vector2 vector2){
		return new Vector2(this.x-vector2.x,
				this.y-vector2.y);
	}

	//我在找资料的时候发现大多数是C++写的
	//其实可以考虑学一些C++(虽然我都还没熟悉)，因为这个对于提高效率真的是高了不止一点半点
	//http://blog.csdn.net/tsinfeng/article/details/5871043
	/**
	 * 向量点乘
	 * @param v1
	 * @param v2
     * @return
     */
	public float Vector2Dot(Vector2 v1,Vector2 v2){
		return v1.x*v2.x+v1.y*v2.y;
	}

	public float Vector2Cross(Vector2 v1,Vector2 v2){
		return v1.x * v2.y - v1.y * v2.x;
	}

	public boolean IsSameSide(Vector2 A, Vector2 B,Vector2 C, Vector2 P)
	{
		Vector2 AB = B.sub(A);
		Vector2 AC = C.sub(A);
		Vector2 AP = P.sub(A);

		float f1 = Vector2Cross(AB, AC);
		float f2 = Vector2Cross(AB, AP);

		// f1 and f2 should to the same direction
		return f1*f2 >= 0.0f;
	}

	public boolean IsPointInTriangle(Vector2 A, Vector2 B, Vector2 C, Vector2 P)
	{
		return IsSameSide(A, B, C, P) &&
				IsSameSide(B, C, A, P) &&
		        IsSameSide(C, A, B, P);
	}

	public boolean IsPointInAngle(Vector2 A, Vector2 B, Vector2 C, Vector2 P)
	{
		return IsSameSide(A, B, C, P) && IsSameSide(B, C, A, P);
	}

	/**
	 //我们需要将坐标从三维变化到二维里面，加速计算
	 * 将三维坐标映射到二维坐标之中
	 * @param coordinate 三维坐标
	 * @param type 二维坐标
     * @return
     */
	public static Vector2 Coordinate2Vector2(Coordinate coordinate,int type){

		Vector2 vector2 = null;

		switch (type) {
			//xOy平面
			case 1:
				//在xOy平面平行的情况下
				//我们需要判断当前哪些点位于三角形内
				//==============================
				//事实上，我们只需要将这个三维问题映射到该平面所在的二维平面就哦了
				//http://www.tuicool.com/articles/JNVreuU
				//二维平面上判断点在三角形内的最优算法
				//==============================

				//于是我们尝试计算二维向量
				vector2=new Vector2(coordinate.x,coordinate.y);

				break;
			//xOz平面
			case 2:

				vector2=new Vector2(coordinate.x,coordinate.z);
				break;
			//yOz平面
			case 3:

				vector2=new Vector2(coordinate.y,coordinate.z);
				break;
		}

		return vector2;
	}
}


/**
 * 颜色类
 */
class Color{
	float r,g,b,a;

	public Color(float r,float g,float b,float a){
		this.r=r;
		this.g=g;
		this.b=b;
		this.a=a;
	}


	/**判断两元素相等，方法重写
	 * 参考资料 http://blog.csdn.net/witsmakemen/article/details/7323604
	 */
	public boolean equals(Object obj) {
		if (obj instanceof Color) {
			Color color = (Color) obj;
			return this.r==color.r && this.g==color.g &&
					this.b==color.b && this.a==color.a;
		}
		return super.equals(obj);
	}
}