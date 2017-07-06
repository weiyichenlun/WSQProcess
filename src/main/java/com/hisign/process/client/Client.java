package com.hisign.process.client;

import SDK.HSFP.HSFPMatchSDK;
import SDK.cxbio.Cxbio;
import SDK.cxbio.DecOutParam;
import com.hisign.process.ProcessRecord;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;

/**
 * 客户端 特征提取
 * Created by ZP on 2017/6/2.
 */
public class Client {
    private static Logger log = LoggerFactory.getLogger(Client.class);
    private static final int THREAD_NUMBER = Runtime.getRuntime().availableProcessors();
    private static final String LIC = "net://172.16.0.209:888";
    private static HSFPMatchSDK matchSDK;
    private static PointerByReference pbr;
    private static final int nFlag = 5;

    public static void main(String[] args) {
        if (args.length == 0) {
            log.error("Server host is not specified in the command line");
            System.exit(-1);
        }
        final String host = args[0];
        //使用单线程多进程模式进行特征提取
        log.debug("Before init...");
        initExtractFea();
        new Thread(()->{
            while (true) {
                try{
                    handle(host);
                } catch (IOException e) {
                    if (e instanceof SocketException) {
                        log.error("SocketException happened. The error message is {}", e.getMessage(), e);
                        System.exit(-1);
                    }
                    log.error("Socket connection error. Waiting....", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e1) {
                        log.error("InterruptedException: ", e);
                    }
                }
            }
        }, "Thread_Client").start();

    }

    private synchronized static void handle(String host) throws IOException {
        Socket socket = new Socket(host, 8888);
        log.info("Connect to the host: {}", host);
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        while (true) {
            try {
                ProcessRecord record = null;
                byte[] data = new byte[dis.readInt()];
                dis.readFully(data);
                record = ProcessRecord.bytes2Record(data);
                long start = System.currentTimeMillis();
                extractFea(record);
                record.extractCost = System.currentTimeMillis() - start;
                record.imgs = null;
                byte[] res = record.toBytes();
                dos.writeInt(res.length);
                dos.write(res);
                dos.flush();
            } catch (IOException e) {
                log.error("socket error. closing...", e);
                socket.close();
                throw new IOException("socket error. "+socket.getLocalPort(), e);
            } catch (ClassNotFoundException e) {
                log.error("impossiable: ", e);
            }
        }
    }

    private synchronized static void extractFea(ProcessRecord record) {
        byte[] fea;
        int cnt = 0;
        for (int i = 0; i < 10; i++) {
            byte[] wsq_file = record.imgs[i];
            if (null == wsq_file) {
                record.feas[i] = null;
                cnt++;
            } else {
                fea = new byte[3072];
                DecOutParam decOutParam = new DecOutParam();
                long temp = System.currentTimeMillis();
                int n = Cxbio.cxbio.CxbioGetImageData(ByteBuffer.wrap(wsq_file), wsq_file.length, Cxbio.CXBIO_FORMAT_WSQ, decOutParam);
                temp = System.currentTimeMillis() - temp;
                log.debug("get image {} cost {} ms", i, temp);
                if (n != 0) {
                    log.error("Cxbio get image data error. record: {}/{}, imgs[{}]", record.file_dir, record.file_name, i);
                    record.feas[i] = null;
                    cnt++;
                } else {
                    byte[] img = decOutParam.buf.getByteArray(0, decOutParam.buf_size);
                    Pointer p = pbr.getValue();
                    temp = System.currentTimeMillis();
                    int m = matchSDK.HSFp_ExtractFeature(p, (i + 1), HSFPMatchSDK.NonLiveScanRolled, decOutParam.width, decOutParam.height,
                            ByteBuffer.wrap(img), nFlag, ByteBuffer.wrap(fea));
                    temp = System.currentTimeMillis() - temp;
                    log.debug("extract finger{} cost {} ms", i, temp);
                    if (m != 0) {
                        log.error("Extract feature error. record: {}/{}, imgs[{}]", record.file_dir, record.file_name, i);
                        record.feas[i] = null;
                        cnt++;
                    } else {
                        record.feas[i] = fea;
                    }
                }
                Cxbio.cxbio.CxbioFree(decOutParam.buf);
            }
        }
        if (cnt == 10) {
            log.warn("all features are null. record: {}/{}", record.file_dir, record.file_name);
            record.extractOK = false;
            record.ex = new Throwable("features are null");
        } else {
            record.extractOK = true;
        }

    }

    private synchronized static void initExtractFea() {
        log.info("in initExtractFea method");
        matchSDK = HSFPMatchSDK.INSTANCE;
        pbr = new PointerByReference();
        int n = matchSDK.HSFp_BeginExtractFeature(pbr);
        if (n == 0) {
            log.info("init extract fea successfully");
        } else {
            log.info("init extract fea failed. HSFp_BeginExtractFeature n={}", n);
        }
    }
}
