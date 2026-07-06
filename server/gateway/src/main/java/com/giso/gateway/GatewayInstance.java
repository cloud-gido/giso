package com.giso.gateway;

final class GatewayInstance {
    private static final String ID = resolve();

    private GatewayInstance() { }

    static String id() {
        return ID;
    }

    private static String resolve() {
        String host = System.getenv("HOSTNAME");
        if (host != null && !host.isBlank()) return host.trim();
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "gateway-local";
        }
    }
}
