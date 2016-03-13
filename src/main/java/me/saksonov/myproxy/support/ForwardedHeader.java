package me.saksonov.myproxy.support;

public class ForwardedHeader {

    public static String format(String forwardedBy, String forwardedFor, String forwardedHost, String forwardedProto) {
        return String.format("by=%s;for=%s;host=%s;proto=%s", forwardedBy, forwardedFor, forwardedHost, forwardedProto);
    }
}
