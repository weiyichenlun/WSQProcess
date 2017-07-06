package com.hisign.process;

import com.hisign.process.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * Created by ZP on 2017/5/31.
 */
public class Main {
    private static Logger log = LoggerFactory.getLogger(Main.class);

    private static ProcessInfo info;

    private static AtomicBoolean finished = new AtomicBoolean();

    public static void main(String[] args) throws IOException {
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
        }
        log.info("Using read_thread_count: {}", read_thread_count);

        int write_thread_count = 1;
        try {
            write_thread_count = Integer.parseInt(Utils.getConfig("write_thread_count"));
        } catch (NumberFormatException e) {
            log.error("Config error: write_thread_count");
        }
        log.info("Using write_thread_count: {}", write_thread_count);

        info = new ProcessInfo(read_thread_count);
        info.src_dir = src_dir;
        String last_dir = info.lastDir;
        String[] sub_dirs = new File(src_dir).list();
        if (null == sub_dirs) {
            log.error("Src directory is empty. ");
            return;
        }
        sub_dirs = Utils.sort(sub_dirs);
        int fromIndex = 0;
        int toIndex = 0;
        try {
            toIndex = Integer.parseInt(sub_dirs[sub_dirs.length - 1]);
        } catch (NumberFormatException e) {
            log.error("The last sub directory name is not a number: {}", sub_dirs[sub_dirs.length - 1]);
            return;
        }

        if (null == last_dir || last_dir.isEmpty()) {
            fromIndex = 0;
        } else {
            try {
                fromIndex = Integer.parseInt(last_dir) + 1;
            } catch (NumberFormatException e) {
                log.error("in info, the lastDir parameter is not a number: {}", last_dir);
            }
        }

        int interval = (toIndex - fromIndex) / read_thread_count + 1;
        log.debug("interval is {}", interval);
        if (interval < 1) {
            read_thread_count = 1;
            interval = toIndex - fromIndex;
        }
        for (int i = 0; i < read_thread_count; i++) {
            ReadFile readFile;
//            info.start_dirs[i] = info.end_dirs[i] = String.valueOf(i * interval);
            info.start_dirs[i] = String.valueOf(i * interval);
            if ((i + 1) * interval >= toIndex) {
                readFile = new ReadFile(i * interval, toIndex, info, i);
            } else {
                readFile = new ReadFile(i * interval, (i + 1) * interval, info, i);
            }
            new Thread(readFile).start();
        }

        new Thread(Main::serve).start();

        //write thread
        for (int i = 0; i < write_thread_count; i++) {
            new Thread(new WriteFea(info)).start();
        }

        //Waiting for loading all files finished
        new Thread(() -> {
            while (true) {
                if (info.loadAll.get()) {
                    log.info("read all files finish");
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.error(e.toString());
                }
            }
        }).start();

        //Waiting for all read_thread finished
        new Thread(() -> {
            while (true) {
                if (info.thread_num == info.thread_idx.get()) {
                    info.loadAll.set(true);
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.error(e.toString());
                }
            }
        }).start();

        //日志线程
        int finalRead_thread_count = read_thread_count;
        new Thread(()->{
            while (true) {
                try{
                    Thread.sleep(10 * 1000);
                } catch (InterruptedException e) {
                    log.info(e.toString());
                }
                if (finished.get()) {
                    log.info("---------FINISHED---------");
                    break;
                }
                log.info("Count(extract/write/extractFail/writeFail): {}/{}/{}/{}; Queue(read/write) size: {}/{}", info.extractFinishedCount.get(),
                        info.writeFinishedCount.get(), info.extractFailCount.get(), info.writeFailCount.get(), info.readQueue.size(), info.writeQueue.size());
                log.info("Put all: {}, put count: {}, cost(extract/write/writeLastInfo): {}/{}/{}", info.loadAll.get(), info.loadCount.get(),
                        info.extractFeaCost, info.writeFeaCost, info.writeLastInfoCost);
//                log.info("Current waiting index: {}; Current processing dir: {}; Finished waiting count: {}; Current processing count: {} ",
//                        info.currentIndex.get()- finalRead_thread_count, info.currentDir, info.insertInfoMap.size(), info.processingCount.get());
            }
        }, "Logger Thread").start();

        try{
            Thread.sleep(60 * 1000);
        } catch (InterruptedException e) {
        }
        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                log.error(e.toString());
            }
            if (info.readQueue.isEmpty() && info.writeQueue.isEmpty() /*&& info.insertInfoMap.isEmpty()*/) {
                break;
            }
        }
        log.info("All task finished");
        finished.set(true);
    }

    private static void serve() {
        try{
            ServerSocket serverSocket = new ServerSocket(8888);
            while (true) {
                try {
                    final Socket socket = serverSocket.accept();
                    log.info("accept client info: {}", socket.getRemoteSocketAddress());
                    new Thread(() -> {
                        try {
                            handle(socket);
                        } catch (IOException | InterruptedException e) {
                            log.error(e.toString());
                        } /*finally {
                            try {
                                socket.close();
                            } catch (IOException e) {
                                log.error("Close socket error: ", e);
                            }
                        }*/
                    }, "Thread_socket_"+socket.getPort()).start();
                } catch (Exception e) {
                    log.error("Accept client error: ", e);
                }
            }
        } catch (IOException e) {
            log.error(e.toString());
        }

    }

    private static void handle(Socket socket) throws IOException, InterruptedException {
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        while (true) {
            ProcessRecord record = info.readQueue.take();
            info.processingCount.incrementAndGet();
            byte[][] imgs_bak = record.imgs;
            try{
                byte[] data = record.toBytes();
                dos.writeInt(data.length);
                dos.write(data);
                dos.flush();
                byte[] rec_data = new byte[dis.readInt()];
                dis.readFully(rec_data);

                ProcessRecord pr = ProcessRecord.bytes2Record(rec_data);
                info.extractFinishedCount.incrementAndGet();
                info.extractFeaCost.set(pr.extractCost);
                while (true) {
                    try {
                        info.writeQueue.put(pr);
                        break;
                    } catch (InterruptedException e) {
                        log.error("Fail to put into writeQueue. record: {}/{}", record.file_dir, record.file_name, e);
                    }
                }
            } catch (Exception e) {
                if (e instanceof IOException) {
                    log.error("IO error. file: {}/{} ", record.file_dir, record.file_name, e);
                } else {
                    log.error("Error. file: {}/{} ", record.file_dir, record.file_name, e);
                }
                record.imgs = imgs_bak;
                while (true) {
                    try {
                        info.readQueue.put(record);
                        info.extractFinishedCount.decrementAndGet();
                        log.info("***** Put back into readQueue success. record: {}/{} ******", record.file_dir, record.file_name);
                        break;
                    } catch (InterruptedException e1) {
                        log.error("Fail to put back into readQueue. record: {}/{}", record.file_dir, record.file_name, e);
                    }
                }
                break;
            } finally {
                info.processingCount.decrementAndGet();
            }
        }
    }
}
