package me.saksonov.myproxy.support;

import io.vertx.rxjava.core.MultiMap;

public class MultiMapUtils {

    public static String toString(MultiMap map) {
        StringBuilder sb = new StringBuilder();

        for (String name: map.names()) {
            sb.append("\n\t").append(name).append(": ");

            for (String value : map.getAll(name)) {
                sb.append(value).append("; ");
            }
        }

        return sb.toString();
    }
}
