package com.muhardin.endy.belajar.nonblocking;

import reactor.netty.tcp.TcpServer;

public class ReactorNettyEchoServer {
    public static void main(String[] args) {
        TcpServer
            .create()
            .doOnBound(srv -> {
                System.out.println("Menunggu koneksi di port "+srv.port());
            })
            .handle((in, out) -> in
                .receive()
                .asString()
                .map(s -> {
                    System.out.println("C>"+s);
                    return "S>"+s.toUpperCase();
                })
                .as(out::sendString)
            )
            .host("localhost")
            .port(10001)
            .bindNow().onDispose().block();
    }
}
