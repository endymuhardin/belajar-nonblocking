package com.muhardin.endy.belajar.nonblocking;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class SingleThreadNonBlockingEchoServer {
    private static final Integer port = 10001;
    private static final Integer BUFFER_SIZE = 10;
    public static final int LOOP_DELAY = 1 * 1000;

    public static void main(String[] args) throws Exception {
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.configureBlocking(false);
        ssc.bind(new InetSocketAddress(port));

        while (true) {
            Thread.sleep(LOOP_DELAY);
            System.out.println("Menunggu ada yang connect di port "+port);

            SocketChannel sc = ssc.accept();
            while (sc == null) {
                System.out.println("Belum ada yang connect .... ");
                Thread.sleep(LOOP_DELAY);
                sc = ssc.accept();
            }

            sc.configureBlocking(false);
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            int bytesRead = 0;

            while ((bytesRead = sc.read(buffer)) != -1) {
                System.out.println("Baca data ...");

                if (bytesRead < 1) {
                    System.out.println("Belum ada data ...");
                    Thread.sleep(LOOP_DELAY);
                    continue;
                }

                buffer.flip();
                byte[] data = new byte[buffer.limit()];
                buffer.get(data);
                String strData = new String(data);
                System.out.print("Client> "+strData);
                buffer.clear();

                String strReply = "S>"+strData.toUpperCase();
                buffer.put(strReply.getBytes());
                buffer.flip();
                sc.write(buffer);
                buffer.clear();
            }

        }

    }
}
