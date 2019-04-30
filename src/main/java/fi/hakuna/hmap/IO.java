package fi.hakuna.hmap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class IO {

    public static byte[] readBytes(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n = 0;
        while ((n = in.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    public static String toQueryStringMulti(Map<String, String[]> kvps) {
        if (kvps == null || kvps.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String[]> entry : kvps.entrySet()) {
            final String key = entry.getKey();
            final String[] values = entry.getValue();
            if (key == null || key.isEmpty() || values == null || values.length == 0) {
                continue;
            }
            String keyEnc = urlEncodePayload(key);
            for (String value : values) {
                if (value == null || value.isEmpty()) {
                    continue;
                }
                String valueEnc = urlEncodePayload(value);
                if (!first) {
                    sb.append('&');
                }
                sb.append(keyEnc).append('=').append(valueEnc);
                first = false;
            }
        }
        return sb.toString();
    }

    public static String toQueryString(Map<String, String> kvps) {
        if (kvps == null || kvps.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : kvps.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();
            if (key == null || key.isEmpty() || value == null || value.isEmpty()) {
                continue;
            }
            String keyEnc = urlEncodePayload(key);
            String valueEnc = urlEncodePayload(value);
            if (!first) {
                sb.append('&');
            }
            sb.append(keyEnc).append('=').append(valueEnc);
            first = false;
        }
        return sb.toString();
    }

    private static String urlEncodePayload(String s) {
        // URLEncoder changes white space to + that only works on application/x-www-form-urlencoded-type encoding AND needs to be used in paths
        // For parameters etc we want to have it as %20 instead
        // so http://domain/my path?q=my value SHOULD be encoded as -> http://domain/my+path?q=my%20value
        return urlEncode(s).replace("+", "%20");
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException ignore) {
            // Ignore the exception, 'UTF-8' is supported
        }
        // return something, this code is unreachable
        return s;
    }

}
