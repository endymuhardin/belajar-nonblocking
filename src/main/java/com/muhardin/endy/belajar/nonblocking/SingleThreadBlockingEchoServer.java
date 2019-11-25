package com.muhardin.endy.belajar.nonblocking;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class SingleThreadBlockingEchoServer {
    private static final Integer port = 10001;
    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(port);

        System.out.println("Server menunggu di port "+port);

        // block sampai ada yang connect
        Socket socket = serverSocket.accept();

        // lempar ke method handle
        handle(socket);
    }

    private static void handle(Socket socket) throws Exception {
        // buat membaca data
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // buat menulis data
        PrintWriter writer = new PrintWriter(socket.getOutputStream());

        String data;

        // readLine : blocking read sampai ketemu newline character
        while ((data = reader.readLine()) != null && !"quit".equalsIgnoreCase(data)) {
            String hasil = "Client> "+data;
            hasil += "\r\n";
            hasil += "Server> " + data.toUpperCase();
            System.out.print(hasil);
            writer.println(hasil);
            writer.flush();
        }

        // setelah client tutup socket, readLine akan menghasilkan null, keluar dari loop
        reader.close();
        writer.close();
        socket.close();
    }
}
