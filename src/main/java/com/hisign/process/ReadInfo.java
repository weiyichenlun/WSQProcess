package com.hisign.process;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Administrator on 2017/5/27.
 */
public class ReadInfo implements Externalizable{
    public AtomicInteger readCount = new AtomicInteger();
    public AtomicInteger readPersonCount = new AtomicInteger();
    public AtomicBoolean finish = new AtomicBoolean();
    public String src_dir = null;
    public AtomicInteger idx = new AtomicInteger();
    public AtomicInteger idx_person = new AtomicInteger();
    public AtomicLong per_cost = new AtomicLong();
    public AtomicLong person_cost = new AtomicLong();
    public long[] timecost = new long[1000];
    public long[] timecost_person = new long[1000];
    public int[] num_per_dir = new int[1000];
    public AtomicInteger thread_idx = new AtomicInteger();
    public int thread_num = 0;
    public byte[][] imgs = new byte[10][];

    public ReadInfo() {
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        System.out.println("begin externalize...");
        out.writeUTF("total read " + readCount.get() + " files");
        out.writeObject(this.timecost);
        out.flush();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {

    }
}
