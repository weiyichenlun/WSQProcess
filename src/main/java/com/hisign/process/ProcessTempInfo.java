package com.hisign.process;

/**
 * Created by ZP on 2017/5/26.
 */
public class ProcessTempInfo {
    public int idx;

    public int thread_idx;

    public String file_dir;

    public String file_name;

    public boolean extractOK;

    public boolean writeOK;

    public boolean duplicated;

    public String msg;

    public Throwable ex;

    public ProcessTempInfo(ProcessRecord record) {
        this.idx = record.idx;
        this.thread_idx = record.thread_idx;
        this.file_dir = record.file_dir;
        this.file_name = record.file_name;
        this.extractOK = record.extractOK;
        this.writeOK = record.writeOK;
        this.duplicated = record.duplicated;
        if (!this.extractOK) {
            this.ex = record.ex;
            if (this.ex == null) {
                this.msg = "unknown error";
            } else {
                this.msg = this.ex.getMessage();
            }
        }
    }
}
