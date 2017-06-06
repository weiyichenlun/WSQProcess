package com.hisign.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ZP on 2017/5/26.
 */
public class ProcessInfo {
    private Logger log = LoggerFactory.getLogger(ProcessInfo.class);

    public ArrayBlockingQueue<ProcessRecord> readQueue = new ArrayBlockingQueue<ProcessRecord>(1000);

    public ArrayBlockingQueue<ProcessRecord> writeQueue = new ArrayBlockingQueue<ProcessRecord>(1000);

    public AtomicInteger currentIndex = new AtomicInteger();

    public AtomicInteger finishCount = new AtomicInteger();

    public AtomicInteger insertCount = new AtomicInteger();

    public AtomicInteger failCount = new AtomicInteger();

    public AtomicInteger writeFailCount = new AtomicInteger();

    public AtomicInteger loadCount = new AtomicInteger();

    public AtomicLong extractFeaCost = new AtomicLong();

    public AtomicLong writeFeaCost = new AtomicLong();

    public AtomicLong writeLastInfoCost = new AtomicLong();

    public AtomicInteger processingCount = new AtomicInteger();

    public ConcurrentMap<Integer, ProcessTempInfo> insertInfoMap = new ConcurrentHashMap<>();

    public boolean continueLast;
    public int lastIndex;
    public String lastDir;
    public String lastName;

    public String currentDir;

    public AtomicBoolean loadAll = new AtomicBoolean();

    public int thread_num = 0;

    public String src_dir = null;

    public AtomicInteger thread_idx = new AtomicInteger();

}
