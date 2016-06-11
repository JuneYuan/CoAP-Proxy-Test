package main;

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.serialization.DataParser;
import org.eclipse.californium.core.network.serialization.DataSerializer;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

public class WSProxyTest {
    private final static Logger LOGGER = Logger.getLogger(WSProxyTest.class.getCanonicalName());
    private static final String OUT_PATH = "/Users/SmartJune/Desktop/wsproxy.txt";
    private static final String wsuri = "ws://10.103.240.159:8887";
    private static final String coapuri = "coap://10.103.240.159:5683/target";
    private static PrintWriter out;

    public static void main(String[] args) throws Exception {
        long time1 = System.currentTimeMillis();

        out = new PrintWriter(new BufferedWriter(new FileWriter(OUT_PATH, true)));

        // 模拟不同并发量
        int[] ccl = {100};
        //int[] ccl = {10, 20, 30, 50, 100, 200, 300, 500, 1000, 2000, 3000, 5000, 10000};
        for (int concurrencyLevel : ccl) {
            ExecutorService executor = Executors.newFixedThreadPool(concurrencyLevel);
            List<Future<Pair>> list = new ArrayList<>();
            int totalReq = 0;
            int succReq = 0;

            // 每个单独的线程：放进线程池，并将返回值添加到结果集
            Callable<Pair> callable;
            for (int i  = 0; i < concurrencyLevel; i++) {
                callable = new Task(1000000*i, 65535/concurrencyLevel*i);
                Future<Pair> future = executor.submit(callable);
                list.add(future);
            }

            // 收集线程的执行结果
            for (Future<Pair> fut : list) {
                totalReq += fut.get().first;
                succReq  += fut.get().second;
            }

            // 把结果写入文件
            out.println(concurrencyLevel + " " + succReq + " " + (totalReq-succReq));

            // 关闭线程池，sleep（）1秒，准备进入下一次循环
            executor.shutdown();
            Thread.sleep(1000);
        }

        out.close();

        long time2 = System.currentTimeMillis();
        System.out.printf("用时%d秒", (time2 - time1) / 1000);
    }


    private static class Task implements Callable<Pair> {
        private int tokenBase, midBase;
        private WebSocketClient wsc;
        private final int totalTime = 1000*10;
        private int total = 0;
        private int succ = 0;

        private long start, end;
        private Boolean finished = null;

        Task(int tokenBase, int midBase) throws URISyntaxException {
            this.tokenBase = tokenBase;
            this.midBase = midBase;

            wsc = new WebSocketClient(new URI(wsuri)) {
                @Override
                public void onMessage(String message) {
                    //LOGGER.info(">> (收到代理的文本消息)\n" + message + "\n");
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
/*                    DataParser dp = new DataParser(bytes.array());
                    Response response = dp.parseResponse();
                    LOGGER.info(">> (收到代理的二进制消息)\n" + bytes + "\n" + response.toString());*/
                    succ++;

                    if (System.currentTimeMillis() < end) {
                        wsc.send(getBinaryCoapRequest());
                        total++;
                        finished = false;
                    } else
                        finished = true;
                }

                @Override
                public void onOpen(ServerHandshake handshake) {
                    LOGGER.info("状态：与代理的 WebSocket 连接已建立 ：）\n");

                    wsc.send(getBinaryCoapRequest());
                    total++;
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                }

                @Override
                public void onError(Exception ex) {
                    ex.printStackTrace();
                }
            };

            wsc.connect();

        }

        public Pair call() {
            start = System.currentTimeMillis();
            end = start + totalTime;

            while (System.currentTimeMillis() < end)     ;

/*            while (finished == null) {
                if (System.currentTimeMillis() < end)
                    continue;
                else break;
            }

            while (finished == false)   ;*/

            wsc.close();
            return new Pair(total, succ);
        }

        // 生成一个coap二进制消息
        private byte[] getBinaryCoapRequest() {
            Request req = new Request(CoAP.Code.GET, org.eclipse.californium.core.coap.CoAP.Type.CON);
            //Request req = new Request(CoAP.Code.GET, org.eclipse.californium.core.coap.CoAP.Type.CON);
            req.setURI(coapuri);
            int token = tokenBase++;
            req.setToken(new byte[] { (byte) (token >>> 24), (byte) (token >>> 16), (byte) (token >>> 8), (byte) token });
            int mid = midBase++;
            //int mid = Math.abs(midBase % (1 << 16));
            req.setMID(mid);

            DataSerializer ds = new DataSerializer();
            return ds.serializeRequest(req);
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