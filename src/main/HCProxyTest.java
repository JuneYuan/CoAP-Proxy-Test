package main;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class HCProxyTest {
    private final static Logger LOGGER = Logger.getLogger(HCProxyTest.class.getCanonicalName());  // 日志
    private static final String OUT_PATH = "/Users/SmartJune/Desktop/hcproxy1.txt";
    private static PrintWriter out;     // 用于输出并发量和完成请求数的结果
    private static final String url = "http://10.103.243.117:8080/proxy/coap://localhost/target";  // 代理URI
    //private static final String url = "http://localhost:8087/proxy/coap://localhost:PORT/target";

    public static void main(String[] args) throws Exception {
        long time1 = System.currentTimeMillis();

        out = new PrintWriter(OUT_PATH, "UTF-8");

        // 模拟不同的并发量
        int[] ccl = {100};
        //int[] ccl = {10, 20, 30, 50, 100, 200, 300, 500, 1000, 2000, 3000, 5000, 10000};
        for (int concurrencyLevel : ccl) {
            ExecutorService executor = Executors.newFixedThreadPool(concurrencyLevel);
            List<Future<Pair>> list = new ArrayList<>();
            int totalReq = 0;
            int failReq = 0;

            Callable<Pair> callable = new Task();

            // 每个单独的线程：放进线程池，并将返回值添加到结果集
            for (int i = 0; i < concurrencyLevel; i++) {
                Future<Pair> future = executor.submit(callable);
                list.add(future);
            }

            // 收集线程的执行结果
            for (Future<Pair> fut : list) {
                totalReq += fut.get().first;
                failReq += fut.get().second;
            }

            // 把结果写入文件
            out.println(concurrencyLevel + " " + (totalReq-failReq) + " " + failReq);

            // 关闭线程池，sleep（）1秒，准备进入下一次循环
            executor.shutdown();
            Thread.sleep(1000);
        }

        out.close();

        long time2 = System.currentTimeMillis();
        System.out.printf("用时%d秒", (time2 - time1) / 1000);

    }


    private static class Task implements Callable<Pair> {
        private final int totalTime = 1000*10;  // 10 seconds
        private int total = 0;
        private int fail = 0;

        @Override
        public Pair call() throws Exception {
            long start = System.currentTimeMillis();
            long end = start + totalTime;
            while (System.currentTimeMillis() < end) {
                sendGet();

            }

            return new Pair(total, fail);
        }

        // 向代理发送请求
        private void sendGet() throws Exception {
            URL obj = new URL(url);
            BufferedReader reader = null;
            HttpURLConnection con = null;
            int responseCode = -1;

            try {
                con = (HttpURLConnection) obj.openConnection();
                con.setRequestMethod("GET");
                //con.setReadTimeout(10 * 1000);
                con.connect();
                responseCode = con.getResponseCode();

                total++;

            } catch (Exception e) {
                fail++;
                //LOGGER.info(e.getMessage());
                e.printStackTrace();
            } finally {
                con.disconnect();
            }

        }

    }

    private static class Pair {
        int first, second;
        Pair(int first, int second) {
            this.first = first;
            this.second = second;
        }
    }

}


