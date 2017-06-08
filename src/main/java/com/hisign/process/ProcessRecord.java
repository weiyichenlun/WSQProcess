package com.hisign.process;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 *
 * Created by ZP on 2017/5/26.
 */
public class ProcessRecord implements Serializable{
    private static final long serialVersionUID = 7122855224578149L;
    //编号，用于断点续传
    public int idx;

    public int thread_idx;

    //源文件所在目录
    public String file_dir;

    //源文件名称
    public String file_name;

    //源文件wsq数据
    public byte[][] imgs = new byte[10][];

    //提取得到的特征数据
    public final byte[][] feas = new byte[10][];

    //提取结果
    public boolean extractOK;

    public long extractCost;

    //写入结果
    public boolean writeOK;

    public String msg;
    public Throwable ex;

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(this);
        oos.flush();
        return baos.toByteArray();
    }

    public static ProcessRecord bytes2Record(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bais);
        return (ProcessRecord) ois.readObject();
    }


}
