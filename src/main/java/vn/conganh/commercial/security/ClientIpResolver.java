package vn.conganh.commercial.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.springframework.stereotype.Component;

/**
 * Resolves the canonical client address after the servlet container has
 * applied the configured trusted-proxy policy. This class intentionally does
 * not parse Forwarded or X-Forwarded-* headers.
 */
@Component
public class ClientIpResolver {

    public ClientIp resolve(HttpServletRequest request) {
        return fromAddress(request.getRemoteAddr());
    }

    ClientIp normalize(String rawAddress) {
        return fromAddress(rawAddress);
    }

    public static ClientIp fromAddress(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) {
            return ClientIp.unknown();
        }
        try {
            InetAddress address = parseNumericAddress(rawAddress.trim());
            if (address instanceof Inet4Address) {
                String normalized = address.getHostAddress();
                return new ClientIp(normalized, normalized + "/32");
            }
            if (address instanceof Inet6Address) {
                byte[] network = Arrays.copyOf(address.getAddress(), 16);
                Arrays.fill(network, 8, 16, (byte) 0);
                String normalized = address.getHostAddress();
                String prefix = InetAddress.getByAddress(network).getHostAddress() + "/64";
                return new ClientIp(normalized, prefix);
            }
            return ClientIp.unknown();
        } catch (UnknownHostException | IllegalArgumentException exception) {
            return ClientIp.unknown();
        }
    }

    private static InetAddress parseNumericAddress(String value) throws UnknownHostException {
        if (value.indexOf(':') >= 0) {
            return InetAddress.getByName(value);
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Not a numeric IP address");
        }
        byte[] bytes = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            if (parts[index].isBlank() || parts[index].length() > 3) {
                throw new IllegalArgumentException("Invalid IPv4 address");
            }
            int octet = Integer.parseInt(parts[index]);
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Invalid IPv4 address");
            }
            bytes[index] = (byte) octet;
        }
        return InetAddress.getByAddress(bytes);
    }

    public record ClientIp(String address, String rateLimitPrefix) {
        public static ClientIp unknown() {
            return new ClientIp("unknown", "unknown");
        }
    }
}
