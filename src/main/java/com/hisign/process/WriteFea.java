package com.hisign.process;

import com.hisign.process.utils.QueryRunnerUtil;
import com.hisign.process.utils.Utils;
import org.apache.commons.dbutils.QueryRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ZP on 2017/5/26.
 */
public class WriteFea implements Runnable {
    private Logger log = LoggerFactory.getLogger(WriteFea.class);

    private ProcessInfo info;

    private QueryRunner qr = QueryRunnerUtil.getInstance();

    private final String PATH = Utils.getConfig("src_dir");

    public WriteFea(ProcessInfo info) {
        this.info = info;
    }

    @Override
    public void run() {
        while (true) {
            ProcessRecord record = null;
            try {
                record = info.writeQueue.take();
            } catch (InterruptedException e) {
                log.info("InterrupedException during take from info.writeQueue ", e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {

                }
                continue;
            }

            boolean insertOK = false;
            try {
                long start = System.currentTimeMillis();
                log.trace("before isnert: {}/{}", record.file_dir, record.file_name);
                if (record.extractOK) {
                    insertOK = insertRecord(record);
                    start = System.currentTimeMillis() - start;
                    info.writeFeaCost.set(start);
                    if (insertOK) {
                        log.debug("insert record successful. record: {}/{}, cost: {}ms", record.file_dir, record.file_name, start);
                        info.finishCount.incrementAndGet();
                    } else {
                        log.warn("duplicated record: {}/{}", record.file_dir, record.file_name);
                    }
                }
                record.writeOK = true;
            } catch (SQLException e) {
                log.error("insert record error. record: {}/{}", record.file_dir, record.file_name, e);
                record.writeOK = false;
                info.writeFailCount.incrementAndGet();
            }

            try {
                long start = System.currentTimeMillis();
                info.writeLastInfo(record);
                start = System.currentTimeMillis() - start;
                log.debug("after write last info: {}/{}, cost: {}ms", record.file_dir, record.file_name, start);
                info.writeLastInfoCost.set(start);
            } catch (Exception e) {
                log.error("write last info error. record: {}/{}", record.file_dir, record.file_name, e);
            }
            info.writeFinishedCount.incrementAndGet();

        }
    }

    private boolean insertRecord(ProcessRecord record) throws SQLException {
        String insert_sql = "insert into hbie_tenfp (id,ver,name,path,dir,fea0,fea1,fea2,fea3,fea4,fea5,fea6,fea7,fea8,fea9) " +
                "values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        List<Object> params = new ArrayList<>();
        params.add(record.file_name);
        params.add(0);
        params.add(record.file_name);
        params.add(PATH);
        params.add(record.file_dir);
        for (int i = 0; i < 10; i++) {
            params.add(record.feas[i]);
        }

        try {
            qr.update(insert_sql, params.toArray());
            return true;
        } catch (SQLException e) {
            if(e.getErrorCode() == 1) //违反唯一约束性条件
                return false;
            throw e;
        }

    }
}
