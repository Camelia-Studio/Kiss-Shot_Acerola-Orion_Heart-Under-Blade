package org.camelia.studio.kiss.shot.acerola.utils;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

public final class UrlSafetyValidator {
    private UrlSafetyValidator() {
    }

    /**
     * Rejects non-HTTP(S) schemes and any hostname resolving to a loopback, link-local
     * (including the cloud metadata range), site-local, wildcard or multicast address, to
     * prevent user-supplied URLs from reaching internal network services (CWE-918).
     */
    public static boolean isSafePublicUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            return false;
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return false;
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return false;
            }

            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    return false;
                }
            }
        } catch (UnknownHostException exception) {
            return false;
        }

        return true;
    }

    private static boolean isPublicAddress(InetAddress address) {
        return !address.isLoopbackAddress()
                && !address.isSiteLocalAddress()
                && !address.isLinkLocalAddress()
                && !address.isAnyLocalAddress()
                && !address.isMulticastAddress();
    }
}
