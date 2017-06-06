package SDK.HSFP;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import java.nio.ByteBuffer;

/**
 *
 * Created by ZP on 2017/6/1.
 */
public interface HSFPMatchSDK extends Library {
    HSFPMatchSDK INSTANCE = HSFPUtil.init((HSFPMatchSDK) Native.loadLibrary("HSFpMatchSDK", HSFPMatchSDK.class));

    int HSFp_Initialize(String lic);

    int HSFp_BeginExtractFeature(PointerByReference br);

    int HSFp_ExtractFeature(Pointer var0, int var1, int var2, int var3, int var4, ByteBuffer var5, int var6, ByteBuffer var7);

}
