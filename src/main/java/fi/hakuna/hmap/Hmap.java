package fi.hakuna.hmap;

import java.io.File;
import java.net.URL;
import java.util.Map;

import org.oskari.wcs.capabilities.Capabilities;
import org.oskari.wcs.coverage.CoverageDescription;
import org.oskari.wcs.coverage.RectifiedGridCoverage;
import org.oskari.wcs.parser.CapabilitiesParser;
import org.oskari.wcs.parser.CoverageDescriptionsParser;
import org.oskari.wcs.request.DescribeCoverage;
import org.oskari.wcs.request.GetCapabilities;

public class Hmap {

    public static void main(String[] args) throws Exception {
        String endPoint = args[0];
        String coverageId = args[1];
        String baseDir = args[2];
        double[] extent = parseDoubleArray(args[3]);
        int maxZoom = Integer.parseInt(args[4]);

        Capabilities caps = getCapabilities(endPoint);
        CoverageDescription tmp = describeCoverage(endPoint, coverageId);
        if (!(tmp instanceof RectifiedGridCoverage)) {
            throw new Exception("Expected coverage of type RectifiedGridCoverage");
        }
        RectifiedGridCoverage desc = (RectifiedGridCoverage) tmp;

        File dir = new File(baseDir);

        new HmapProcessor(endPoint, caps, desc, extent, maxZoom, dir, 0, 0, 0).run();
        new HmapProcessor(endPoint, caps, desc, extent, maxZoom, dir, 0, 1, 0).run();
    }

    private static double[] parseDoubleArray(String csv) {
        String[] splitted = csv.split(",");
        double[] arr = new double[splitted.length];
        for (int i = 0; i < splitted.length; i++) {
            arr[i] = Double.parseDouble(splitted[i]);
        }
        return arr;
    }

    private static Capabilities getCapabilities(String endPoint)
            throws Exception {
        Map<String, String> q = GetCapabilities.toQueryParameters();
        String queryString = IO.toQueryString(q);
        URL url = new URL(endPoint + "?" + queryString);
        return CapabilitiesParser.parse(url);
    }

    private static CoverageDescription describeCoverage(String endPoint, String coverageId)
            throws Exception {
        Map<String, String> q = DescribeCoverage.toQueryParameters(coverageId);
        String queryString = IO.toQueryString(q);
        URL url = new URL(endPoint + "?" + queryString);
        return CoverageDescriptionsParser.parse(url).get(0);
    }

}
