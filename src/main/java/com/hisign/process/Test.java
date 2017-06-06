package com.hisign.process;

import javax.imageio.stream.FileImageInputStream;
import java.io.*;

/**
 * Created by Administrator on 2017/5/27.
 */
public class Test {
    public static void main(String[] args) {
        String path = "E:\\dest_person_data\\52";
        String path1 = "E:\\dest_person_data\\53";
        int count = 0;
        File src_dir = new File(path1);
        File[] names = src_dir.listFiles();
        long start = System.currentTimeMillis();
        for (File name : names) {
            String[] wsqNames = name.list();

            for (String wsqname : wsqNames) {
                if (!wsqname.contains("_")) {
                    System.out.println(("invalid wsqname: " + wsqname));
                    continue;
                }
                try {
                    String posStr = wsqname.substring(wsqname.lastIndexOf("_") + 1, wsqname.lastIndexOf("."));
                    int pos = Integer.parseInt(posStr);
                    if (pos > 10 || pos < 1) {
                        System.out.println(("Unsoupported pos. wsq: " + wsqname));
                        continue;
                    }

                    byte[] t1 = readFile1(new File(name, wsqname));
                    count++;
                } catch (NumberFormatException e) {
                    System.out.println(e.toString());
                } catch (IOException e) {
                    System.out.println(e.toString());
                }
            }
        }
        long end = System.currentTimeMillis();
        System.out.println("read " + count + " files and cost " + (end - start) + " ms");
    }

    public static byte[] readFile(File file) throws IOException {
        DataInputStream dis = new DataInputStream(new FileInputStream(file));
        byte[] res = new byte[(int) file.length()];
        dis.readFully(res);
        dis.close();
        return res;
    }

    public static byte[] readFile1(File file) throws FileNotFoundException {
        byte[] data = null;
        FileImageInputStream input = null;
        try {
            input = new FileImageInputStream(file);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buff = new byte[1024];
            int numBytesRead = 0;
            while ((numBytesRead = input.read(buff)) != -1) {
                output.write(buff, 0, numBytesRead);
            }
            output.flush();
            data = output.toByteArray();
            output.close();
            input.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return data;
    }
}
