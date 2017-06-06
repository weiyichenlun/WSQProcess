package com.hisign.process.client;

import com.hisign.process.ExtractFea;
import com.hisign.process.ProcessRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Created by ZP on 2017/6/2.
 */
public class Client {
    private static Logger log = LoggerFactory.getLogger(Client.class);
    private static final int THREAD_NUMBER = Runtime.getRuntime().availableProcessors() * 2;
    private static ExecutorService service = Executors.newFixedThreadPool(THREAD_NUMBER);

    public static void main(String[] args) {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        String pid = name.split("@")[0];

        if (args.length == 0) {
            log.error("Server host is not specified in the command line");
            System.exit(-1);
        }
        final String host = args[0];
        int threadCount = 1;
//        int threadCount = Runtime.getRuntime().availableProcessors();
        try {
            if (args.length > 1) {
                threadCount = Integer.parseInt(args[1]);
            }
        } catch (NumberFormatException e) {
        }
        log.info("Use thread count: {}", threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(()->{
                while (true) {
                    try{
                        handle(host, service);
                    } catch (IOException e) {
                        log.error("Socket connection error. Waiting....", e);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e1) {
                            log.error("InterruptedException: ", e);
                        }
                    }
                }
            }).start();
        }
    }

    private static void handle(String host, ExecutorService service) throws IOException {
        while (true) {
            Socket socket = new Socket(host, 8888);
            try {
                log.info("Connect to the host: {}", host);

                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                DataInputStream dis = new DataInputStream(socket.getInputStream());
                ProcessRecord record = null;
                try {
                    byte[] data = new byte[dis.readInt()];
                    dis.readFully(data);
                    record = ProcessRecord.bytes2Record(data);
                } catch (ClassNotFoundException e) {
                    log.error(e.toString());
//                    record.ex = e;
//                    record.msg = e.toString();
                }
                long start = System.currentTimeMillis();
                log.trace("befor extract features");
                ExtractFea extractFea = new ExtractFea(record, service);
                Future<ProcessRecord> future = service.submit(extractFea);
                FutureTask<ProcessRecord> ft = new FutureTask<ProcessRecord>(extractFea);
//                new Thread(ft).start();
                try {
                    record = future.get();
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Exception: ", e);
                }
                byte[] res = record.toBytes();
                dos.writeInt(res.length);
                dos.write(res);
                log.trace("After extract features. record name: {}, cost: {}ms", record.file_name, System.currentTimeMillis() - start);
            } catch (IOException e) {
                log.error("socket error. closing...", e);
            } finally {
                socket.close();
            }
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
