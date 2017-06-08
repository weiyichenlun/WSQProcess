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
import java.util.Arrays;
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

        info = new ProcessInfo();
        info.thread_num = read_thread_count;
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
        log.info("Continue last: {}", info.continueLast);
        if (info.continueLast) {
            log.info("Last index: {}, last dir: {}, last name: {}", info.lastIndex, info.lastDir, info.lastName);
            new Thread(() -> continueLast(info)).start();
        }

        for (int i = 0; i < read_thread_count; i++) {
            ReadFile readFile;
            if ((i + 1) * interval >= toIndex) {
                log.debug("i={}, ReadFile fromIndex={}, toIndex={}",i, i*interval, toIndex);
                readFile = new ReadFile(i * interval, toIndex, info);
            } else {
                log.debug("i={}, ReadFile fromIndex={}, toIndex={}", i, i * interval, (i + 1) * interval);
                readFile = new ReadFile(i * interval, (i + 1) * interval, info);
            }
            new Thread(readFile).start();
        }

        //TODO Server Main start
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
                log.info("Current waiting index: {}; Current processing dir: {}; Finished waiting count: {}; Current processing count: {} ",
                        info.currentIndex.get(), info.currentDir, info.insertInfoMap.size(), info.processingCount.get());
            }
        }, "Logger Thread").start();

        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                log.error(e.toString());
            }
            if (info.readQueue.isEmpty() && info.writeQueue.isEmpty() && info.insertInfoMap.isEmpty()) {
                break;
            }
        }
        log.info("All task finished");
        finished.set(true);
    }

    private static void continueLast(ProcessInfo info) {
        boolean finishSkip = false;
        int idx = info.lastIndex + 1;
        info.currentIndex.set(idx);
        boolean displaySkip = true;
        String src_dir = info.src_dir;
        File src_dir_file = new File(src_dir);
        String[] sub_dirs = src_dir_file.list();
        if (sub_dirs == null) {
            log.error("Src directory is empty.");
            return;
        }
        sub_dirs = Utils.sort(sub_dirs);
        for (String sub_dir : sub_dirs) {
            if (info.continueLast && !finishSkip && sub_dir.compareTo(info.lastDir) < 0) {
                if (displaySkip) {
                    log.debug("Skipping dirs");
                    displaySkip = false;
                }
                continue;
            }
            File sub_dir_file = new File(src_dir, sub_dir);
            if(!sub_dir_file.isDirectory()) continue;
            log.info("Now read dir: {}", sub_dir);
            String[] file_names = sub_dir_file.list();
            if (file_names == null) {
                log.debug("Empty sub directory: {}", sub_dir);
                continue;
            }
            Arrays.sort(file_names);
            log.debug("Now read files ...");
            for (String file_name : file_names) {
                if (info.continueLast && !finishSkip) {
                    if (file_name.compareTo(info.lastName) <= 0) continue;
                    else {
                        finishSkip = true;
                        log.info("Now finish skip. Start from dir: {}, file: {}", sub_dir, file_name);
                    }
                }
                ProcessRecord record = new ProcessRecord();
                File data_dir = new File(sub_dir, file_name);
                String[] wsqNames = data_dir.list((dir, name) -> name.endsWith("wsq"));
                if (wsqNames == null || wsqNames.length == 0) {
                    log.warn("No wsq in the dir: {}/{}", sub_dir, file_name);
                    continue;
                }
                for (String wsqname : wsqNames) {
                    if (!wsqname.contains("_")) {
                        log.warn("invalid wsqname: {}", wsqname);
                        continue;
                    }
                    try {
                        String posStr = wsqname.substring(wsqname.lastIndexOf("_") + 1, wsqname.lastIndexOf("."));
                        int pos = Integer.parseInt(posStr);
                        if (pos > 10 || pos < 1) {
                            log.warn("Unsoupported pos. wsq:{}/{}/{}", sub_dir, file_name, wsqname);
                            continue;
                        }
                        record.imgs[pos - 1] = Utils.readFile(new File(data_dir, wsqname));
                    } catch (NumberFormatException e) {
                        log.error("Invalid pos value. wsq:{}/{}/{}", sub_dir, file_name, wsqname, e);
                    } catch (IOException e) {
                        log.error("Read wsq file error. wsq: {}/{}/{}", sub_dir, file_name, wsqname, e);
                    }
                }
                record.file_dir = sub_dir;
                record.file_name = file_name;
                record.idx = idx++;

                try {
                    info.readQueue.put(record);
                    info.loadCount.incrementAndGet();
                } catch (InterruptedException e) {
                    log.error("Put record error. file: {}/{}", sub_dir, file_name, e);
                }
            }
        }
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
        log.info("Begin taking readQueue...");
        while (true) {
            log.debug("Info readQueue size is {}", info.readQueue.size());
            ProcessRecord record = info.readQueue.take();
            log.debug("After taking {}", record.file_name);
            info.processingCount.incrementAndGet();
            //TODO 是否保存读取的wsq数据防止提取特征失败
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

                log.debug("-----------Try to put back into readqueue----------------");
                while (true) {
                    try {
                        info.readQueue.put(record);
                        info.extractFinishedCount.decrementAndGet();
                        break;
                    } catch (InterruptedException e1) {
                        log.error("Fail to put back into readQueue. record: {}/{}", record.file_dir, record.file_name, e);
                    }
                }
                log.debug("-----------After putting back into readqueue------file: {}/{}", record.file_dir, record.file_name);
                break;
            } finally {
                info.processingCount.decrementAndGet();
            }
        }
    }
}
