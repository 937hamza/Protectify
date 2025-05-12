package org.firewall.protectify.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.os.Build;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DnsServersDetector {
    private static final String METHOD_EXEC_PROP_DELIM = "]: [";
    private static final Executor executor = Executors.newSingleThreadExecutor();

    private static final String[] DNS_PROPERTIES = {
            "net.dns1", "net.dns2", "net.dns3", "net.dns4"
    };

    public interface DnsDetectionCallback {
        void onComplete(String[] servers);
        void onError(Exception exception);
    }

    public static void detectDnsServers(Context context, DnsDetectionCallback callback) {
        executor.execute(() -> {
            try {
                String[] servers = getDnsServers(context);
                callback.onComplete(servers);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public static String[] getDnsServers(Context context) {
        String[] servers = getViaConnectivityManager(context);
        if (servers != null && servers.length > 0) return servers;

        servers = getViaSystemProperties();
        if (servers != null && servers.length > 0) return servers;

        servers = getViaProcessExecution();
        return servers != null ? servers : new String[0];
    }

    private static String[] getViaConnectivityManager(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return null;

        List<String> priorityServers = new ArrayList<>();
        List<String> fallbackServers = new ArrayList<>();

        try {
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                if (capabilities == null ||
                        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    continue;
                }

                LinkProperties props = cm.getLinkProperties(network);
                if (props == null) continue;

                for (InetAddress address : props.getDnsServers()) {
                    String ip = address.getHostAddress();
                    if (hasDefaultRoute(props)) {
                        priorityServers.add(ip);
                    } else {
                        fallbackServers.add(ip);
                    }
                }
            }
        } catch (SecurityException e) {
            Logger.logException(e);
            return null;
        }

        return !priorityServers.isEmpty() ?
                priorityServers.toArray(new String[0]) :
                fallbackServers.toArray(new String[0]);
    }

    private static String[] getViaSystemProperties() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return null;
        }

        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method getMethod = systemProperties.getMethod("get", String.class);
            Set<String> servers = new HashSet<>();

            for (String prop : DNS_PROPERTIES) {
                String value = (String) getMethod.invoke(null, prop);
                if (isValidIpAddress(value)) {
                    servers.add(value);
                }
            }
            return servers.isEmpty() ? null : servers.toArray(new String[0]);
        } catch (Exception e) {
            Logger.logException(e);
            return null;
        }
    }

    private static String[] getViaProcessExecution() {
        try {
            Process process = Runtime.getRuntime().exec("getprop");
            InputStream inputStream = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            Set<String> servers = parseDnsProperties(reader);
            return servers.isEmpty() ? null : servers.toArray(new String[0]);
        } catch (Exception e) {
            Logger.logException(e);
            return null;
        }
    }

    private static Set<String> parseDnsProperties(BufferedReader reader) throws Exception {
        Set<String> servers = new HashSet<>();
        String line;

        while ((line = reader.readLine()) != null) {
            int delimiterPos = line.indexOf(METHOD_EXEC_PROP_DELIM);
            if (delimiterPos == -1) continue;

            String property = line.substring(1, delimiterPos);
            String value = line.substring(
                    delimiterPos + METHOD_EXEC_PROP_DELIM.length(),
                    line.length() - 1
            );

            if (isDnsProperty(property) && isValidIpAddress(value)) {
                servers.add(value);
            }
        }
        return servers;
    }

    private static boolean isDnsProperty(String property) {
        return property.endsWith(".dns") ||
                property.endsWith(".dns1") ||
                property.endsWith(".dns2") ||
                property.endsWith(".dns3") ||
                property.endsWith(".dns4");
    }

    private static boolean isValidIpAddress(String value) {
        return value != null && (
                value.matches("^\\d+(\\.\\d+){3}$") ||
                        value.matches("^[0-9a-f]+(:[0-9a-f]*)+:[0-9a-f]+$")
        );
    }

    private static boolean hasDefaultRoute(LinkProperties props) {
        for (RouteInfo route : props.getRoutes()) {
            if (route.isDefaultRoute()) {
                return true;
            }
        }
        return false;
    }
}