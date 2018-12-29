

import java.awt.*;
import java.awt.Color;
import java.awt.event.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import java.awt.color.*;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;

public class Workbench {
    public static final String TAG="测试用";

    //与之前不同，我们应当维护一个模型数组，每个数组对应一个amf对象
    public static ArrayList<AMF> module=new ArrayList<AMF>();

    //窗口
    private JFrame frame;
    //文件选择器
    JFileChooser jfc = null;
    //进度条
    public static JProgressBar processBar;
    public static JTextArea statusValue;
    public static JTextField finenessNum;
    public static JTextField minLimitNum;
    public static JCheckBox minLimitEnable;
    public static JTextField DenseDepth;
    public static JCheckBox innerFlooding;
    public static JCheckBox outerFlooding;
    public static JCheckBox enableFlooding;

    //菜单
    public static JMenuItem openMenuItem;
    public static JMenuItem denseMenuItem;
    public static JMenuItem handleMenuItem;
    public static JMenuItem objMenuItem;
    public static JMenuItem voxMenuItem;
    public static JMenuItem resetMenuItem;
    public static JMenuItem mergeMenuItem;
    public static JMenuItem preferenceItem;

    //保存上一次打开记录
    //http://blog.csdn.net/hitxueliang/article/details/7657504
    Preferences pref = Preferences.userRoot().node(this.getClass().getName());

    //打开模式
    FileFilter fopen;
    FileFilter fwrite;

    public static void main(String[] args){
        //Windows样式
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | UnsupportedLookAndFeelException e1) {
            e1.printStackTrace();
        }

        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    Workbench bench = new Workbench(args);
                    bench.frame.setVisible(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public Workbench(String[] args){
        SetUI(args);
    }

    public void Restart(String[] args) throws IOException {
        StringBuilder cmd = new StringBuilder();
        cmd.append(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java ");
        for (String jvmArg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            cmd.append(jvmArg + " ");
        }
        cmd.append("-cp ").append(ManagementFactory.getRuntimeMXBean().getClassPath()).append(" ");
        cmd.append(Workbench.class.getName()).append(" ");
        for (String arg : args) {
            cmd.append(arg).append(" ");
        }
        Runtime.getRuntime().exec(cmd.toString());
        System.exit(0);
    }

    private void SetUI(String[] args) {

        //初始化界面
        InitLayout();

        //现在我们可以保存上一次打开的路径了！！！！
        //来自http://blog.csdn.net/hitxueliang/article/details/7657504
        //下面代码
        String lastPath = pref.get("lastPath", "");
        if(!lastPath.equals("")){
            jfc = new JFileChooser(lastPath);
        }
        else {
            jfc=new JFileChooser();
        }

        //事件监听器
        openMenuItem.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {

                //对输入的值进行判定
                String strFineness=Workbench.finenessNum.getText();
                String strLimit=Workbench.minLimitNum.getText();
                if(strFineness.equals("") || strLimit.equals("")){
                    JOptionPane.showMessageDialog(null, "提示", "输入不能为空", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                float finenessNum=Float.valueOf(strFineness);
                float minLimitNum=Float.valueOf(strLimit);

                //我们将这些数据读入全局变量中
                Constants.minLimitEnabled=Workbench.minLimitEnable.isSelected();
                Constants.innerFloodEnabled=Workbench.innerFlooding.isSelected();
                Constants.floodingEnabled=Workbench.enableFlooding.isSelected();

                if(finenessNum<=0 || minLimitNum<=0){
                    JOptionPane.showMessageDialog(null, "提示", "输入数据非法", JOptionPane.ERROR_MESSAGE);
                    return;
                }


                //“读入文件”按钮的事件
                File file=FileChooseHelper.OpenFileChoose(jfc,fopen,frame);
                if(file==null) return;

                //保存这一次打开的路径
                pref.put("lastPath",file.getPath());

                new Thread() {
                    public void run() {

                        //显示进度条
                        processBar.setVisible(true);
                        statusValue.setText("读入AMF文件");
                        openMenuItem.setEnabled(false);

                        long startTime = System.currentTimeMillis();    //获取开始时间

                        //读取并存入数据
                        LoadAMF loadAMF = new LoadAMF();
                        loadAMF.readFileAndSave(module,file,finenessNum,minLimitNum);

                        //允许用户点击  处理
                        denseMenuItem.setEnabled(true);
                        handleMenuItem.setEnabled(true);
                        objMenuItem.setEnabled(true);
                        voxMenuItem.setEnabled(true);
                        processBar.setValue(100);
                        //加载完啦，就隐藏进度条好了
                        processBar.setVisible(false);

                        long endTime = System.currentTimeMillis();    //获取结束时间
                        int surfaceNum=0,innerNum=0;
                        long surfaceTime=0,innerTime=0;
                        int count=0;
                        Workbench.updateStatus("体素化完成\r\n");
                        for(AMF amf:module) {
                            surfaceNum+=amf.voxelList.surfaceNum;
                            innerNum+=amf.voxelList.innerNum;
                            surfaceTime+=amf.voxelList.surfaceTime;
                            innerTime+=amf.voxelList.innerTime;

                            count++;
                            Workbench.showStatistics("第 "+count+" object的体素分辨率:" + amf.voxelList.LxNum + "*"
                                    + amf.voxelList.LyNum + "*" + amf.voxelList.LzNum +"\r\n"
                                    + " 表面体素数量：" + amf.voxelList.surfaceNum + "个"
                                    + " 内部体素数量：" + amf.voxelList.innerNum + "个"
                                    + " 整个模型包含三角面片数量 " + amf.volume.size() + " \r\n "
                                    + " 表面体素化用时：" + amf.voxelList.surfaceTime + "ms"
                                    + " 内部体素化用时：" + amf.voxelList.innerTime + "ms"
                                    + " 体素化用时：" + (amf.voxelList.surfaceTime + amf.voxelList.innerTime) + "ms\r\n\r\n");

                        }

                        Workbench.showStatistics("处理完成，包括读取文件时间一共用时：" + (endTime - startTime) + "ms"

                                + " 整个模型分辨率:" + Constants.lxNum + "*"
                                + Constants.lyNum + "*" + Constants.lzNum +"\r\n"
                                + " 统一体素大小：" + Constants.amfa
                                + " 表面体素数量：" + surfaceNum + "个"
                                + " 内部体素数量：" + innerNum + "个"
                                + " 表面体素化用时：" + surfaceTime + "ms"
                                + " 内部体素化用时：" + innerTime + "ms"
                                + " 体素化用时：" + (surfaceTime + innerTime) + "ms\r\n"
                        );

                        System.out.println("读取完成");
                        System.out.println("打开和处理文件用时："+(endTime-startTime)+"ms");
                    }
                }.start();

                //好了，折腾了这么久总算该输出了吧
                //我们要输出的文件存放在voxelList.voxels里面
                //我们吧这部分放在handleMenuItem里面吧

            }
        });

        //加密
        denseMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                new Thread() {
                    @Override
                    public void run() {

                        long startTime = System.currentTimeMillis();
                        //开始加密
                        DenseHelper denseHelper = new DenseHelper(module);
                        Workbench.showStatistics("正在加密表面...");
                        denseHelper.run();
                        int dense = 0;
                        for (AMF amf : module) {
                            dense += amf.voxelList.voxels.size();
                        }
                        long endTime = System.currentTimeMillis();

                        System.out.println("加密完成");
                        System.out.println("加密用时:" + (endTime - startTime) + "ms");
                        Workbench.showStatistics("加密用时:" + (endTime - startTime) + "ms");
                        Workbench.showStatistics("加密层级：" + Constants.DENSE_DEPTH + " 层");
                        Workbench.showStatistics("第一级加密体素 " + dense + " 个");
                        Workbench.showStatistics("加密体素合并数量 "+ Constants.RootRemain +" 个");
                        Workbench.showStatistics("后面的加密层级将逐层大量增加，输出到文件可得加密体素数量");

                        denseMenuItem.setEnabled(false);
                        voxMenuItem.setEnabled(false);
                    }
                }.start();

            }
        });

        //输出文件
        handleMenuItem.addActionListener(new ActionListener(){

            public void actionPerformed(ActionEvent e) {

                //好了，可以看到我在1min写出了以上代码
                //哈哈~~~~~~~~
                //其实是之前写的STL2AMF的时候 代码copy过来了
                String defaultSCADName="Convert.scad";
                File file = FileChooseHelper.SaveFileChooser(jfc,fwrite,frame,defaultSCADName);
                if(file==null) return;

                //输出文件
                OutputFile(0,file);
            }
        });

        objMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                String defaultObjName="Convert.obj";
                File file = FileChooseHelper.SaveFileChooser(jfc,fwrite,frame,defaultObjName);
                if(file==null) return;

                //输出文件
                OutputFile(1,file);

            }
        });

        voxMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                JOptionPane.showMessageDialog(frame.getContentPane(),
                        "模型边长不能大于126个体素!", "提示", JOptionPane.INFORMATION_MESSAGE);

                String defaultObjName="Convert.vox";
                File file = FileChooseHelper.SaveFileChooser(jfc,fwrite,frame,defaultObjName);
                if(file==null) return;

                //输出文件
                OutputFile(2,file);
            }
        });

        resetMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Reset();
                handleMenuItem.setEnabled(false);
                denseMenuItem.setEnabled(false);
                objMenuItem.setEnabled(false);
                voxMenuItem.setEnabled(false);
                openMenuItem.setEnabled(true);
                mergeMenuItem.setEnabled(true);
//                DenseDepth.setEnabled(false);
                Workbench.updateStatus("尚未读取文件");
                try {
                    Restart(args);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });

        mergeMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                new Thread() {
                    @Override
                    public void run() {

                        long startTime=System.currentTimeMillis();

                        MergeHelper mergeHelper=new MergeHelper(module);
                        mergeHelper.run();
                        Constants.innerMerged=true;

                        int voxelCount=0;
                        for(AMF amf:module){
                            for(MergeVoxel mergeVoxel:amf.mergeVoxels){
                                voxelCount++;
                            }
                        }

                        long endTime=System.currentTimeMillis();

                        Workbench.showStatistics("合并用时："+(endTime-startTime)+"ms");
                        Workbench.showStatistics("合并内部体素，产生新体素："+voxelCount+" 个");

                        denseMenuItem.setEnabled(false);
                        mergeMenuItem.setEnabled(false);
                        objMenuItem.setEnabled(false);
                        voxMenuItem.setEnabled(false);

                    }
                }.start();

            }
        });

        preferenceItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                JFrame d =new JFrame();
                d.setTitle("加密层级");
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                d.setBounds((screenSize.width-400)/2, (screenSize.height-200)/2, 400, 200);
                d.getRootPane().setWindowDecorationStyle(JRootPane.INFORMATION_DIALOG);
                d.setResizable(false);
                d.setVisible(true);
                frame.setEnabled(false);
                //增加事件监听
                d.addWindowListener(new Workbench.CloseHandler());
                d.getContentPane().setLayout(new FlowLayout(FlowLayout.LEFT,0,0));

                //布局
                JPanel panel=new JPanel();
                GridBagLayout gridbag = new GridBagLayout();
                panel.setLayout(gridbag);
                //控件
                JLabel denseLabel=new JLabel("加密层级");
                String[] values={"0","1","2","3","4"};
                JComboBox denseValue=new JComboBox(values);
                denseValue.setSelectedIndex(Constants.DENSE_DEPTH);
//
//                denseValue.addItemListener(new ItemListener() {
//                    @Override
//                    public void itemStateChanged(ItemEvent e) {
//                        Constants.DENSE_DEPTH=denseValue.getSelectedIndex();
//                    }
//                });
                denseValue.setBounds(0,0,100,30);
                JButton confirm=new JButton("确认");
                confirm.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Constants.DENSE_DEPTH=denseValue.getSelectedIndex();
                        System.out.println(Constants.DENSE_DEPTH);
                        frame.setEnabled(true);
                        d.dispose();
                    }
                });

                GridBagConstraints c = new GridBagConstraints();
                c.fill=GridBagConstraints.HORIZONTAL;
                c.insets=new Insets(20,50,0,20);
                c.gridx = 0; //x grid position
                c.gridy = 0; //y grid position
                c.weightx=1;
                gridbag.setConstraints(denseLabel, c); //设置标签的限制
                panel.add(denseLabel,c); //增加到内容面板

                c.gridx = 3; //x grid position
                c.gridy = 0; //y grid position
                gridbag.setConstraints(denseValue, c); //设置标签的限制
                panel.add(denseValue,c);

                c.gridx = 0; //x grid position
                c.gridy = 1; //y grid position
                gridbag.setConstraints(confirm, c); //设置标签的限制
                panel.add(confirm,c);

                d.add(panel);
            }
        });

    }

    private class CloseHandler extends WindowAdapter {
        public void windowClosing(final WindowEvent event) {
            frame.setEnabled(true);
        }
    }
    /**
     * 输出信息到文件
     * @param outputType 输出类型
     * @param file 文件
     */
    private void OutputFile(int outputType,File file) {

        new Thread(){

            @Override
            public void run() {

                long startTime = System.currentTimeMillis();    //获取开始时间

                handleMenuItem.setEnabled(false);
                objMenuItem.setEnabled(false);
                voxMenuItem.setEnabled(false);
                Workbench.showStatistics("正在写入文件...");
                BufferedWriter bw = null;
                DataOutputStream dos=null;
                try {
                    bw = new BufferedWriter(new FileWriter(file.getAbsolutePath()));
                    dos = new DataOutputStream(new FileOutputStream(file.getAbsolutePath()));

                    //输出scad
                    if(outputType==0) {

                        //如果没有对内部进行合并
                        VoxelOutput voxelOutput = new VoxelOutput(bw, module, file);
                        voxelOutput.output();
                    }
                    //输出obj
                    else if(outputType==1){
                        //输出obj
                        ObjOutput objOutput=new ObjOutput(bw,module,file);
                        objOutput.output();
                        objOutput.Clear();
                    }
                    else if(outputType==2){
                        //输出vox
                        VoxOutput voxOutput=new VoxOutput(dos,module,file);
                        voxOutput.output();
                        voxOutput.Clear();
                    }

                    dos.close();
                    bw.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                    System.out.println("Output Failed...");
                }


                long endTime = System.currentTimeMillis();    //获取结束时间
                System.out.println("写入文件完成");
                System.out.println("写入文件用时："+(endTime-startTime)+"ms");

                Workbench.showStatistics("写入文件完成");
                Workbench.showStatistics("写入文件用时："+(endTime-startTime)+"ms");
                Workbench.showStatistics("写入加密体素 "+Constants.DENSE_NUM+" 个");

                //输出总计体素
                Workbench.showStatistics("\r\n总计写入体素："+Constants.VoxelCount+" 个");
                Workbench.showStatistics("写入表面体素(以此为准)："+Constants.VoxelSurfaceCount+" 个");
                Workbench.showStatistics("写入内部体素(以此为准)："+Constants.VoxelInnerCount+" 个");
                Workbench.showStatistics("写入加密体素(以此为准)："+Constants.VoxelDenseCount+" 个");

                //输出完成之后，我们应当将某些值啊什么的都清的干干净净
                //这样，我们再一次打开的时候，可以继续转换另一个模型
                //我们不妨在最下面设置一个Reset()函数
                Reset();

                //不再允许用户点击  打印
                //意味着用户只要打开一个文件并且
                // 点击了 打印 按钮生成一个.scad之后，不再能够继续打印
                //直至用户再次打开一个新的amf文件
                denseMenuItem.setEnabled(false);
                openMenuItem.setEnabled(true);
            }
        }.start();
    }

    /**
     * 初始化界面
     */
    private void InitLayout() {

        // 设置窗口样式
        frame=new JFrame("体素化项目");
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new GridLayout(6,1,10,10));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //菜单栏
        JMenuBar menuBar = new JMenuBar();
        frame.setJMenuBar(menuBar);

        //文件
        JMenu fileMenu = new JMenu("文件");
        fileMenu.setFont(new Font("黑体", Font.PLAIN, 15));
        menuBar.add(fileMenu);
        openMenuItem = new JMenuItem("读取文件");
        fileMenu.add(openMenuItem);
        denseMenuItem=new JMenuItem("进行加密");
        denseMenuItem.setEnabled(false);
        fileMenu.add(denseMenuItem);
        handleMenuItem = new JMenuItem("输出scad");
        handleMenuItem.setEnabled(false);
        fileMenu.add(handleMenuItem);
        objMenuItem=new JMenuItem("输出obj");
        objMenuItem.setEnabled(false);
        fileMenu.add(objMenuItem);
        voxMenuItem=new JMenuItem("输出vox");
        voxMenuItem.setEnabled(false);
        fileMenu.add(voxMenuItem);

        //编辑
        JMenu resetMenu = new JMenu("编辑");
        resetMenu.setFont(new Font("黑体", Font.PLAIN, 15));
        menuBar.add(resetMenu);
        resetMenuItem  = new JMenuItem("重置");
        resetMenu.add(resetMenuItem);
        mergeMenuItem=new JMenuItem("合并体素");
        resetMenu.add(mergeMenuItem);

        //编辑
        JMenu toolMenu = new JMenu("设置");
        toolMenu.setFont(new Font("黑体", Font.PLAIN, 15));
        menuBar.add(toolMenu);
        preferenceItem  = new JMenuItem("加密层级");
        toolMenu.add(preferenceItem);

        //进行界面美化
        //参考 http://blog.csdn.net/zhai56565/article/details/8675327
        //参考 http://blog.sina.com.cn/s/blog_6f116c940101alna.html
        //初始化各种控件
        JPanel progressPanel = new JPanel();//进度条布局
        JPanel headPanel = new JPanel(); //状态栏布局
        JPanel searchTypePanel=new JPanel();//是使用内部Flooding还是外部Flooding
        JPanel floodingEnablePanel=new JPanel();//是否需要内部填充
        JPanel inputPanel = new JPanel(); //输入框组合布局
//        JPanel densePanel = new JPanel(); //输入框组合布局

        //页面第0个布局控件,它位于最上方
        GridLayout processLayout=new GridLayout(1,1,5,5);
        progressPanel.setLayout(processLayout);
        processBar= new JProgressBar();
        processBar.setBounds(20,0,200,10);
        processBar.setStringPainted(true);// 设置进度条上的字符串显示，false则不能显示
        processBar.setBackground(Color.BLUE);
        processBar.setVisible(false);
        progressPanel.add(processBar);
        frame.add(progressPanel);

        //页面第一个布局控件，
        GridLayout flowLayout1 = new GridLayout(1 , 1);
        headPanel.setLayout(flowLayout1);
        statusValue=new JTextArea("尚未读取文件");
        statusValue.setEditable(false);
        statusValue.setLineWrap(true);        //激活自动换行功能
        statusValue.setWrapStyleWord(true);   // 激活断行不断字功能
        headPanel.add(statusValue);
        frame.add(headPanel);

        JScrollPane jsp = new JScrollPane(statusValue);
        jsp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        headPanel.add(jsp, BorderLayout.CENTER);

        FlowLayout floodingEnableLayout=new FlowLayout(FlowLayout.CENTER,1,1);
        floodingEnablePanel.setLayout(floodingEnableLayout);
        enableFlooding = new JCheckBox("是否进行内部填充", true);
        enableFlooding.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(enableFlooding.isSelected()){
                    innerFlooding.setEnabled(true);
                    outerFlooding.setEnabled(true);
                }
                else {
                    innerFlooding.setEnabled(false);
                    outerFlooding.setEnabled(false);
                }
            }
        });
        floodingEnablePanel.add(enableFlooding);
        frame.add(floodingEnablePanel);

        FlowLayout searchTypeLayout=new FlowLayout(FlowLayout.CENTER,1,1);
        searchTypePanel.setLayout(searchTypeLayout);
        ButtonGroup btgOption = new ButtonGroup();
        innerFlooding = new JCheckBox("内部Flooding", false);
        outerFlooding = new JCheckBox("外部Flooding" , true);
        btgOption.add(innerFlooding);
        btgOption.add(outerFlooding);
        searchTypePanel.add(innerFlooding);
        searchTypePanel.add(outerFlooding);
        frame.add(searchTypePanel);

        //页面第二个布局
        GridLayout gridLayout1 = new GridLayout(2, 3, 10, 10);
        inputPanel.setLayout(gridLayout1);
        JLabel fineness=new JLabel("精细度",JLabel.CENTER);
        finenessNum = new JTextField();
        finenessNum.setFont(new Font("宋体", Font.BOLD, 18));
        finenessNum.setBounds(0,0,100,40);
        finenessNum.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        finenessNum.setText("128");
        //屏蔽非法输入 http://blog.csdn.net/lanjianhun/article/details/8273453
        finenessNum.addKeyListener(new KeyAdapter(){
            public void keyTyped(KeyEvent e) {
                int keyChar = e.getKeyChar();
                if(keyChar >= KeyEvent.VK_0 && keyChar <= KeyEvent.VK_9){

                }else{
                    e.consume(); //关键，屏蔽掉非法输入
                }
            }
        });
        JLabel finenessDefault=new JLabel("默认值128",JLabel.CENTER);

        JLabel minLimit=new JLabel("限定体素大小",JLabel.CENTER);
        minLimitNum = new JTextField();
        minLimitNum.setFont(new Font("宋体", Font.BOLD, 18));
        minLimitNum.setBounds(0,0,100,40);
        minLimitNum.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        minLimitNum.setText("1");
        minLimitNum.addKeyListener(new KeyAdapter(){
            public void keyTyped(KeyEvent e) {
                int keyChar = e.getKeyChar();
                if(keyChar >= KeyEvent.VK_0 && keyChar <= KeyEvent.VK_9){

                }else{
                    e.consume(); //关键，屏蔽掉非法输入
                }
            }
        });
        minLimitEnable=new JCheckBox("开启限制",false);

        inputPanel.add(fineness);
        inputPanel.add(finenessNum);
        inputPanel.add(finenessDefault);
        inputPanel.add(minLimit);
        inputPanel.add(minLimitNum);
        inputPanel.add(minLimitEnable);
        frame.add(inputPanel);

//        GridLayout gridLayout2 = new GridLayout(1, 3, 10, 10);
//        densePanel.setLayout(gridLayout2);
//        JLabel denseLabel=new JLabel("限定加密层级",JLabel.CENTER);
//        DenseDepth= new JTextField();
//        DenseDepth.setFont(new Font("宋体", Font.BOLD, 18));
//        DenseDepth.setBounds(0,0,100,20);
//        DenseDepth.setEnabled(false);
//        DenseDepth.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
//        DenseDepth.setText("2");
//        DenseDepth.addKeyListener(new KeyAdapter(){
//            public void keyTyped(KeyEvent e) {
//                int keyChar = e.getKeyChar();
//                if(keyChar >= KeyEvent.VK_0 && keyChar <= KeyEvent.VK_9){
//
//                }else{
//                    e.consume(); //关键，屏蔽掉非法输入
//                }
//            }
//        });
//        JLabel denseDefault=new JLabel("默认值2",JLabel.CENTER);
//        densePanel.add(denseLabel);
//        densePanel.add(DenseDepth);
//        densePanel.add(denseDefault);
//        frame.add(densePanel);
    }

    /**
     * 更新进度条
     * @param count
     * @param max
     */
    public static void updateProgress(int count,int max){

        String msg = null;
        float progress = (float) ( count / (max * 1.0) * 100);
        DecimalFormat decimalFormat = new DecimalFormat(".00");//构造方法的字符格式这里如果小数不足2位,会以0补足.
        msg = decimalFormat.format(progress);//format 返回的是字符串
        processBar.setValue((int) Float.parseFloat(msg));

    }

    /**
     * 更新状态信息
     * @param status
     */
    public static void updateStatus(String status){
        statusValue.setText(status);
        processBar.setValue(0);
    }

    /**
     * 显示统计信息，使用append加到文字框最低端
     * @param statistic
     */
    public static void showStatistics(String statistic){
        statusValue.append(statistic+"\r\n");
    }

    /**
     * 重置状态
     */
    public void Reset(){

        //重置文件选择器
        jfc.setSelectedFile(new File(""));

        for(AMF amf:module) {
            //重置AMF
            amf.vertices.clear();
            amf.volume.clear();
            amf.voxelList.voxels.clear();


            //重置体素集合
            amf.voxelList.voxels.clear();
            amf.voxelList.LxNum = 0;
            amf.voxelList.LyNum = 0;
            amf.voxelList.LzNum = 0;

            amf.mergeVoxels.clear();
        }

        //清空模型
        module.clear();

        //重置颜色集
        Constants.colors.clear();
        Constants.DENSE_NUM=0;
        Constants.VoxelCount=0;
        Constants.innerMerged=false;
        Constants.SCAD_PART_COUNT=0;
        Constants.RootRemain=0;

        //手动回收一下垃圾
        System.gc();
    }

}

