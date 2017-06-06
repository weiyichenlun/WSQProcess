package com.hisign.process;

import com.sun.jna.Library;
import com.sun.jna.Native;

/**
 * Created by ZP on 2017/6/1.
 */
public class TestJNA {
    public interface CLibrary extends Library {
        String filePath = CLibrary.class.getResource("/").getPath().replaceFirst("/", "").replaceAll("%20", " ") + "bin\\HSFpMatchSDK.dll";
//        NativeLibrary JNA_NATIVE_LIB = NativeLibrary.getInstance("HFpMatchSDK");
        CLibrary INSTANCE = Native.loadLibrary(".\\bin\\HSFpMatchSDK.dll", CLibrary.class);
        public int HSFp_GetTotalLevel_TT();
    }

    public static void main(String[] args) {
        String s = System.getProperty("jna.library.path");
        System.out.println(s);
        System.out.println(CLibrary.filePath);
        int a = CLibrary.INSTANCE.HSFp_GetTotalLevel_TT();
        System.out.println(a);
    }

}
