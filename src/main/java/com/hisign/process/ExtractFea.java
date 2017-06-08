package com.hisign.process;

import SDK.HSFP.HSFPMatchSDK;
import SDK.cxbio.Cxbio;
import SDK.cxbio.DecOutParam;
import com.sun.jna.ptr.PointerByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 *
 * Created by ZP on 2017/5/26.
 */
public class ExtractFea implements Callable<ProcessRecord>{
    private final Logger log = LoggerFactory.getLogger(ExtractFea.class);
    private ProcessRecord record;
    private final String LIC = "net://172.16.0.209:8888";
    private ExecutorService executorService;
    private HSFPMatchSDK matchSDK = null;

    public ExtractFea(ProcessRecord record, ExecutorService service) {
        this.record = record;
        this.executorService = service;
        matchSDK = HSFPMatchSDK.INSTANCE;
    }

    private boolean extract() {
        Map<Integer, Future<byte[]>> map = new HashMap<>(10);
        List<Future<byte[]>> list = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            int finalI = i;
            Future<byte[]> future = executorService.submit(new Callable<byte[]>() {
                @Override
                public byte[] call() throws Exception {
                    byte[] fea = new byte[3072];
                    byte[] wsq_file = record.imgs[finalI];
                    if(null == wsq_file) return null;
                    int len = wsq_file.length;
                    int type = 1;
                    DecOutParam decOutParam = new DecOutParam();
                    int n = Cxbio.cxbio.CxbioGetImageData(ByteBuffer.wrap(wsq_file), len, type, decOutParam);
                    if (n != 0) {
                        throw new RuntimeException("Cxbio get image error. record.name " + record.file_name + ", wsqfile: " + finalI);
                    }
                    int img_len = decOutParam.buf_size;
                    byte[] img = decOutParam.buf.getByteArray(0, img_len);
                    PointerByReference pbr = new PointerByReference();
                    HSFPMatchSDK.INSTANCE.HSFp_BeginExtractFeature(pbr);
                    int m = HSFPMatchSDK.INSTANCE.HSFp_ExtractFeature(pbr.getValue(), finalI + 1, 3, decOutParam.width, decOutParam.height,
                            ByteBuffer.wrap(img), 5, ByteBuffer.wrap(fea));
                    if (m != 0) {
                        throw new RuntimeException("Extract feature error. record.name " + record.file_name + ", imgfile: " + finalI);
                    }
                    return fea;
                }
            });
            map.put(finalI, future);
        }
        for (int i = 0; i < map.size(); i++) {
            Future<byte[]> f = map.get(i);
            try {
                byte[] fea = f.get();
                record.feas[i] = fea;
                if(fea!=null)
                    log.trace("fea {} length {}", i, fea.length);
                else
                    log.trace("fea {} is null", i);
            } catch (InterruptedException | ExecutionException e) {
                log.error(e.toString());
                return false;
            }
        }
        return true;
    }


    public ProcessRecord getResult() {
        record.extractOK = extract();
        return record;
    }

    @Override
    public ProcessRecord call() throws Exception {
        return getResult();
    }
}
