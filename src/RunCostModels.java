public class RunCostModels {

    // this estimates the number of bytes used by binary packing with with w
    // to compress data
    public static int binarypacking(int[] data, int w) {
        int cost = 0;
        for (int k = 0; k + w <= data.length; k += w) {
            cost += 1; // store bit width
            cost += (Util.maxbits(data, k, w) * w + 7) / 8;
        }
        return cost;
    }

    public static int binarypackinglowerbound(int[] data) {
        int cost = 0;
        for (int k = 0; k < data.length; ++k) {
            cost += Util.bits(data[k]);
        }
        return (cost + 7) / 8;
    }

    public static int varint(int[] data) {
        int cost = 0;
        for (int v : data) {
            if (v < (1 << 7)) {
                cost += 1;
            } else if (v < (1 << 14)) {
                cost += 2;
            } else if (v < (1 << 21)) {
                cost += 3;
            } else
                cost += 4;
        }
        return cost;
    }

    public static int packedvarint(int[] data, int w) {
        double cost = 0;
        for (int k = 0; k + w <= data.length; k += w) {
            cost += 0.25; // store bit width
            cost += (Util.maxbits(data, k, w) + 7) / 8 * w;
            if (Util.maxbits(data, k, w) == 0)
                cost += w;
        }
        return (int) Math.round(cost);
    }

    public static int varintgb(int[] data) {
        double cost = 0;
        for (int v : data) {
            cost += 0.25;// 2bits
            cost += (Util.bits(v) + 7) / 8;
            if (v == 0)
                ++cost;
        }
        return (int) Math.round(cost);
    }

    public static int max(int[] i, int pos, int length) {
        int m = 0;
        for (int k = pos; k < pos + length; ++k)
            m = Math.max(i[pos], m);
        return m;
    }

    private static boolean __nate(int[] data, int pos, int small, int large) {
        int left = Math.min(4, data.length - pos);
        int smallcnt = 0;
        int largecnt = 0;
        for (int k = 0; k < left; ++k) {
            if (data[pos + k] < small)
                ++smallcnt;
            else if (data[pos + k] < large)
                ++largecnt;
            else
                return false;
        }
        return largecnt == 1;
    }

    private static boolean __natereverse(int[] data, int pos, int small,
            int large) {
        int left = Math.min(4, data.length - pos);
        int smallcnt = 0;
        int largecnt = 0;
        for (int k = 0; k < left; ++k) {
            if (data[pos + k] < small)
                ++smallcnt;
            else if (data[pos + k] < large)
                ++largecnt;
            else
                return false;
        }
        return largecnt <= 3;
    }

    public static int nate(int[] data) {
        int cost = 0;
        int[] counters = new int[16];
        for (int k = 0; k < data.length;) {
            // we do something simple... not quite simple8b
            int left = data.length - k;
            if (Util.maxbits(data, k, Math.min(left, 32)) <= 0) {
                k += 32;
                cost += 1;
                counters[0] += 32;
            } else if (Util.maxbits(data, k, Math.min(left, 4)) <= 2) {
                k += 4;
                cost += 1;
                counters[1] += 4;
            } else if (Util.maxbits(data, k, Math.min(left, 32)) <= 3) {
                k += 32;
                cost += 32 * 3 / 8 + 1;
                counters[2] += 32;
            } else if (__nate(data, k, 4, 256)) {
                // We then have an encoding for cases for 3 small 0B deltas and
                // one 1B.
                // 3 x (d <= 3) + 1 (D <= 256) == 8b + 8b / 4 docs == 4 bits per
                // doc
                k += 4;
                cost += 2;
                counters[3] += 4;
            } else if (Util.maxbits(data, k, Math.min(left, 32)) <= 4) {
                k += 32;
                cost += 1 + 4 * 32 / 8;
                counters[4] += 32;
            } else if (Util.maxbits(data, k, Math.min(left, 12)) <= 8) {
                k += 4;
                cost += 1 + 4;
                counters[5] += 4;
            } else if (__nate(data, k, 256, 512)) {
                // It turns out we can also handle one slightly higher delta for
                // the same cost:
                // 3 x (d <= 256) + 1 x (d <= 512) == 8b + (4 * 8b) / 4 docs ==
                // 10 bits per doc
                k += 4;
                cost += 1 + 4;
                counters[6] += 4;
            } else if (__nate(data, k, 256, 65546)) {
                k += 4;
                cost += 1 + 3 + 2;
                // It's probably worth also handling one medium jump among
                // smalls:
                // 3 x (d <= 256) + 1 x (d <=65546) == 8b + (3 * 8b) + (1 * 16b)
                // / 4 docs
                // == 12 bits per doc
                counters[7] += 4;
            } else if (__nate(data, k, 512, 65546)) {
                k += 4;
                cost += 1 + 3 * 2 + 2;
                //
                // Next up we handle cases where we have one small and 3 medium.
                // Through
                // some trickery, we can actually handle up to 512 for the small
                // (2 x
                // 256):
                // 1 x (d <= 512) + 3 x (d <= 65536) == 8b + (3 * 16b) + (1 *
                // 16b) / 4
                // docs == 16 bits per doc
            } else if (__nate(data, k, 65536, 65546)
                    || __nate(data, k, 65536, 2 * 65546)) {
                k += 4;
                cost += 1 + 4 * 2;
                // Then cases where we need a key and 4 2B values. We can handle
                // one
                // "burst" to double:
                // 4 x (d <= 65536) == 8b + (4 * 16b) / 4 docs == 18 bits per
                // doc
                // 3 x (d <= 65536) + 1 x (d <= 2*65536) == 18 bits per doc
            } else if (__nate(data, k, 65536, 1 << (3 * 8))) {
                k += 4;
                cost += 1 + 3 * 2 + 3;
                // Beyond that we need to burst to add one 3B large to the mix:
                // 3 x (d <= 65536) + 1 x (d <= 3B) == 8b + (3 * 16b) + (1 *
                // 24b) / 4
                // docs == 20 bits per doc
            } else if (__natereverse(data, k, 65536, 1 << (3 * 8))) {
                // And then we handle cases of one 2B double-medium and 3 x 3B
                // large.
                // 1 x (d <= 2*65536) + 3 x (d <= 3B) == 8b + (1 * 16b) + (3 *
                // 24b) / 4
                // docs == 24 bits per doc
                k += 4;
                cost += 1 + 2 + 3 * 3;
            } else if (Util.maxbits(data, k, Math.min(left, 4)) <= 24) {
                // Then we handle cases where we we need 4 3B large elements:
                // 4 x (d <=3B) == 8b + (4 * 24b) / 4 docs == 26 bits per doc
                k += 4;
                cost += 1 + 4 * 3;
            } else if (__nate(data, k, 1 << (3 * 8), 2 << (3 * 8))) {
                // We can double the range for one of the elements without extra
                // cost:
                // 3 x (d <=3B) + 1 x (d <=2*3B) == 8b + (4 * 24b) / 4 docs ==
                // 26 bits per doc
                k += 4;
                cost += 1 + 4 * 3;
            } else if (__nate(data, k, 1 << (3 * 8), Integer.MAX_VALUE)) {
                k += 4;
                cost += 1 + 3 * 3 + 4;
                // 3 x (d <= 3B) + 1 x (d <= 4B) == 8b + (3 * 24b) + (1 * 32b)
                // == 28 bits per doc
            } else if (__natereverse(data, k, 1 << (3 * 8), Integer.MAX_VALUE)) {
                k += 4;
                cost += 1 + 3 * 4 + 3;
                // Three giant and one large:
                // 3 x (d <= 4B) + 1 x (d <= 3B) == 8b + (3 * 32b) + (1 * 24b)
                // == 32 bits per doc
            } else {
                k += 4;
                cost += 1 + 4 * 4;
                throw new RuntimeException("really?");
                // Four giant:
                // 4 x (d > 3B) == 8b + (4 * 32b) = 34 bits per doc
            }
        }
        if (false) {
            java.text.DecimalFormat df = new java.text.DecimalFormat("0.00");
            System.out.println();
            for (int j = 0; j < counters.length; ++j)
                System.out.print(df.format(counters[j] * 1.0 / data.length)
                        + " ");
            System.out.println();
        }
        return cost;
    }

    public static int simple8b(int[] data) {
        int cost = 0;
        for (int k = 0; k < data.length;) {
            // we do something simple... not quite simple8b
            int left = data.length - k;
            if (Util.maxbits(data, k, Math.min(left, 90)) <= 0) {
                k += 90;
            } else if (Util.maxbits(data, k, Math.min(left, 60)) <= 1) {
                k += 60;
            } else if (Util.maxbits(data, k, Math.min(left, 30)) <= 2) {
                k += 30;
            } else if (Util.maxbits(data, k, Math.min(left, 20)) <= 3) {
                k += 20;
            } else if (Util.maxbits(data, k, Math.min(left, 15)) <= 4) {
                k += 15;
            } else if (Util.maxbits(data, k, Math.min(left, 12)) <= 5) {
                k += 12;
            } else if (Util.maxbits(data, k, Math.min(left, 10)) <= 6) {
                k += 10;
            } else if (Util.maxbits(data, k, Math.min(left, 8)) <= 7) {
                k += 8;
            } else if (Util.maxbits(data, k, Math.min(left, 7)) <= 8) {
                k += 7;
            } else if (Util.maxbits(data, k, Math.min(left, 6)) <= 10) {
                k += 6;
            } else if (Util.maxbits(data, k, Math.min(left, 5)) <= 12) {
                k += 5;
            } else if (Util.maxbits(data, k, Math.min(left, 4)) <= 15) {
                k += 4;
            } else if (Util.maxbits(data, k, Math.min(left, 3)) <= 20) {
                k += 3;
            } else if (Util.maxbits(data, k, Math.min(left, 2)) <= 30) {
                k += 2;
            } else {
                k += 1;
            }
            cost += 8;
        }
        return cost;
    }

    public static int simple16b(int[] data) {
        int cost = 0;
        for (int k = 0; k < data.length;) {
            int left = data.length - k;
            for (int b = 1; b <= 32; ++b) {
                if (Util.maxbits(data, k, Math.min(left, 128 / b)) <= b) {
                    k += 128 / b;
                    break;
                }
            }
            cost += 16 + 1;
        }
        return cost;
    }

    public static int simple4b(int[] data) {
        int cost = 0;
        for (int k = 0; k < data.length;) {
            int left = data.length - k;
            for (int b = 1; b <= 32; ++b) {
                if (28 / (b + 1) == 28 / b)
                    continue;
                if (Util.maxbits(data, k, Math.min(left, 28 / b)) <= b) {
                    k += 32 / b;
                    break;
                }
            }
            cost += 4;
        }
        return cost;
    }

    public static int idealvarint(int[] data) {
        int cost = 0;
        for (int v : data) {
            if (v == 0) {
                cost += 0;
            } else if (v < (1 << 8)) {
                cost += 1;
            } else if (v < (1 << 16)) {
                cost += 2;
            } else if (v < (1 << 24)) {
                cost += 3;
            } else
                cost += 4;
        }
        return cost;
    }

    public static int fastpfor(int[] data, int w) {
        int cost = 0;
        int[] buffer = new int[33];
        for (int k = 0; k + w <= data.length; k += w) {
            int maxbit = Util.maxbits(data, k, w);
            int lowestcost = maxbit * w;
            int ab = maxbit;
            int nofe = 0;
            for (int b = 0; b <= maxbit; ++b) {
                int numberofexceptions = 0;
                for (int z = k; z < k + w; ++z) {
                    if (Util.bits(data[z]) > b)
                        ++numberofexceptions;
                }
                int thiscost = b * w + numberofexceptions * (8 + maxbit - b);
                if (thiscost < lowestcost) {
                    lowestcost = thiscost;
                    ab = b;
                    nofe = numberofexceptions;
                }
            }
            buffer[maxbit - ab] += nofe;
            if (nofe == 0)
                cost += 2;
            else
                cost += 3 + nofe;

            cost += (ab * w + 7) / 8;
        }
        for (int k = 0; k < buffer.length; ++k) {
            cost += (buffer[k] + 31) / 32 * 32 * k / 8;
        }
        return cost;
    }

    public static int blockedfastpfor(int[] data, int w) {
        int cost = 0;
        for (int k = 0; k + w <= data.length; k += w) {
            int maxbit = Util.maxbits(data, k, w);
            int lowestcost = maxbit * w;
            int ab = maxbit;
            int nofe = 0;
            for (int b = 0; b <= maxbit; ++b) {
                int numberofexceptions = 0;
                for (int z = k; z < k + w; ++z) {
                    if (Util.bits(data[z]) > b)
                        ++numberofexceptions;
                }
                int thiscost = b * w + numberofexceptions * (8 + maxbit - b);
                if (thiscost < lowestcost) {
                    lowestcost = thiscost;
                    ab = b;
                    nofe = numberofexceptions;
                }
            }
            cost += (nofe * (8 + maxbit - ab) + 7) / 8;
            if (nofe == 0)
                cost += 2;
            else
                cost += 3 + nofe;

            cost += (ab * w + 7) / 8;
        }
        return cost;
    }

    public static void process(int[] data, int Max) {
        int N = data.length;
        java.text.DecimalFormat df = new java.text.DecimalFormat("0.00");

        System.out.println("reasonable lower bound "
                + df.format(binarypackinglowerbound(data) * 8.0 / N));
        System.out.println("using a bitmap " + df.format(Max * 1.0 / N));
        System.out.println("nate " + df.format(nate(data) * 8.0 / N));
        System.out.println("binary packing (32) "
                + df.format(binarypacking(data, 32) * 8.0 / N));
        System.out.println("binary packing (128) "
                + df.format(binarypacking(data, 128) * 8.0 / N));
        System.out.println("fastpfor (128) "
                + df.format(fastpfor(data, 128) * 8.0 / N));
        System.out.println("fastpfor (256) "
                + df.format(fastpfor(data, 256) * 8.0 / N));
        System.out.println("blockedfastpfor (128) "
                + df.format(fastpfor(data, 128) * 8.0 / N));
        System.out.println("blockedfastpfor (256) "
                + df.format(fastpfor(data, 256) * 8.0 / N));

        System.out.println("varint " + df.format(varint(data) * 8.0 / N));
        System.out.println("idealvarint "
                + df.format(idealvarint(data) * 8.0 / N));
        System.out.println("simple4b " + df.format(simple4b(data) * 8.0 / N));
        System.out.println("simple8b " + df.format(simple8b(data) * 8.0 / N));
        System.out.println("simple16b " + df.format(simple16b(data) * 8.0 / N));
        System.out.println("packedvarint4 "
                + df.format(packedvarint(data, 4) * 8.0 / N));
        System.out.println("packedvarint8 "
                + df.format(packedvarint(data, 8) * 8.0 / N));
        System.out.println("varintgb " + df.format(varintgb(data) * 8.0 / N));

        System.out.println();
    }

    public static void main(String[] args) {
        int Max = 1 << 22;
        System.out.println("We estimate the number of bits per int.");
        System.out.println("First with uniform data.");
        UniformDataGenerator udg = new UniformDataGenerator();
        double[] P = { 0.99, 0.95, 0.5, 0.1 };
        for (double p : P) {
            System.out.println("uniform distribution with density = " + p);
            int[] array = udg.generateUniform((int) Math.round(Max * p), Max);
            for (int k = array.length - 1; k > 0; --k)
                array[k] -= array[k - 1] + 1;
            process(array, Max);
        }
        Max = 1 << 25;
        System.out.println("Next with cluster data.");
        ClusteredDataGenerator cdg = new ClusteredDataGenerator();
        for (int N = 131072; N <= 1048576; N *= 8) {
            System.out.println("N=" + N);
            int[] data = cdg.generateClustered(N, Max);
            for (int k = data.length - 1; k > 0; --k)
                data[k] -= data[k - 1] + 1;
            // to make things fun, I will add a few jumps...
            java.util.Random r = new java.util.Random();
            for (int k = 0; k < N * 0.01; ++k) {
                int loc = Math.abs(r.nextInt()) % data.length;
                data[loc] += Math.abs(r.nextInt()) % (1 << 8);
            }
            process(data, Max);
        }
    }

}