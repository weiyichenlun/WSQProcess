package com.hisign.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * Created by ZP on 2017/5/26.
 */
public class ProcessInfo {
    private Logger log = LoggerFactory.getLogger(ProcessInfo.class);
    //读序列，存放待提取特征的WSQ文件记录
    public ArrayBlockingQueue<ProcessRecord> readQueue = new ArrayBlockingQueue<ProcessRecord>(1000);
    //写序列，存放提取特征后的记录
    public ArrayBlockingQueue<ProcessRecord> writeQueue = new ArrayBlockingQueue<ProcessRecord>(1000);

    //加载文件数目
    public AtomicInteger loadCount = new AtomicInteger();

    //当前索引
    public AtomicInteger currentIndex = new AtomicInteger();

    //提取特征花费时间
    public AtomicLong extractFeaCost = new AtomicLong();

    //特征提取处理数目
    public AtomicInteger extractFinishedCount = new AtomicInteger();

    //提取失败数目
    public AtomicInteger extractFailCount = new AtomicInteger();

    //写入特征花费时间
    public AtomicLong writeFeaCost = new AtomicLong();

    //插入数目
    public AtomicInteger writeFinishedCount = new AtomicInteger();

    //写入失败数目
    public AtomicInteger writeFailCount = new AtomicInteger();

    //客户端正在处理提取特征的record数目
    public AtomicInteger processingCount = new AtomicInteger();

    //完成数目
    public AtomicInteger finishCount = new AtomicInteger();

    //写最后一条记录花费时间
    public AtomicLong writeLastInfoCost = new AtomicLong();

    //保存每条数据完成后需要记录的字符串信息，若B数据比A数据顺序靠后但比A完成得早，则在A完成之前暂时将信息保存到此map中
    public ConcurrentMap<Integer, ProcessTempInfo> insertInfoMap = new ConcurrentHashMap<>();

    //线程索引，用来判断是否所有数据都已经全部加载完成
    public AtomicInteger thread_idx = new AtomicInteger();

    public boolean[] continueLasts;
    public int lastIndex;
    public String lastDir;
    public String lastName;
    public String currentDir;
    public AtomicBoolean loadAll = new AtomicBoolean();
    public int thread_num = 0;
    public String src_dir = null;
    public String[] start_dirs;
    public String[] end_dirs;
    public String[] last_names;
    private RandomAccessFile raf;
    private PrintWriter pw_extract_fail;
    private PrintWriter pw_write_fail;
    private final int INTERVAL = 10;
    private long start = System.currentTimeMillis();

    public ProcessInfo() throws IOException {
        this(1);//默认读取线程为1
    }

    public ProcessInfo(int read_thread_count) throws IOException {
        this.thread_num = read_thread_count;
        start_dirs = new String[read_thread_count];
        end_dirs = new String[read_thread_count];
        last_names = new String[read_thread_count];
        continueLasts = new boolean[read_thread_count];

        raf = new RandomAccessFile("last_info.txt", "rw");
        pw_extract_fail = new PrintWriter(new FileWriter("fail_extract_list.txt", true), true);
        pw_write_fail = new PrintWriter(new FileWriter("fail_write_list.txt", true), true);

        Properties props = new Properties();
        props.load(new FileInputStream("last_info.txt"));
        if (props.size() > 0) {
            try {
                lastIndex = Integer.parseInt(props.getProperty("last_index"));
                log.info("last index is {}", lastIndex);
            } catch (NumberFormatException e) {
                log.error("last_index number format error: {}",props.getProperty("last_index"), e);
            }
            lastDir = props.getProperty("last_dir");
            log.info("last dir is {}", lastDir);
            lastName = props.getProperty("last_name");
            log.info("last name is {}", lastName);
            if (null == lastDir) {
                lastDir = "";
            }
            if (null == lastName) {
                lastName = "";
            }
            try {
                int extract_finish_count = Integer.parseInt(props.getProperty("extract_finish_count"));
                log.info("last extract_finish_count is {}", extract_finish_count);
                extractFinishedCount.set(extract_finish_count);
            } catch (NumberFormatException e) {
                log.error("extract_finish_count number format eroor: {}", props.getProperty("extract_finish_count"), e);
            }

            try {
                int write_finish_count = Integer.parseInt(props.getProperty("write_finish_count"));
                log.info("last write_finish_count is {}", write_finish_count);
                writeFinishedCount.set(write_finish_count);
            } catch (NumberFormatException e) {
                log.error("write_finish_count number format eroor: {}", props.getProperty("write_finish_count"), e);
            }

            try {
                int extract_fail_count = Integer.parseInt(props.getProperty("extract_fail_count"));
                log.info("last extract_fail_count is {}", extract_fail_count);
                extractFailCount.set(extract_fail_count);
            } catch (NumberFormatException e) {
                log.error("extract_fail_count number format eroor: {}", props.getProperty("extract_fail_count"), e);
            }

            try {
                int write_fail_count = Integer.parseInt(props.getProperty("write_fail_count"));
                log.info("last write_fail_count is {}", write_fail_count);
                writeFailCount.set(write_fail_count);
            } catch (NumberFormatException e) {
                log.error("write_fail_count number format eroor: {}", props.getProperty("write_fail_count"), e);
            }

            try {
                int finish_count = Integer.parseInt(props.getProperty("finish_count"));
                finishCount.set(finish_count);
            } catch (NumberFormatException e) {
                log.error("finish_count number format eroor: {}", props.getProperty("finish_count"), e);
            }

            try {
                int num_thread = Integer.parseInt(props.getProperty("read_thread_count"));
                if (thread_num != num_thread) {
                    log.error("read_thread_count is not equal with read_thread_count in last_info.txt. info.read_thread_num is {} and read_thread_count in last_info is {}",
                            thread_num, num_thread);
                } else {
                    for (int i = 0; i < num_thread; i++) {
                        end_dirs[i] = props.getProperty("thread_" + i + "_last_dir");
                        last_names[i] = props.getProperty("thread_" + i + "_last_name");
                        log.info("thread_{} last dir is {}, last name is {}", i, end_dirs[i], last_names[i]);
                        continueLasts[i] = true;
                    }
                }
            } catch (NumberFormatException e) {
                log.error("thread_number format error: {}", props.getProperty("thread_num"), e);
            }


        }
    }

    public synchronized void writeLastInfo(ProcessRecord record) {
        ProcessTempInfo tempInfo = new ProcessTempInfo(record);
        writeRandomFile(tempInfo);
        int thread_idx = tempInfo.thread_idx;
        ProcessTempInfo temp = insertInfoMap.get(thread_idx);
        if (temp == null) {
            insertInfoMap.put(thread_idx, tempInfo);
        } else {
            if (temp.file_name.compareTo(tempInfo.file_name) < 0) {
                insertInfoMap.replace(thread_idx, tempInfo);
            }
        }
        StringBuilder sb = new StringBuilder();
        int last_index = getLastIdx();
        sb.append("last_index=").append(last_index).append("\r\n");
        sb.append("extract_finish_count=").append(extractFinishedCount.get()).append("\r\n");
        sb.append("extract_fail_count=").append(extractFailCount.get()).append("\r\n");
        sb.append("write_finish_count=").append(writeFinishedCount.get()).append("\r\n");
        sb.append("write_fail_count=").append(writeFailCount.get()).append("\r\n");
        sb.append("finish_count=").append(finishCount.get() + 1).append("\r\n");
        sb.append("read_thread_count=").append(thread_num).append("\r\n");
        if (thread_num == insertInfoMap.size() && (System.currentTimeMillis() - start > INTERVAL * 1000)) {
            insertInfoMap.forEach((integer, tempInfo1) -> sb.append(addInfo(tempInfo1)));
            log.info(sb.toString());
            writeRandomFile(sb.toString());
            start = System.currentTimeMillis();
            insertInfoMap.clear();
        }
    }

    private int getLastIdx() {
        final int[] idx = {0};
        insertInfoMap.forEach((integer, tempInfo) -> {
            if (tempInfo.idx > idx[0]) {
                idx[0] = tempInfo.idx;
            }
        });
        return idx[0];
    }

    private String addInfo(ProcessTempInfo tempInfo1) {
        String sb = "thread_" + tempInfo1.thread_idx + "_last_dir=" + tempInfo1.file_dir + "\r\n" +
                "thread_" + tempInfo1.thread_idx + "_last_name=" + tempInfo1.file_name + "\r\n";
        return sb;
    }

    private synchronized void writeRandomFile(ProcessTempInfo tempInfo) {
        currentDir = tempInfo.file_dir;
        if (!tempInfo.extractOK) {
            extractFailCount.incrementAndGet();
            if (tempInfo.ex != null) {
                log.error("Extract features ERROR: {}/{}", tempInfo.file_dir, tempInfo.file_name, tempInfo.ex);
            } else {
                log.error("Extract features ERROR: {}/{}， Unknown error", tempInfo.file_dir, tempInfo.file_name);
            }
            writeExtractFailInfo(tempInfo);
        }
        if (!tempInfo.writeOK) {
            writeInsertFailInfo(tempInfo);
        }
    }

    private synchronized void writeRandomFile(String s) {
        try {
            raf.setLength(0);
            raf.writeBytes(s);
        } catch (IOException e) {
            log.error("write last_info fail. {}", s, e);
        }
    }

    private synchronized void writeExtractFailInfo(ProcessTempInfo tempInfo) {
        pw_extract_fail.println(tempInfo.idx + "\t" + tempInfo.file_dir + "\t" + tempInfo.file_name + "\t" + tempInfo.msg);
        pw_extract_fail.flush();
    }

    private synchronized void writeInsertFailInfo(ProcessTempInfo tempInfo) {
        pw_write_fail.println(tempInfo.idx + "\t" + tempInfo.file_dir + "\t" + tempInfo.file_name + "\t");
        pw_write_fail.flush();
    }
}
