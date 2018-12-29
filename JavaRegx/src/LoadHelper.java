import org.dom4j.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * 用于辅助amf信息读入
 */
public class LoadHelper {


    /**
     * 处理所有三角面
     * @param amf
     * @param volume
     */
    public static void GenerateVolume(AMF amf, Element volume) {

        List<Element> triangles=volume.elements("triangle");
        for(Element triangle:triangles){
            //三角面片的颜色
            Element triangleColor = triangle.element("color");

            Triangle t = new Triangle();
            t.v1 = Integer.valueOf((String) triangle.element("v1").getData());
            t.v2 = Integer.valueOf((String) triangle.element("v2").getData());
            t.v3 = Integer.valueOf((String) triangle.element("v3").getData());

            //这个三角形有颜色信息
            if(triangleColor!=null){

                float[] rgba=getColor(triangleColor);
//				t.setColor(rgba[0],rgba[1],rgba[2],rgba[3]);

                //将这个颜色加到颜色集中
                Constants.addColor(rgba[0],rgba[1],rgba[2],rgba[3]);

                //amf中的颜色只存储下标
                t.setColor((short)Constants.colors.indexOf(new Color(rgba[0],rgba[1],rgba[2],rgba[3])));
            }

            amf.addTriangle(t);
        }
    }

    /**
     * 处理所有点vertex
     * @param amf
     * @param vertices
     */
    public static void GenerateVertices(AMF amf, Element vertices) {
        List<Element> vertexes=vertices.elements("vertex");

        //求取点的极值,将各数值初始化
        float maxx = Integer.MIN_VALUE, maxy = Integer.MIN_VALUE, maxz = Integer.MIN_VALUE;
        float minx = Integer.MAX_VALUE, miny = Integer.MAX_VALUE, minz = Integer.MAX_VALUE;

        //遍历每一个vertex
        for(Element vertex:vertexes){
            //我们暂时不考虑点的颜色
//			Element vertexColor = vertex.element("color");

            //我们取得顶点
            Element coordinates=vertex.element("coordinates");

            Coordinate c = new Coordinate();
            c.x = Float.valueOf((String) coordinates.element("x").getData());
            c.y = Float.valueOf((String) coordinates.element("y").getData());
            c.z = Float.valueOf((String) coordinates.element("z").getData());

            //记录极值
            maxx = maxx > c.x ? maxx : c.x;
            maxy = maxy > c.y ? maxy : c.y;
            maxz = maxz > c.z ? maxz : c.z;

            minx = minx < c.x ? minx : c.x;
            miny = miny < c.y ? miny : c.y;
            minz = minz < c.z ? minz : c.z;

            //这个点有颜色信息
//			if(vertexColor!=null){
//
//				float[] rgba=getColor(vertexColor);
//				c.setColor(rgba[0],rgba[1],rgba[2],rgba[3]);
//			}

            amf.addVertex(c);//将数据放入amf文件中
        }

        //求出这个Object的宽度
        float Lx, Ly, Lz;
        Lx = maxx - minx;
        Ly = maxy - miny;
        Lz = maxz - minz;

        System.out.println("模型大小："+Lx+","+Ly+","+Lz);
        //存储到amf对象中
        amf.minV=new Coordinate(minx,miny,minz);
        amf.maxV=new Coordinate(maxx,maxy,maxz);
        amf.Lx=Lx;amf.Ly=Ly;amf.Lz=Lz;
    }

    /**
     * 从一个color节点中获取颜色信息
     * @param element
     * @return
     */
    public static float[] getColor(Element element){
        float[] rgba=new float[4];

        rgba[0]=Float.valueOf((String) element.element("r").getData());
        rgba[1]=Float.valueOf((String) element.element("g").getData());
        rgba[2]=Float.valueOf((String) element.element("b").getData());
        if(element.element("a")!=null)
            rgba[3]=Float.valueOf((String) element.element("a").getData());
        else rgba[3]=1;//没有定义透明度的情况下，我们认为透明度是1

        return rgba;
    }

    /**
     * 设置体素大小
     * @param module
     * @param finenessNum
     * @param minLimitNum
     */
    public static void GenerateVoxelSize(ArrayList<AMF> module, float finenessNum,
                                   float minLimitNum) {

        //求取点的极值,将各数值初始化
        float maxx = Integer.MIN_VALUE, maxy = Integer.MIN_VALUE, maxz = Integer.MIN_VALUE;
        float minx = Integer.MAX_VALUE, miny = Integer.MAX_VALUE, minz = Integer.MAX_VALUE;

        //我们遍历每一个object
        for(AMF amf:module){

            //只要与里面的最大或者最小值比较就好了
            Coordinate cMax=amf.maxV;
            Coordinate cMin=amf.minV;

            //记录极值
            maxx = maxx > cMax.x ? maxx : cMax.x;
            maxy = maxy > cMax.y ? maxy : cMax.y;
            maxz = maxz > cMax.z ? maxz : cMax.z;

            minx = minx < cMin.x ? minx : cMin.x;
            miny = miny < cMin.y ? miny : cMin.y;
            minz = minz < cMin.z ? minz : cMin.z;
        }

        //求出体素宽度
        float Lx, Ly, Lz;
        Lx = maxx - minx;
        Ly = maxy - miny;
        Lz = maxz - minz;
        float aimL = getMax(Lx, Ly, Lz);
        //最小体素长宽高为最大长度的1/128
        double amfa = aimL / finenessNum;

        //在开启体素限制的情况下
        if(Constants.minLimitEnabled) {
            amfa = minLimitNum;
        }

        //这个整体的分辨率，应当由全局来保存
        Constants.addResolution(Lx,Ly,Lz,amfa);
        //整体的最小最大值
        Constants.addMinMaxCoordinate(minx,miny,minz,maxx,maxy,maxz);

        //统一所有amf的数据
        for(AMF amf:module) {

            //存体素统一的边长
            amf.a=amfa;

            //存储这个Object的分辨率 +0.5
            int LxNum= (int) Math.ceil((amf.Lx) /amfa);
            int LyNum= (int) Math.ceil((amf.Ly) /amfa);
            int LzNum= (int) Math.ceil((amf.Lz) /amfa);

            amf.voxelList.LxNum = LxNum;
            amf.voxelList.LyNum = LyNum;
            amf.voxelList.LzNum = LzNum;
        }
    }

    //获取三个数最大值
    public static float getMax(float num1,float num2,float num3){

        float a=num1>num2?num1:num2;
        return a>num3?a:num3;
    }
}
