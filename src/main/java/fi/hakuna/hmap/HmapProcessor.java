package fi.hakuna.hmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.oskari.wcs.capabilities.Capabilities;
import org.oskari.wcs.coverage.RectifiedGridCoverage;
import org.oskari.wcs.request.GetCoverage;

public class HmapProcessor implements Runnable {

    private static final int MASK_SOUTH_WEST = 1;
    private static final int MASK_SOUTH_EAST = 2;
    private static final int MASK_NORTH_WEST = 4;
    private static final int MASK_NORTH_EAST = 8;

    private static final int MAX_SIZE_WCS_REQUEST_METRES = 10000;

    private String endPoint;
    private Capabilities caps;
    private RectifiedGridCoverage desc;
    private double[] allowedExtent;
    private int maxZoom;
    private File dir;
    private int z;
    private int c;
    private int r;

    private byte[] hmap = new byte[8452];

    private static final byte[] EMPTY = new byte[8450];
    static {
        int off = 0;
        int v = 5000;
        for (int i = 0; i < 65 * 65; i++) {
            EMPTY[off++] = (byte) (v >>> 0);
            EMPTY[off++] = (byte) (v >>> 8);
        }
    }

    public HmapProcessor(String endPoint, Capabilities caps,
            RectifiedGridCoverage desc, double[] allowedExtent,
            int maxZoom, File dir, int z, int c, int r) {
        this.endPoint = endPoint;
        this.caps = caps;
        this.desc = desc;
        this.allowedExtent = allowedExtent;
        this.maxZoom = maxZoom;
        this.dir = dir;
        this.z = z;
        this.c = c;
        this.r = r;
    }

    public void run() {
        if (z > maxZoom) {
            return;
        }

        double[] bounds = getBounds(z, c, r);
        int mask = checkBounds(caps, desc, hmap, bounds, allowedExtent);
        if (mask != -1) {
            try {
                save(z, c, r, hmap);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            z = z + 1;
            c = c * 2;
            r = r * 2;
            if ((mask & MASK_SOUTH_WEST) != 0) {
                new HmapProcessor(endPoint, caps, desc, allowedExtent, maxZoom, dir, z, c, r).run();
            }
            if ((mask & MASK_SOUTH_EAST) != 0) {
                new HmapProcessor(endPoint, caps, desc, allowedExtent, maxZoom, dir, z, c + 1, r).run();
            }
            if ((mask & MASK_NORTH_WEST) != 0) {
                new HmapProcessor(endPoint, caps, desc, allowedExtent, maxZoom, dir, z, c, r + 1).run();
            }
            if ((mask & MASK_NORTH_EAST) != 0) {
                new HmapProcessor(endPoint, caps, desc, allowedExtent, maxZoom, dir, z, c + 1, r + 1).run();
            }
            return;
        }

        double[] apprExtentTM35 = getApproximateExtent(bounds);
        mask = checkApproximateExtent(apprExtentTM35, desc, hmap);
        if (mask != -1) {
            try {
                save(z, c, r, hmap);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            z = z + 1;
            c = c * 2;
            r = r * 2;
            if ((mask & MASK_SOUTH_WEST) != 0) {
                new HmapProcessor(endPoint, caps, desc, allowedExtent, maxZoom, dir, z, c, r).run();
            }
            if ((mask & MASK_SOUTH_EAST) != 0) {
                new HmapProcessor(endPoint, caps, desc, allowedExtent, maxZoom, dir, z, c + 1, r).run();
            }
            if ((mask & MASK_NORTH_WEST) != 0) {
                new HmapProcessor(endPoint, caps, desc, allowedExtent, maxZoom, dir, z, c, r + 1).run();
            }
            if ((mask & MASK_NORTH_EAST) != 0) {
                new HmapProcessor(endPoint, caps, desc, allowedExtent, maxZoom, dir, z, c + 1, r + 1).run();
            }
            return;
        }

        try {
            FloatGeoTIFF image = getImageByExtent(apprExtentTM35);
            storeTerrain(z, c, r, image, null, hmap);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void storeTerrain(int zoom, int col, int row, FloatGeoTIFF image, HeightPoint[] points, byte[] hmap) {
        if (points == null) {
            points = new HeightPoint[65 * 65];
        }

        double[] bounds = getBounds(zoom, col, row);
        setHeightPoints(bounds[0], bounds[1], bounds[2], bounds[3], points);
        int mask = encodeHeightInformationU16(image, points, hmap, zoom == maxZoom);
        try {
            System.out.printf("%d/%d/%d, %s%n", zoom, col, row, Arrays.toString(bounds));
            save(zoom, col, row, hmap);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        zoom = zoom + 1;
        col = col * 2;
        row = row * 2;
        if ((mask & MASK_SOUTH_WEST) != 0) {
            storeTerrain(zoom, col, row, image, points, hmap);
        }
        if ((mask & MASK_SOUTH_EAST) != 0) {
            storeTerrain(zoom, col + 1, row, image, points, hmap);
        }
        if ((mask & MASK_NORTH_WEST) != 0) {
            storeTerrain(zoom, col, row + 1, image, points, hmap);
        }
        if ((mask & MASK_NORTH_EAST) != 0) {
            storeTerrain(zoom, col + 1, row + 1, image, points, hmap);
        }
    }

    private void save(int zoom, int col, int row, byte[] hmap) throws IOException {
        String path = String.format("%d/%d/%d", zoom, col, row);
        File file = new File(dir, path + ".terrain");
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        try (OutputStream out = new FileOutputStream(file);
                GZIPOutputStream gos = new GZIPOutputStream(out, 4096)) {
            gos.write(hmap);
        }
        System.out.println("Saved " + path);
    }

    private static int checkBounds(Capabilities caps, RectifiedGridCoverage desc, byte[] hmap, double[] bounds, double[] allowedExtent) {
        double lonMin = bounds[0];
        double latMin = bounds[1];
        double lonMax = bounds[2];
        double latMax = bounds[3];

        if (lonMax < allowedExtent[0] || latMax < allowedExtent[1] || lonMin > allowedExtent[2] || latMin > allowedExtent[3]) {
            System.arraycopy(EMPTY, 0, hmap, 0, EMPTY.length);
            hmap[hmap.length - 2] = (byte) 0;
            hmap[hmap.length - 1] = (byte) 0;
            return 0;
        }

        // If length in either axis is over 0.5 degree skip the request 
        if (Math.max(lonMax - lonMin, latMax - latMin) > 0.5) {
            System.arraycopy(EMPTY, 0, hmap, 0, EMPTY.length);
            int mask = getMaskPotential(lonMin, latMin, lonMax, latMax,
                    allowedExtent[0], allowedExtent[1], allowedExtent[2], allowedExtent[3]);
            hmap[hmap.length - 2] = (byte) mask;
            hmap[hmap.length - 1] = (byte) 0;
            return mask;
        }

        return -1;
    }

    private static double[] getApproximateExtent(double[] bounds) {
        double lonMin = bounds[0];
        double latMin = bounds[1];
        double lonMax = bounds[2];
        double latMax = bounds[3];

        double[] extentTM35 = new double[8];
        EUREFFIN.toETRSTM35FIN(lonMin, latMin, extentTM35, 0);
        EUREFFIN.toETRSTM35FIN(lonMin, latMax, extentTM35, 2);
        EUREFFIN.toETRSTM35FIN(lonMax, latMin, extentTM35, 4);
        EUREFFIN.toETRSTM35FIN(lonMax, latMax, extentTM35, 6);

        double eMin = Math.min(extentTM35[0], extentTM35[2]);
        double nMin = Math.min(extentTM35[1], extentTM35[5]);
        double eMax = Math.max(extentTM35[4], extentTM35[6]);
        double nMax = Math.max(extentTM35[3], extentTM35[7]);

        return new double[] { eMin - 20, nMin - 20, eMax + 20, nMax + 20 };
    }

    private int checkApproximateExtent(double[] extentTM35, RectifiedGridCoverage desc, byte[] hmap) {
        double eMin = extentTM35[0];
        double nMin = extentTM35[1];
        double eMax = extentTM35[2];
        double nMax = extentTM35[3];

        double gridEMin = desc.getBoundedBy().getLowerCorner()[0];
        double gridNMin = desc.getBoundedBy().getLowerCorner()[1];
        double gridEMax = desc.getBoundedBy().getUpperCorner()[0];
        double gridNMax = desc.getBoundedBy().getUpperCorner()[1];

        // WCS reported TM35FIN bounds
        if (eMin > gridEMax || nMin > gridNMax || eMin < gridEMin || nMax < gridNMin) {
            System.arraycopy(EMPTY, 0, hmap, 0, EMPTY.length);
            hmap[hmap.length - 2] = (byte) 0;
            hmap[hmap.length - 1] = (byte) 0;
            return 0;
        }

        double sizeMetres = Math.max(eMax - eMin, nMax - nMin);
        if (sizeMetres > MAX_SIZE_WCS_REQUEST_METRES) {
            // If the extent is too big (zoom is too low)
            // then create an empty tile
            System.arraycopy(EMPTY, 0, hmap, 0, EMPTY.length);
            int mask = getMaskPotential(eMin, nMin, eMax, nMax, gridEMin, gridNMin, gridEMax, gridNMax);
            hmap[hmap.length - 2] = (byte) mask;
            hmap[hmap.length - 1] = (byte) 0;
            return mask;
        }

        return -1;
    }

    private FloatGeoTIFF getImageByExtent(double[] extentTM35) throws IOException {
        double eLo = extentTM35[0];
        double nLo = extentTM35[1];
        double eHi = extentTM35[2];
        double nHi = extentTM35[3];
        GetCoverage getCoverage = new GetCoverage(caps, desc, "image/tiff");
        getCoverage.subset("E", eLo, eHi);
        getCoverage.subset("N", nLo, nHi);
        return getImage(endPoint, getCoverage);
    }

    private void setHeightPoints(double lonMin, double latMin, double lonMax, double latMax, HeightPoint[] points) {
        double dLon = (lonMax - lonMin) / 64;
        double dLat = (latMax - latMin) / 64;
        int i = 0;
        double[] coord = new double[2];

        double lat = latMax;
        for (int row = 0; row < 65; row++) {
            double lon = lonMin;
            for (int col = 0; col < 65; col++) {
                EUREFFIN.toETRSTM35FIN(lon, lat, coord, 0);
                HeightPoint p = points[i];
                if (p == null) {
                    p = new HeightPoint();
                    points[i] = p;
                }
                i++;
                p.mask = getMask(row, col);
                p.east = coord[0];
                p.north = coord[1];
                p.outsideCoverageBounds = p.east < desc.getBoundedBy().getLowerCorner()[0]
                        || p.east > desc.getBoundedBy().getUpperCorner()[0]
                                || p.north < desc.getBoundedBy().getLowerCorner()[1]
                                        || p.north > desc.getBoundedBy().getUpperCorner()[1];
                                        lon += dLon;
            }
            lat -= dLat;
        }
    }

    private int encodeHeightInformationU16(FloatGeoTIFF image, HeightPoint[] points, byte[] hmap, boolean maxZoom) {
        int off = 0;
        int mask = 0;

        for (HeightPoint p : points) {
            if (!p.outsideCoverageBounds) {
                float elev = image.getValue(p.east, p.north);
                if (!Float.isNaN(elev) && elev != 0 && elev != -9999f) {
                    mask |= p.mask;
                    int v = Math.round((elev + 1000f) * 5f);
                    hmap[off++] = (byte) (v >>> 0);
                    hmap[off++] = (byte) (v >>> 8);
                    continue;
                }
            }
            int v = 5000;
            hmap[off++] = (byte) (v >>> 0);
            hmap[off++] = (byte) (v >>> 8);
        }

        if (maxZoom) {
            mask = 0;
        }

        hmap[off++] = (byte) mask;
        hmap[off] = (byte) 0;
        return mask;
    }

    private static int getMaskPotential(double x1, double y1, double x2, double y2,
            double xMin, double yMin, double xMax, double yMax) {
        double cx = x1 + (x2 - x1) * 0.5;
        double cy = y1 + (y2 - y1) * 0.5;
        boolean leftTileInside = cx > xMin;
        boolean bottomTileInside = cy > yMin;
        boolean rightTileInside = cx < xMax;
        boolean topTileInside = cy < yMax;
        int mask = 0;
        if (bottomTileInside) {
            if (leftTileInside) {
                mask |= MASK_SOUTH_WEST;
            }
            if (rightTileInside) {
                mask |= MASK_SOUTH_EAST;
            }
        }
        if (topTileInside) {
            if (leftTileInside) {
                mask |= MASK_NORTH_WEST;
            }
            if (rightTileInside) {
                mask |= MASK_NORTH_EAST;
            }
        }
        return mask;
    }

    private static int getMask(int row, int col) {
        if (row > 32) {
            if (col < 32) {
                return 1;
            }
            return 2;
        }
        if (col < 32) {
            return 4;
        }
        return 8;
    }

    // TMS WGS84
    private static double[] getBounds(int zoom, int col, int row) {
        double tileSize = 180.0 / (1 << zoom);
        double lonMin = -180.0 + col * tileSize;
        double latMin = -90.0 + row * tileSize;
        return new double[] {
                lonMin,
                latMin,
                lonMin + tileSize,
                latMin + tileSize
        };
    }

    public static FloatGeoTIFF getImage(String endPoint, GetCoverage getCoverage) throws IOException {
        Map<String, String[]> getCoverageKVP = getCoverage.toKVP();
        String queryString = IO.toQueryStringMulti(getCoverageKVP);
        String r = endPoint + "?" + queryString;
        System.out.println(r);
        URL url = new URL(r);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (200 != conn.getResponseCode()) {
            try (InputStream in = conn.getErrorStream()) {
                byte[] err = IO.readBytes(conn.getErrorStream());
                throw new RuntimeException(new String(err, StandardCharsets.UTF_8));
            }
        }
        try (InputStream in = conn.getInputStream()) {
            byte[] b = IO.readBytes(in);
            return new FloatGeoTIFF(b);
        }
    }

    private static class HeightPoint {

        int mask;
        double east;
        double north;
        boolean outsideCoverageBounds;

    }

}
