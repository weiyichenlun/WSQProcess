package SDK.cxbio;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

/**
 *
 * Created by ZP on 2017/6/1.
 */
public class DecOutParam extends Structure {
    public int width;
    public int height;
    public int buf_size;
    public int resolution;
    public Pointer buf;

    public DecOutParam() {
    }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("width", "height", "buf_size", "resolution", "buf");
    }
}
