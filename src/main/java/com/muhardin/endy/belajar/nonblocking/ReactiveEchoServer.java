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

    public static void main(String[] args) {
        ReactiveEchoServer.create(10001)
                .setProsesBisnis(c -> c  // terima Koneksi dan harus return Mono<Void>
                    .terimaData()        // return Flux<ByteBuffer>
                    .map(b -> {          // terima ByteBuffer dan harus return ByteBuffer lagi
                        byte[] data = new byte[b.limit()];
                        b.get(data);
                        String msg = new String(data);
                        System.out.println("C>"+msg);

                        String reply = "S>" + msg.toUpperCase();
                        return ByteBuffer.wrap(reply.getBytes());
                    }).as(c::kirimData)  // terima Flux<ByteBuffer> dan harus return Mono<Void>
                )
                .start()
                .block();
    }

    public static ReactiveEchoServer create( Integer port){
        return new ReactiveEchoServer(port);
    }

    private InetSocketAddress inetAddress;
    private Function<Koneksi, Mono<Void>> prosesBisnis;

    private ReactiveEchoServer(Integer port) {
        this.inetAddress = new InetSocketAddress(port);
    }

    public ReactiveEchoServer setProsesBisnis(Function<Koneksi, Mono<Void>> pb){
        this.prosesBisnis = pb;
        return this;
    }

    public Mono<Void> start() {
        return Flux
                .push(sink -> {
                    try {
                        // simpan selector read dan write untuk socket yang sedang connect (support multi socket)
                        Map<SocketChannel, Tuple2<FluxSink<SelectionKey>, FluxSink<SelectionKey>>>
                                daftarKoneksi = new HashMap<>();

                        ServerSocketChannel ssc = ServerSocketChannel.open();
                        ssc.configureBlocking(false);

                        System.out.println("Menunggu koneksi di port " + inetAddress.getPort());
                        ssc.bind(inetAddress);

                        Selector selector = Selector.open();
                        ssc.register(selector, SelectionKey.OP_ACCEPT);  // listen koneksi masuk (accepted)

                        sink.onDispose(() -> {
                            try {
                                ssc.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });

                        // loop terus selama belum disetop
                        while (!sink.isCancelled()) {
                            selector.select();  // blocking, menunggu event ACCEPT
                            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                            while (keys.hasNext()) {
                                SelectionKey key = keys.next();
                                keys.remove();
                                if (key.isAcceptable()) {
                                    System.out.println("Ada yang connect, melakukan registrasi socket");
                                    SocketChannel sc = ssc.accept();
                                    sc.configureBlocking(false);

                                    // tempat penyimpanan selector READ
                                    UnicastProcessor<SelectionKey> reader
                                            = UnicastProcessor.create(Queues.<SelectionKey>one().get());

                                    // tempat penyimpanan selector WRITE
                                    UnicastProcessor<SelectionKey> writer
                                            = UnicastProcessor.create(Queues.<SelectionKey>one().get());

                                    // simpan kombinasi socket dengan selector read & write
                                    daftarKoneksi.put(sc, Tuples.of(reader.sink(), writer.sink()));

                                    // buat object yang akan menjalankan proses bisnisnya
                                    // kalo mau canggih, harusnya Koneksi ini command pattern
                                    Koneksi k = new Koneksi(sc, key, reader, writer);

                                    // jalankan proses bisnis
                                    sink.next(prosesBisnis.apply(k).subscribe());
                                } else if (key.isReadable()) { // handle event ready to READ
                                    daftarKoneksi.get(key.channel())
                                            .getT1() // T1 berisi selector READ
                                            .next(key); // jalankan proses bisnis yang handle data masuk
                                } else if (key.isWritable()) {  // handle event ready to WRITE
                                    daftarKoneksi.get(key.channel())
                                            .getT2() // T2 berisi selector WRITE
                                            .next(key); // jalankan proses bisnis untuk kirim data
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
}

// proses bisnis untuk menghandle koneksi
// isinya sebetulnya adalah wrapper dari Java NIO menjadi Reactor
// proses bisnis yang sebenarnya disupply dengan function di main method
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

    // ini dijalankan apabila SelectionKey.OP_READ terjadi
    Flux<ByteBuffer> terimaData(){
        return readSelector
                .doOnSubscribe(s -> {
                    Selector selector = currentSelector.selector();
                    try {
                        // registrasi selector OP_READ
                        socketChannel.register(selector, SelectionKey.OP_READ);
                        selector.wakeup(); // pindahkan thread yang menunggu select ke sini
                    } catch (ClosedChannelException e) {
                        e.printStackTrace();
                    }
                })
                .doOnNext(selectionKey -> {
                    currentSelector = selectionKey; // pindahkan key ke instance variable
                })
                .doOnCancel(() -> {
                    try {
                        Koneksi.this.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .handle((key, sink) -> {  // proses baca data dan return ByteBuffer berisi data dibungkus dalam Flux
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

    // dijalankan pada saat SelectionKey.OP_WRITE terjadi
    Mono<Void> kirimData(Publisher<ByteBuffer> message) {
        return Mono.fromRunnable(() -> {
            // cukup subscribe saja, agar data diproses
            // proses bisnis sebenarnya ada di method hookOnXxx
            message.subscribe(this);
        });
    }

    // start listening hanya kalau sudah ada yang subscribe
    @Override
    protected void hookOnSubscribe(Subscription subscription) {
        writeSelector
                .doOnNext(key -> {
                    currentSelector = key;
                    key.interestOps(SelectionKey.OP_READ); // setelah selesai kirim data, standby terima lagi
                })
                .subscribe(key -> {
                    hookOnNext(currentBuffer);  // proses data dalam ByteBuffer
                });
        subscription.request(1); // minta data yang ingin dikirim
    }

    // kirim data dalam ByteBuffer
    @Override
    protected void hookOnNext(ByteBuffer byteBuffer) {
        int bytesWrite;
        try {
            bytesWrite = socketChannel.write(byteBuffer); // kirim data ke socket
        } catch (IOException e) {
            e.printStackTrace();
            throw new UncheckedIOException(e);
        }

        if (bytesWrite == -1) {
            cancel();
            return;
        }

        // selama masih ada data yang mau dikirim, jangan pindah ke mode listening dulu
        // proses menulis data nonblocking, jadi harus tunggu event OP_WRITE (socket siap kirim data)
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
        this.currentSelector.channel().close();
    }
}