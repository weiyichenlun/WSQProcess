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
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.Socket;
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
        String name = ManagementFactory.getRuntimeMXBean().getName();
//        String pid = name.split("@")[0];

        if (args.length == 0) {
            log.error("Server host is not specified in the command line");
            System.exit(-1);
        }
        final String host = args[0];
        int threadCount = THREAD_NUMBER;
        try {
            if (args.length > 1) {
                threadCount = Integer.parseInt(args[1]);
            }
        } catch (NumberFormatException e) {
        }
        log.info("Use thread count: {}", threadCount);
        initExtractFea();
        for (int i = 0; i < 1; i++) {
            new Thread(()->{
                while (true) {
                    try{
                        handle(host);
                    } catch (IOException e) {
                        log.error("Socket connection error. Waiting....", e);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e1) {
                            log.error("InterruptedException: ", e);
                        }
                    }
                }
            }, "Thread_Client_"+i).start();
        }
    }

    private synchronized static void handle(String host) throws IOException {
        Socket socket = new Socket(host, 8888);
        log.info("Connect to the host: {}", host);
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        while (true) {
            try {
                ProcessRecord record = null;
                try {
                    byte[] data = new byte[dis.readInt()];
                    log.debug("data from server: data length is {}", data.length);
                    dis.readFully(data);
                    record = ProcessRecord.bytes2Record(data);
                } catch (ClassNotFoundException e) {
                    log.error(e.toString());
                    record.ex = e;
                    record.msg = e.toString();
                }
                long start = System.currentTimeMillis();
                log.debug("before extract features");
                extractFea(record);
                record.extractOK = true;
                record.extractCost = System.currentTimeMillis() - start;
                byte[] res = record.toBytes();
                dos.writeInt(res.length);
                dos.write(res);
                dos.flush();
                log.debug("res.length is {}", res.length);
                log.debug("After extract features. record name: {}, cost: {}ms", record.file_name, System.currentTimeMillis() - start);
            } catch (IOException e) {
                log.error("socket error. closing...", e);
            } /*finally {
                socket.close();
            }*/
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
        }
    }

    private synchronized static void extractFea(ProcessRecord record) {
        byte[] fea;
        for (int i = 0; i < 10; i++) {
            byte[] wsq_file = record.imgs[i];
            if (null == wsq_file) {
                record.feas[i] = null;
            } else {
                fea = new byte[3072];
                int len = wsq_file.length;
                DecOutParam decOutParam = new DecOutParam();
                long temp = System.currentTimeMillis();
                int n = Cxbio.cxbio.CxbioGetImageData(ByteBuffer.wrap(wsq_file), len, Cxbio.CXBIO_FORMAT_WSQ, decOutParam);
                temp = System.currentTimeMillis() - temp;
                log.debug("get image {} cost {} ms", i, temp);
                if (n != 0) {
                    log.error("Cxbio get image data error. record: {}/{}, imgs[{}]", record.file_dir, record.file_name, i);
                }
                log.trace("Cxbio get image data finish");
                int img_len = decOutParam.buf_size;
                byte[] img = decOutParam.buf.getByteArray(0, img_len);
                Pointer p = pbr.getValue();

                temp = System.currentTimeMillis();
                int m = matchSDK.HSFp_ExtractFeature(p, (i + 1), HSFPMatchSDK.NonLiveScanRolled, decOutParam.width, decOutParam.height,
                        ByteBuffer.wrap(img), nFlag, ByteBuffer.wrap(fea));
                temp = System.currentTimeMillis() - temp;
                log.debug("extract finger{} copst {} ms", i, temp);
                if (m != 0) {
                    log.error("Extract feature error. record: {}/{}, imgs[{}]", record.file_dir, record.file_name, i);
                    record.feas[i] = null;
                } else {
                    log.debug("Extract feature successfully. ");
                    record.feas[i] = fea;
                }
                Cxbio.cxbio.CxbioFree(decOutParam.buf);
            }
        }

    }

    private synchronized static void initExtractFea() {
        matchSDK = HSFPMatchSDK.INSTANCE;
        pbr = new PointerByReference();
        int n = matchSDK.HSFp_BeginExtractFeature(pbr);
        if (n == 0) {
            log.debug("init extract fea successfully");
        } else {
            log.info("init extract fea failed. HSFp_BeginExtractFeature n={}", n);
        }
    }

    private static void clearDir(File sub_tmp_dir) {
        File[] files = sub_tmp_dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            file.delete();
        }
    }
}
