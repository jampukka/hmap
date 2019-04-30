package fi.hakuna.hmap;

/**
 * @see http://docs.jhs-suositukset.fi/jhs-suositukset/JHS197_liite2/JHS197_liite2.html
 * @see http://docs.jhs-suositukset.fi/jhs-suositukset/JHS197_liite3/JHS197_liite3.html
 */
public class EUREFFIN {

    private static final double a = 6378137.0;
    private static final double f = 1.0 / 298.257222101;
    private static final double e = Math.sqrt(2 * f - (f * f));

    private static final double n = f / (2.0 - f);
    private static final double n2 = n * n;
    private static final double n3 = n * n * n;
    private static final double n4 = n * n * n * n;

    private static final double A1 = (a / (1.0 + n)) * (1.0 + (n2 / 4.0) + (n4 / 64.0));

    private static final double h1_ = (n / 2.0) - (2.0 * n2 /3.0) + (5.0 * n3 / 16.0) + (41.0 * n4 / 180.0);
    private static final double h2_ = (13.0 * n2 / 48.0) - (3.0 * n3 / 5.0) + (557.0 * n4 / 1440.0);
    private static final double h3_ = (61.0 * n3 / 240.0) - (103.0 * n4 / 140.0);
    private static final double h4_ = (49561.0 * n4 / 161280.0);

    private static final double k0 = 0.9996;
    private static final double l0 = Math.toRadians(27.0);
    private static final double E0 = 500000.0;

    public static void toETRSTM35FIN(double lon, double lat, double[] out, int off) {
        lat = Math.toRadians(lat);
        lon = Math.toRadians(lon);

        double Q1 = asinh(tan(lat));
        double Q2 = atanh(e * sin(lat));
        double Q = Q1 - (e * Q2);

        double l = lon - l0;
        double B = atan(sinh(Q));
        double n_ = atanh(cos(B) * sin(l));

        double ks_ = asin(sin(B) / sech(n_));

        double ks1 = h1_ * sin(2.0 * ks_) * cosh(2.0 * n_);
        double ks2 = h2_ * sin(4.0 * ks_) * cosh(4.0 * n_);
        double ks3 = h3_ * sin(6.0 * ks_) * cosh(6.0 * n_);
        double ks4 = h4_ * sin(8.0 * ks_) * cosh(8.0 * n_);

        double n1_ = h1_ * cos(2.0 * ks_) * sinh(2.0 * n_);
        double n2_ = h2_ * cos(4.0 * ks_) * sinh(4.0 * n_);
        double n3_ = h3_ * cos(6.0 * ks_) * sinh(6.0 * n_);
        double n4_ = h4_ * cos(8.0 * ks_) * sinh(8.0 * n_);

        double ks = ks_ + ks1 + ks2 + ks3 + ks4;
        double nn = n_ + n1_ + n2_ + n3_ + n4_;

        out[off + 0] = A1 * nn * k0 + E0;
        out[off + 1] = A1 * ks * k0;
    }

    private static double tan(double x) {
        return Math.tan(x);
    }

    private static double sin(double x) {
        return Math.sin(x);
    }

    private static double asin(double x) {
        return Math.asin(x);
    }

    private static double sinh(double x) {
        return Math.sinh(x);
    }

    private static double asinh(double x) {
        return Math.log(x + Math.sqrt(x*x + 1.0));
    }

    private static double cos(double x) {
        return Math.cos(x);
    }

    private static double cosh(double x) {
        return Math.cosh(x);
    }

    private static double sech(double x) {
        return 1.0 / Math.cosh(x);
    }

    private static double atan(double x) {
        return Math.atan(x);
    }

    private static double atanh(double x) {
        return 0.5 * Math.log((1.0 + x) / (1.0 - x));
    }

}
