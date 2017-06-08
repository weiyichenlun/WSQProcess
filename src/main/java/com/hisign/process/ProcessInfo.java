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

    public boolean continueLast;
    public int lastIndex;
    public String lastDir;
    public String lastName;
    public String currentDir;
    public AtomicBoolean loadAll = new AtomicBoolean();
    public int thread_num = 0;
    public String src_dir = null;

    private RandomAccessFile raf;
    private PrintWriter pw_extract_fail;
    private PrintWriter pw_write_fail;

    public ProcessInfo() throws IOException {
        raf = new RandomAccessFile("last_info.txt", "rw");
        pw_extract_fail = new PrintWriter(new FileWriter("fail_extract_list.txt", true), true);
        pw_write_fail = new PrintWriter(new FileWriter("fail_write_list.txt", true), true);

        Properties props = new Properties();
        props.load(new FileInputStream("last_info.txt"));
        if (props.size() > 0) {
            continueLast = true;
            try {
                lastIndex = Integer.parseInt(props.getProperty("last_index"));
            } catch (NumberFormatException e) {
                log.error("last_index number format error: {}",props.getProperty("last_index"), e);
            }
            lastDir = props.getProperty("last_dir");
            lastName = props.getProperty("last_name");
            if (null == lastDir) {
                lastDir = "";
            }
            if (null == lastName) {
                lastName = "";
            }
            try {
                int extract_finish_count = Integer.parseInt(props.getProperty("extract_finish_count"));
                extractFinishedCount.set(extract_finish_count);
            } catch (NumberFormatException e) {
                log.error("extract_finish_count number format eroor: {}", props.getProperty("extract_finish_count"), e);
            }

            try {
                int write_finish_count = Integer.parseInt(props.getProperty("write_finish_count"));
                writeFinishedCount.set(write_finish_count);
            } catch (NumberFormatException e) {
                log.error("write_finish_count number format eroor: {}", props.getProperty("write_finish_count"), e);
            }

            try {
                int extract_fail_count = Integer.parseInt(props.getProperty("extract_fail_count"));
                extractFailCount.set(extract_fail_count);
            } catch (NumberFormatException e) {
                log.error("extract_fail_count number format eroor: {}", props.getProperty("extract_fail_count"), e);
            }

            try {
                int write_fail_count = Integer.parseInt(props.getProperty("write_fail_count"));
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
        }
    }

    public synchronized void writeLastInfo(ProcessRecord record) {
        log.trace("____________now in writeLastInfo method");
        ProcessTempInfo tempInfo = new ProcessTempInfo(record);
        log.trace("tempInfo.idx is {}", tempInfo.idx);
        log.trace("now current idx is {}", currentIndex.get());
        if (tempInfo.idx == currentIndex.get()) {
            writeRandomFile(tempInfo);
            currentIndex.incrementAndGet();
            while (true) {
                ProcessTempInfo pti = insertInfoMap.get(currentIndex.get());
                if (pti == null) {
                    break;
                } else {
                    writeRandomFile(pti);
                    insertInfoMap.remove(pti.idx);
                    if (currentIndex.get() == pti.idx) {
                        currentIndex.incrementAndGet();
                    }
                }
            }
        } else {
            log.trace("____________put tempInfo into insertInfoMap");
            insertInfoMap.put(tempInfo.idx, tempInfo);
        }
    }

    synchronized void writeRandomFile(ProcessTempInfo tempInfo) {
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
        String s = String.format("last_index=%d\r\nlast_dir=%s\r\nlast_name=%s\r\nextract_finish_count=%d\r\nwrite_finish_count=%d\r\nextract_fail_count=%d\r\n" +
                        "write_fail_count=%d\r\nfinish_count=%d\r\n", tempInfo.idx, tempInfo.file_dir, tempInfo.file_name, extractFinishedCount.get() + 1,
                writeFinishedCount.get(), extractFailCount.get(), writeFailCount.get(), finishCount.get());
        writeRandomFile(s);
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
