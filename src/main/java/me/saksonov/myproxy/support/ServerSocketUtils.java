package me.saksonov.myproxy.support;

import java.io.IOException;
import java.net.ServerSocket;

public class ServerSocketUtils {

    public static int getLocalPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
