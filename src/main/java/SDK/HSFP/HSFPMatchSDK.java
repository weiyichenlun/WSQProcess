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

    public static final int LiveScanPlain = 0;
    public static final int LiveScanRolled = 1;
    public static final int NonLiveScanPlain = 2;
    public static final int NonLiveScanRolled = 3;
    public static final int LatentImpression = 4;
    public static final int LatentTracing = 5;
    public static final int LatentPhoto = 6;
    public static final int LatentLift = 7;

    int HSFp_Initialize(String lic);

    int HSFp_BeginExtractFeature(PointerByReference br);

    int HSFp_ExtractFeature(Pointer var0, int var1, int var2, int var3, int var4, ByteBuffer var5, int var6, ByteBuffer var7);

}
