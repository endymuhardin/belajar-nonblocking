# Belajar Reactive Programming #

Seringkali untuk memahami suatu konsep, tidak cukup kita membaca ratusan lembar buku dan menonton berjam-jam tutorial Youtube. Contohnya `reactive programming` ini. Saya sudah membaca banyak tutorial, diantaranya:

* [Referensi Project Reactor](https://projectreactor.io/docs/core/release/reference/)
* [Reactive Manifesto](https://www.reactivemanifesto.org)
* [Tutorial Java NIO](https://www.cs.bgu.ac.il/~spl111/wiki.files/spl111ps11.pdf)
* [Hands On Project Reactor](https://www.codingame.com/playgrounds/929/reactive-programming-with-reactor-3/Intro)

Dan juga banyak video tutorial Youtube, diantaranya:

* [Tutorial Reactive Programming Java](https://www.youtube.com/watch?v=f3acAsSZPhU)
* [Reactor Execution Model](https://www.youtube.com/watch?v=MkdwriQAllk)
* [Konsep Event Loop NodeJS](https://www.youtube.com/watch?v=8aGhZQkoFbQ)
* [Reactive WebApp dengan Spring 5](https://www.youtube.com/watch?v=rdgJ8fOxJhc)
* [Membuat Aplikasi dengan Spring Webflux](https://www.youtube.com/watch?v=1F10gr2pbvQ)
* [Konsep dan Cara Kerja Netty](https://www.youtube.com/watch?v=DKJ0w30M0vg)
* [Konsep dan Implementasi Java NIO](https://www.youtube.com/watch?v=uKc0Gx_lPsg)
* [Membuat Reactive Server dalam satu jam](https://www.youtube.com/watch?v=3m9RN4aDh08)

Tapi belum juga mengerti :(

Akhirnya, saya mengikuti dua video youtube terakhir dalam daftar di atas, dan mengetik ulang semua source codenya. Baru akhirnya paham :D

Berikut kode programnya :

### Blocking I/O ###

Kode program ini menggunakan Blocking I/O. Yaitu tiap request ditangani satu thread. Thread tersebut menunggu (blocking) selama belum ada input/output. Selama dia menunggu, thread tidak bisa digunakan untuk pekerjaan lain.

1. [SingleThreadBlockingEchoServer](src/main/java/com/muhardin/endy/belajar/nonblocking/SingleThreadBlockingEchoServer.java). Ini adalah implementasi paling sederhana. Cuma bisa menangani satu koneksi. Bila sudah ada yang connect, maka koneksi berikutnya tidak bisa dilayani.

2. [MultiThreadBlockingEchoServer](src/main/java/com/muhardin/endy/belajar/nonblocking/MultiThreadBlockingEchoServer.java). Logika programnya sama dengan nomor 1, tapi tiap ada koneksi masuk, dilempar ke thread baru untuk dilayani. Sementara thread awal langsung standby menunggu koneksi berikut.

### NonBlocking I/O ###

NonBlocking artinya thread tidak menunggu data datang atau data selesai terkirim. Thread looping terus menerus, kalau ada data diproses, kalau tidak ada teruskan looping. Ada variabel delay untuk memperlambat looping. Selama dia looping, maka CPU akan bekerja terus.

Agar tidak looping terus, kita menggunakan fitur `Selector` yang disediakan Java NIO. Kode program kembali blocking, tapi satu thread bisa menangani banyak koneksi.

3. [SingleThreadNonBlockingEchoServer](src/main/java/com/muhardin/endy/belajar/nonblocking/SingleThreadNonBlockingEchoServer.java). Satu thread, tapi bisa menangani banyak koneksi.

4. [SingleThreadNonBlockingSelectorEchoServer](src/main/java/com/muhardin/endy/belajar/nonblocking/SingleThreadNonBlockingSelectorEchoServer.java). Satu thread, menggunakan `selector` agar tidak perlu looping, tapi cukup menunggu event.

5. [ReactiveEchoServer](src/main/java/com/muhardin/endy/belajar/nonblocking/ReactiveEchoServer.java). Kode program yang sama seperti sebelumnya, menggunakan Java NIO Selector, tapi dibungkus dengan Reactive Programming dengan [Project Reactor](https://projectreactor.io/).

6. [ReactorNettyEchoServer](src/main/java/com/muhardin/endy/belajar/nonblocking/ReactorNettyEchoServer.java). Implementasi menggunakan [Reactor Netty](https://github.com/reactor/reactor-netty) supaya kita tidak perlu repot mengurus selector.

## Cara Menjalankan ##

* Jalankan dulu salah satu kode program di atas. Kalau menggunakan IDE, langsung saja klik kanan, run.

* Buka terminal, connect ke aplikasi dengan telnet. Bila di komputer anda tidak ada telnet, ganti sistem operasi dengan yang ada telnetnya (misalnya Ubuntu atau macOs).

        telnet localhost 10001
        Connected to localhost.
        Escape character is '^]'.
    
    Lalu ketikkan string sembarang. Aplikasi akan menjawab dengan string yang dijadikan huruf besar. Outputnya seperti ini:
    
        test
        S>TEST
        halo
        S>HALO
        asdfaf
        S>ASDFAF
        saf232sadfadfasfafa
        S>SAF232SADFADFASFAFA