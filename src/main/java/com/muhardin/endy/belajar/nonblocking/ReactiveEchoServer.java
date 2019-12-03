package com.muhardin.endy.belajar.nonblocking;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.publisher.*;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.concurrent.Queues;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

public class ReactiveEchoServer {

    private static final Integer port = 10001;

    public static void main(String[] args) {
        ReactiveEchoServer.create("localhost", port)
                .handle(c -> c
                    .terimaData()
                    .map(b -> {
                        byte[] data = new byte[b.limit()];
                        b.get(data);
                        String msg = new String(data);
                        System.out.println("C>"+msg);

                        String reply = "S>" + msg.toUpperCase();
                        return ByteBuffer.wrap(reply.getBytes());
                    }).as(c::kirimData)
                )
                .start()
                .block();
    }

    public static ReactiveEchoServer create(String host, Integer port){
        return new ReactiveEchoServer(host, port);
    }

    private InetSocketAddress inetAddress;
    private Function<Koneksi, Mono<Void>> koneksi;

    public ReactiveEchoServer handle(Function<Koneksi, Mono<Void>> k){
        this.koneksi = k;
        return this;
    }

    public Mono<Void> start() {
        return Flux
                .push(sink -> {
                    try {
                        Map<SocketChannel, Tuple2<FluxSink<SelectionKey>, FluxSink<SelectionKey>>> daftarKoneksi
                                = new HashMap<>();

                        ServerSocketChannel ssc = ServerSocketChannel.open();
                        ssc.configureBlocking(false);

                        System.out.println("Menunggu koneksi di port " + port);
                        ssc.bind(new InetSocketAddress(port));

                        Selector selector = Selector.open();
                        ssc.register(selector, SelectionKey.OP_ACCEPT);

                        sink.onDispose(() -> {
                            try {
                                ssc.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });

                        while (!sink.isCancelled()) {
                            selector.select();
                            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                            while (keys.hasNext()) {
                                SelectionKey key = keys.next();
                                keys.remove();
                                if (key.isAcceptable()) {
                                    System.out.println("Ada yang connect, melakukan registrasi socket");
                                    SocketChannel sc = ssc.accept();
                                    sc.configureBlocking(false);

                                    UnicastProcessor<SelectionKey> reader = UnicastProcessor.create(Queues.<SelectionKey>one().get());
                                    UnicastProcessor<SelectionKey> writer = UnicastProcessor.create(Queues.<SelectionKey>one().get());

                                    daftarKoneksi.put(sc, Tuples.of(reader.sink(), writer.sink()));

                                    Koneksi k = new Koneksi(sc, key, reader, writer);
                                    sink.next(koneksi.apply(k).subscribe());
                                } else if (key.isReadable()) {
                                    daftarKoneksi.get(key.channel())
                                            .getT1()
                                            .next(key);

                                } else if (key.isWritable()) {
                                    daftarKoneksi.get(key.channel())
                                            .getT2()
                                            .next(key);
                                } else {
                                    System.out.println("Ready Ops : " + key.readyOps());
                                }
                            }
                        }
                    } catch (Exception err) {
                        err.printStackTrace();
                    }
                })
                .subscribeOn(Schedulers.newSingle(ReactiveEchoServer.class.getName()))
                .then();
    }

    private ReactiveEchoServer(String host, Integer port) {
        this.inetAddress = InetSocketAddress.createUnresolved(host, port);
    }
}

class Koneksi extends BaseSubscriber<ByteBuffer> implements AutoCloseable {

    private static final Integer BUFFER_SIZE = 10;

    final SocketChannel socketChannel;
    final Flux<SelectionKey> readSelector;
    final Flux<SelectionKey> writeSelector;
    final Scheduler scheduler;
    volatile SelectionKey currentSelector;

    ByteBuffer currentBuffer;

    public Koneksi(SocketChannel socketChannel, SelectionKey key, Flux<SelectionKey> readSelector, Flux<SelectionKey> writeSelector) {
        this.socketChannel = socketChannel;
        this.readSelector = readSelector;
        this.writeSelector = writeSelector;
        this.currentSelector = key;
        this.scheduler = Schedulers.single(Schedulers.parallel());
    }

    Flux<ByteBuffer> terimaData(){
        return readSelector
                .doOnSubscribe(s -> {
                    Selector selector = currentSelector.selector();
                    try {
                        socketChannel.register(selector, SelectionKey.OP_READ);
                        selector.wakeup();
                    } catch (ClosedChannelException e) {
                        e.printStackTrace();
                    }
                })
                .doOnNext(selectionKey -> {
                    currentSelector = selectionKey;
                })
                .doOnCancel(() -> {
                    try {
                        Koneksi.this.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .handle((key, sink) -> {
                    try {
                        System.out.println("Socket sudah ada datanya, siap dibaca");
                        SocketChannel sc = (SocketChannel) key.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

                        System.out.println("Baca data ...");
                        int bytesRead = sc.read(buffer);
                        System.out.println("Membaca data sebanyak " + bytesRead + " byte");

                        if(bytesRead > 0) {
                            sink.next((ByteBuffer) buffer.flip());
                        }
                    } catch (Exception err) {
                        err.printStackTrace();
                    }
                });
    }

    Mono<Void> kirimData(Publisher<ByteBuffer> message) {
        return Mono.fromRunnable(() -> {
            message.subscribe(this);
        });
    }

    @Override
    protected void hookOnSubscribe(Subscription subscription) {
        writeSelector
                .doOnNext(key -> {
                    currentSelector = key;
                    key.interestOps(SelectionKey.OP_READ);
                })
                .subscribe(key -> {
                    hookOnNext(currentBuffer);
                });
        subscription.request(1);
    }

    @Override
    protected void hookOnNext(ByteBuffer byteBuffer) {
        int bytesWrite;
        try {
            bytesWrite = socketChannel.write(byteBuffer);
        } catch (IOException e) {
            e.printStackTrace();
            throw new UncheckedIOException(e);
        }

        if (bytesWrite == -1) {
            cancel();
            return;
        }

        if (byteBuffer.hasRemaining()) {
            currentBuffer = byteBuffer;
            SelectionKey key = currentSelector;
            key.interestOps(SelectionKey.OP_WRITE);
            key.selector().wakeup();
            return;
        }

        request(1);
    }

    @Override
    public void close() throws Exception {
        System.out.println("Close");
    }
}