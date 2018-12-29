import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.io.File;

/**
 * 文件选择器类
 */
public class FileChooseHelper {

    public static File OpenFileChoose(JFileChooser jfc,FileFilter fopen,JFrame frame){
        jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        jfc.setFileFilter(fopen);
        
        int option=jfc.showOpenDialog(frame);
        File file = jfc.getSelectedFile();
        if (file == null) return null;
        if (option != JFileChooser.APPROVE_OPTION)
            return null;

        return file;
    }

    public static File SaveFileChooser(JFileChooser jfc, FileFilter fwrite, JFrame frame, String defaultName){
        jfc.setFileFilter(fwrite);
        //设置默认保存名称
        jfc.setSelectedFile(new File(defaultName));

        //对于重名文件进行判定
        while(true){
            int option=jfc.showSaveDialog(frame);
            if (option != JFileChooser.APPROVE_OPTION)
                return null;
            else{
                File fileName = new File( jfc.getSelectedFile()+"");
                if(fileName.getName() == null || fileName.getName().equals(""))
                    return null;
                //如果重名了
                if(fileName.exists())
                {
                    option = JOptionPane.showConfirmDialog(jfc,"已有重名文件，是否覆盖?");
                    // may need to check for cancel option as well
                    if (option == JOptionPane.NO_OPTION)
                        continue;
                    if(option == JOptionPane.CANCEL_OPTION)
                        return null;
                    else break;
                }
                //没有重名就可以直接保存了
                else break;
            }
        }

        return jfc.getSelectedFile();
    }
}
