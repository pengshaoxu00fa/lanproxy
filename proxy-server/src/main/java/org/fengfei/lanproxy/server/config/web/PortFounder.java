package org.fengfei.lanproxy.server.config.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Random;

public class PortFounder {
    public PortFounder() {
    }
    private Socket socket;
    public int findPort(){
        int port;
        try {
            socket = new Socket();
            InetSocketAddress inetAddress = new InetSocketAddress(0);
            socket.bind(inetAddress);
            port = socket.getLocalPort();
            return port;
        } catch (IOException e) {
            e.printStackTrace();
            Random random = new Random();
            return random.nextInt(Math.abs(65500-13000)) + 13000;
        } finally {
            freePort();
        }

    }

    private void freePort() {
        if (null == socket || socket.isClosed()) {
            return;
        }
        try {
            socket.close();
        } catch (IOException e){
            e.printStackTrace();
        }

    }



}
