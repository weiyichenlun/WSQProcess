package com.hisign.process;

import com.hisign.process.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * load wsq files into info.readqueue between directory fromIndex and direactory toIndex
 * Created by ZP on 2017/5/25.
 */
public class ReadFile implements Runnable {
    private Logger log = LoggerFactory.getLogger(ReadFile.class);
    private int fromIndex = 0;
    private int toIndex = 0;
    private String TOPDIR = null;
    private ProcessInfo info;
    private int thread_idx;
    public ReadFile(int fromIndex, int toIndex, ProcessInfo info, int thread_idx) {
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
        this.info = info;
        TOPDIR = info.src_dir;
        this.thread_idx = thread_idx;
    }



    @Override
    public void run() {
        boolean continueLast = info.continueLasts[thread_idx];
        log.info("ReadFile thread_{} continueLast: {}", thread_idx, continueLast);
        boolean finishSkip = false;
        int index = 0;
        int last_dir_num = 0;
        if (continueLast) {
            log.info("ReadFile thread_{} Last index: {}, last dir: {}, last name: {}", thread_idx, info.lastIndex, info.end_dirs[thread_idx], info.last_names[thread_idx]);
            index = info.lastIndex + 1;
            if (info.currentIndex.get() < index) {
                info.currentIndex.set(index);
            }
            last_dir_num = Integer.parseInt(info.end_dirs[thread_idx]);
        }
        boolean displaySkip = true;
        for (int i = fromIndex; i < toIndex; i++) {//0,1,2
            if (continueLast && !finishSkip && i < last_dir_num) {
                if (displaySkip) {
                    log.debug("ReadFile thread_{} is skipping files", thread_idx);
                    displaySkip = false;
                }
                continue;
            }
            String subDirName = String.valueOf(i);
            File subDir = new File(TOPDIR, subDirName);//0,1,2
            if (!subDir.exists()) {
                log.info("Sub directory {} does not exist.", i);
                continue;
            }
            log.info("reading dir: {}", subDir.getAbsolutePath());
            String[] names = subDir.list();
            if (names == null) {
                log.warn("Empty dir: {}", i);
                continue;
            }
            Arrays.sort(names);
            int len = names.length;
            log.info("Total files: {}", len);
            for (int j = 0; j < len; j++) {
                if (continueLast && !finishSkip) {
                    if (names[j].compareTo(info.last_names[thread_idx]) <= 0) {
                        continue;
                    } else {
                        finishSkip = true;
                        log.info("ReadFile thread_{} skip finished. Now From dir: {}, file: {}", thread_idx, subDirName, names[j]);
                    }
                }
                File dataDir = new File(subDir, names[j]);
                if (!dataDir.isDirectory()) continue;
                String[] wsqNames = dataDir.list((dir, name) -> name.endsWith("wsq"));
                if (wsqNames == null || wsqNames.length == 0) {
                    log.warn("No wsq in the dir: {}/{}", subDir, names[i]);
                    continue;
                }
                ProcessRecord record = new ProcessRecord();
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
                record.thread_idx = thread_idx;
                while (true) {
                    try {
                        info.readQueue.put(record);
                        info.loadCount.incrementAndGet();
                        break;
                    } catch (InterruptedException e) {
                        log.error("Put record error. file: {}/{}", subDir, names[j], e);
                    }
                }
            }
        }
        info.thread_idx.incrementAndGet();
    }


}
