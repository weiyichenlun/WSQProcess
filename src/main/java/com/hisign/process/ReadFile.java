package com.hisign.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * load wsq files into info.readqueue between directory fromIndex and direactory toIndex
 * Created by ZP on 2017/5/25.
 */
public class ReadFile implements Runnable {
    private Logger log = LoggerFactory.getLogger(ReadFile.class);
    private int fromIndex = 0;
    private int toIndex = 0;
    private String TOPDIR = null;
    private AtomicInteger count = new AtomicInteger(0);
    private long start = 0L;
    private ProcessInfo info;
    public ReadFile(int fromIndex, int toIndex, ProcessInfo info) {
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
        this.info = info;
        TOPDIR = info.src_dir;
    }



    @Override
    public void run() {
        for (int i = fromIndex; i < toIndex; i++) {//0,1,2
            String subDirName = String.valueOf(i);
            File subDir = new File(TOPDIR, subDirName);//0,1,2
            if (!subDir.exists()) {
                log.info("Sub directory {} does not exist.", i);
                continue;
            }
            log.info("reading dir: {}", subDir.getAbsoluteFile());
            String[] names = subDir.list();
            if (names == null) {
                log.warn("Empty dir: {}", i);
                continue;
            }
            Arrays.sort(names);
            int len = names.length;
            log.info("Total files: {}", len);
            for (int j = 0; j < len; j++) {
                File dataDir = new File(subDir, names[j]);
                if (!dataDir.isDirectory()) continue;
                String[] wsqNames = dataDir.list((dir, name) -> name.endsWith("wsq"));
                if (wsqNames == null || wsqNames.length == 0) {
                    log.warn("No wsq in the dir: {}/{}", subDir, names[i]);
                    continue;
                }
                ProcessRecord record = new ProcessRecord();
//                log.info("in dataDir {} has {} wsq files", dataDir.getAbsolutePath(), wsqNames.length);
                for (String wsqname : wsqNames) {
                    if (!wsqname.contains("_")) {
                        log.warn("invalid wsqname: {}", wsqname);
                        continue;
                    }
                    try {
                        String posStr = wsqname.substring(wsqname.lastIndexOf("_") + 1, wsqname.lastIndexOf("."));
                        int pos = Integer.parseInt(posStr);
                        if (pos > 10 || pos < 1) {
                            log.warn("Unsoupported pos. wsq:{}", wsqname);
                            continue;
                        }
                        record.imgs[pos - 1] = Utils.readFile(new File(dataDir, wsqname));
                    } catch (NumberFormatException e) {
                        log.error("Invalid pos value. wsq:{}", wsqname, e);
                    } catch (IOException e) {
                        log.error("Read wsq file error. wsq:{}", wsqname, e);
                    }
                }
                record.file_dir = subDirName;
                record.file_name = names[j];
                record.idx = info.currentIndex.getAndIncrement();

                try {
                    log.info("put record into readQueue");
                    info.readQueue.put(record);
                    info.loadCount.incrementAndGet();
                } catch (InterruptedException e) {
                    log.error("Put record error. file: {}/{}", subDir, names[j], e);
                }
            }
        }
        info.thread_idx.incrementAndGet();
    }


}
