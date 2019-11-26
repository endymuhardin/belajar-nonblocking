package com.muhardin.endy.belajar.nonblocking;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class SingleThreadNonBlockingSelectorEchoServer {
    private static final Integer port = 10001;
    private static final Integer BUFFER_SIZE = 10;

    public static void main(String[] args) throws Exception {
        Selector selector = Selector.open();

        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.configureBlocking(false);

        System.out.println("Menunggu koneksi di port "+port);
        ssc.bind(new InetSocketAddress(port));

        ssc.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {
            selector.select();
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                if (key.isAcceptable()) {
                    System.out.println("Ada yang connect, melakukan registrasi socket");
                    SocketChannel sc = ssc.accept();
                    sc.configureBlocking(false);
                    sc.register(selector, SelectionKey.OP_READ);
                } else if (key.isReadable()) {
                    System.out.println("Socket sudah ada datanya, siap dibaca");
                    SocketChannel sc = (SocketChannel) key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

                    System.out.println("Baca data ...");
                    int bytesRead = sc.read(buffer);
                    System.out.println("Membaca data sebanyak " + bytesRead + " byte");

                    buffer.flip();
                    byte[] data = new byte[buffer.limit()];
                    buffer.get(data);
                    String strData = new String(data);
                    System.out.print("Client> " + strData);
                    buffer.clear();

                    String strReply = "S>" + strData.toUpperCase();
                    buffer.put(strReply.getBytes());
                    buffer.flip();
                    key.attach(buffer);
                    key.interestOps(SelectionKey.OP_WRITE);

                } else if (key.isWritable()) {
                    ByteBuffer buffer = (ByteBuffer) key.attachment();

                    SocketChannel sc = (SocketChannel) key.channel();
                    sc.write(buffer);
                    buffer.clear();

                    key.interestOps(SelectionKey.OP_READ);
                } else {
                    System.out.println("Ready Ops : " + key.readyOps());
                }
            }
        }
    }
}
