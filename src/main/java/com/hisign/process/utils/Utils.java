package com.hisign.process.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.Properties;

/**
 *
 * Created by ZP on 2017/5/24.
 */
public class Utils {
    private static final Logger log = LoggerFactory.getLogger(Utils.class);
    private static String configFileName = "data.properties";
    private static Properties props = null;
    private static File configFile = null;
    private static long fileLastModified = 0;

    private static synchronized void init() {
        configFile = new File(configFileName);
        log.debug("configfile abs path is {}", configFile.getAbsolutePath());
        fileLastModified = configFile.lastModified();
        props = new Properties();
        load();
    }

    private static synchronized void load() {
        try{
            props.load(new FileInputStream(configFileName));
            fileLastModified = configFile.lastModified();
        } catch (FileNotFoundException e) {
            log.error("can not load configFile {}.", configFileName, e);
        } catch (IOException e) {
            log.error("IOException while in loading configfile {}.",configFileName, e);
        }
    }

    /**
     * 默认加载当前目录下的data.properties到内存进行缓存
     * @param key
     * @return
     */
    public static synchronized String getConfig(String key) {
        if (configFile == null || props == null) {
            init();
        }
        if(configFile.lastModified() > fileLastModified ) load();
        return props.getProperty(key);
    }

    public static String[] sort(String[] sub_dirs) {
        Arrays.sort(sub_dirs, (o1, o2) -> {
            try {
                int i1 = Integer.parseInt(o1);
                int i2 = Integer.parseInt(o2);
                return i1 > i2 ? 1 : (i1 < i2 ? -1 : 0);
            } catch (NumberFormatException e) {
                log.error("sub_dirs format error: not a number. {}, {}", o1, o2);
                return o1.compareTo(o2);
            }
        });
        return sub_dirs;
    }



    public static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    public static byte[] readLicense(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[1024];
        FileReader reader = new FileReader(new File(path));

        try {
            int rd;
            try {
                while((rd = reader.read(buf)) != -1) {
                    sb.append(buf, 0, rd);
                }
            } catch (Exception var9) {
                throw new IllegalArgumentException("read license_file error: " + path);
            }
        } finally {
            reader.close();
        }

        String lic = sb.toString().trim();
        return (lic + "\u0000").getBytes("iso-8859-1");
    }

    public static byte[] readFile(File file) throws IOException {
        DataInputStream dis = new DataInputStream(new FileInputStream(file));
        byte[] res = new byte[(int) file.length()];
        dis.readFully(res);
        dis.close();
        return res;
    }
}
