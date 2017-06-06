package com.hisign.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 *
 * Created by ZP on 2017/5/24.
 */
public class ServerMain {
    private static Logger log = LoggerFactory.getLogger(ServerMain.class);
    private static ReadInfo info;

    public static void main(String[] args) throws InterruptedException, IOException {
        String src_dir = Utils.getConfig("src_dir");
        if (src_dir == null || src_dir.trim().isEmpty()) {
            log.error("Config error: src_dir is empty");
            return ;
        }

        int read_thread_count = 1;
        try {
            read_thread_count = Integer.parseInt(Utils.getConfig("read_thread_count"));
        } catch (NumberFormatException e) {
            log.error("Config error: read_thread_count");
            log.info("Use default read_thread_count: {}", read_thread_count);
        }
        log.info("Using read_thread_count: {}", read_thread_count);

        int process_thread_count = 1;
        try {
            process_thread_count = Integer.parseInt(Utils.getConfig("process_thread_count"));
        } catch (NumberFormatException e) {
            log.error("Config error: process_thread_count");
            log.info("Use default process_thread_count: {}", process_thread_count);
        }
        log.info("Using process_thread_count: {}", process_thread_count);

        int write_thread_count = 1;
        try {
            write_thread_count = Integer.parseInt(Utils.getConfig("write_thread_count"));
        } catch (NumberFormatException e) {
            log.error("Config error: write_thread_count");
            log.info("Use default process_thread_count: {}", write_thread_count);
        }
        log.info("Using write_thread_count: {}", write_thread_count);

        info = new ReadInfo();
        info.src_dir = src_dir;
        info.thread_num = read_thread_count;

        int datamax = 50;
        int per = datamax / read_thread_count;
        for (int i = 0; i < read_thread_count; i++) {
            ReadFile readFile = new ReadFile((i+20) * per, (i + 21) * per, new ProcessInfo());
            Thread t = new Thread(readFile);
            t.start();
        }
        new Thread(){
            @Override
            public void run() {
                while (true) {
                    if (info.thread_num == info.thread_idx.get()) {
                        info.finish.set(true);
                        break;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        log.error(e.toString());
                    }
                }
            }
        }.start();

        new Thread(){
            @Override
            public void run() {
                while (true) {
                    if (info.finish.get()) {
                        log.info("read all files finish");
                        try {
                            writeRes2File(info);
                        } catch (IOException e) {
                            log.error(e.toString());
                        }
                        break;
                    }
                    if (info.finish.get()) {
                        log.info("in Main, total read {} files", info.readCount);
                        log.info("in Main, total read {} persons", info.readPersonCount);
                        log.info("read all files finish ");
                    }
                }
            }
        }.start();
//        new Thread(){
//            @Override
//            public void run() {
//                while (true) {
//                    long s2 = System.currentTimeMillis();
//                    try {
//                        Thread.sleep(2000);
//                    } catch (InterruptedException e) {
//                        log.info("InterruptedException: ", e);
//                    }
//                    if (finished.get()){
//                        log.info("FINISHED!!!");
//                        break;
//                    }
//                    log.info("readCount is {}", readCount);
//                    if (readCount.get() % 10000 == 0) {
//                        log.info("readCount -- read 10000 files cost {} ms", System.currentTimeMillis() - s2);
//                        s2 = System.currentTimeMillis();
//                    }
//
//                }
//            }
//        }.start();

//        ReadFile readFile1 = new ReadFile(0, 2, readCount);
//        Thread t = new Thread(readFile1);
//        t.start();



//        ReadFile readFile1 = new ReadFile(0, 4);
//        ReadFile readFile2 = new ReadFile(4, 8);
//        ReadFile readFile3 = new ReadFile(8, 12);
//        ReadFile readFile4 = new ReadFile(12, 16);
//        ReadFile readFile5 = new ReadFile(16, 20);
//        Thread t1 = new Thread(readFile1, "ReadFileThread1");
//        Thread t2 = new Thread(readFile2, "ReadFileThread2");
//        Thread t3 = new Thread(readFile3, "ReadFileThread3");
//        Thread t4 = new Thread(readFile4, "ReadFileThread4");
//        Thread t5 = new Thread(readFile5, "ReadFileThread5");
//        t1.start();
//        t2.start();
//        t3.start();
//        t4.start();
//        t5.start();

    }

    private static void writeRes2File(ReadInfo info) throws IOException {
        File res = new File("result" + info.thread_num + "_thread.txt");
        if (!res.exists()) {
            try {
                res.createNewFile();
            } catch (IOException e) {
                log.error("create file error");
            }
        }
        res.setWritable(true);

        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(res), "UTF-8"));
        bw.write("total read " + info.readCount.get() + " files \r\n");
        for (int i = 0; i < info.timecost.length; i++) {
            String temp = String.format("%5s %6d %6d %6.4f  %6d %6d %6.4f  %6d%n", (i + 1) + ":", 10000, info.timecost[i], info.timecost[i] == 0 ? 0 : 10000.0 / info.timecost[i] * 1000,
                    1000, info.timecost_person[i], info.timecost_person[i] == 0 ? 0 : (1000.0 / info.timecost_person[i]) * 1000, info.num_per_dir[i]);
            bw.write(temp);
        }
        bw.flush();
        bw.close();

//        DataOutputStream dos = new DataOutputStream(new FileOutputStream(res, true));
//        ObjectOutputStream oos = new ObjectOutputStream(dos);
//        info.writeExternal(oos);
//        oos.close();
//        dos.close();
    }
}
