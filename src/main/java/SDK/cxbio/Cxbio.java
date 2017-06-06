package SDK.cxbio;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.nio.ByteBuffer;

/**
 * Created by ZP on 2017/6/1.
 */
public interface Cxbio extends Library {
    Cxbio cxbio = (Cxbio) Native.loadLibrary("cxbio", Cxbio.class);

    int CxbioGetImageData(ByteBuffer var1, int var2, int var3, DecOutParam var4);

    public static void main(String[] args) {
//        byte[] img = read();
//        int type = 1;
//        int len = img.length;
//        DecOutParam decOutParam = new DecOutParam();
//        int n = Cxbio.cxbio.CxbioGetImageData(ByteBuffer.wrap(img), len, type, decOutParam);
//        System.out.println(n);
//        int l1 = decOutParam.buf_size;
//        System.out.println(l1);
//        System.out.println(decOutParam.width);
//        System.out.println(decOutParam.height);
//        System.out.println(decOutParam.resolution);
//        byte[] res = decOutParam.buf.getByteArray(0, l1);
//        System.out.println(res[0] + " " + res[1]);
//        print();

    }

//    static void print() {
//        System.out.println(dll_path);
//    }
//
//    static byte[] read() {
//        String path = "D:\\FingerPrintProcessSystem\\ProcessWSQ\\src\\main\\data\\R5104000009992003004641";
//        File path_file = new File(path);
//        String[] wsqs = path_file.list();
//        File wsq = new File(path, wsqs[0]);
//        try {
//            DataInputStream dis = new DataInputStream(new FileInputStream(wsq));
//            byte[] img = new byte[(int) wsq.length()];
//            dis.readFully(img);
//            return img;
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return new byte[0];
//    }
}
