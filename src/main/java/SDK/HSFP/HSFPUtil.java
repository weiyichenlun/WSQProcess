package SDK.HSFP;

/**
 * Created by ZP on 2017/6/2.
 */
public class HSFPUtil {
    String JNA_LIBRARY_NAME = "HSFpMatchSDK";
    private static final String LIC = "net://172.16.0.209:8888";
    public static synchronized HSFPMatchSDK init(HSFPMatchSDK hsFpMatchSDK) {
        int r = hsFpMatchSDK.HSFp_Initialize(LIC);
        System.out.println("HSFP_Initialize: " + r);
        return hsFpMatchSDK;
    }
}
