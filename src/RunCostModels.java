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

    // all less than large, at most one greater or equal to small
    private static boolean __hybridvbyte(int[] data, int pos, int small, int large) {
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
        
        return largecnt <= 1;
    }

    // all less than large, at least one less than small
    private static boolean __hybridvbytereverse(int[] data, int pos, int small,
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
        return smallcnt >= 1;
    }

    public static int hybridvbyte(int[] data) {
        int cost = 0;
        int[] counters = new int[18];
        for (int k = 0; k < data.length;) {
            // we do something simple... not quite simple8b
            int left = data.length - k;
            if (Util.maxbits(data, k, Math.min(left, 32)) == 0) {
                // 32 numbers where delta is 0 (consecutive) (.25 b/d)
                k += 32;
                cost += 1;
                counters[0] += 32;
            } else if (Util.maxbits(data, k, Math.min(left, 4)) < 3) {
                // 4 numbers where deltas less than or equal to 3 (2 b/d)
                k += 4;
                cost += 1;
                counters[1] += 4;
            } else if (Util.maxbits(data, k, Math.min(left, 32)) <= 3) {
                // 32 numbers with deltas expressable as 3-bit  (3.25 b/d)
                k += 32;
                cost += 1 + (3 * 32)/8;
                counters[2] += 32;
            } else if (__hybridvbyte(data, k, 3, 256)) {
                // 3 tiny 0B deltas and one 1B. (4 b/d)
                k += 4;
                cost += 2;
                counters[3] += 4;
            } else if (Util.maxbits(data, k, Math.min(left, 32)) <= 4) {
                // 32 expressable as 4-bit (4.25 b/d)
                k += 32;
                cost += 1 + 4 * 32 / 8;
                counters[4] += 32;
            } else if (__hybridvbyte(data, k, 11, 256)) {
                // one tiny delta (< 11) and 3 small (< 256) (8 b/d)
                k += 4;
                cost += 4;
                counters[5] += 4;
            } else if (Util.maxbits(data, k, Math.min(left, 4)) <= 8) {
                // 4 that fit in a byte (10 b/d)
                k += 4;
                cost += 1 + 4;
                counters[6] += 4;
            } else if (__hybridvbyte(data, k, 256, 512)) {
                // 3 that fit in a byte, plus one less than 512 (10 b/d)
                k += 4;
                cost += 1 + 4;
                counters[7] += 4;
            } else if (__hybridvbyte(data, k, 256, 256 * 256)) {
                // 3 that fit in a byte, plus one that fits in two bytes (12 b/d)
                k += 4;
                cost += 1 + 3 + 2;
                counters[8] += 4;
            } else if (__hybridvbytereverse(data, k, 512, 256 * 256)) {
                // 4 that fit in 2 bytes, at least one less than 512 (16 b/d)
                k += 4;
                cost += 1 + 3 * 2 + 1;
                counters[9] += 4;
            } else if (Util.maxbits(data, k, Math.min(left, 4)) <= 16) {
                // 4 that fit in 2 bytes (18 b/d)
                k += 4;
                cost += 1 + 4 * 2;
                counters[10] += 4;
            } else if (__hybridvbyte(data, k, 256 * 256, 2 *256 * 256)) {
                // 3 that fit in 2 bytes plus one less than double that (18 b/d)
                k += 4;
                cost += 1 + 4 * 2;
                counters[11] += 4;
            } else if (__hybridvbyte(data, k, 256 * 256, 256 * 256 * 256)) {
                // 3 that fit in 2 bytes plus one that can fit in 3 bytes (20 b/d)
                k += 4;
                cost += 1 + 3 * 2 + 3;
                counters[12] += 4;
            } else if (__hybridvbytereverse(data, k, 2 * 256 * 256, 256 * 256 * 256)) {
                // one double-2B 3 x 3B (24 b/d)
                k += 4;
                cost += 1 + 2 + 3 * 3;
                counters[13] += 4;
            } else if (Util.maxbits(data, k, Math.min(left, 4)) <= 24) {
                // 4 that fit in 3B (26 b/d)
                k += 4;
                cost += 1 + 4 * 3;
                counters[14] += 4;
            } else if (__hybridvbyte(data, k, 256 * 256 * 256, 2 * 256 * 256 * 256)) {
                // 3 3B large elements plus one less than double-3B (26 b/d)
                k += 4;
                cost += 1 + 4 * 3;
                counters[15] += 4;
            } else if (__hybridvbyte(data, k, 256 * 256 * 256, 256 * 256 * 256 * 256)) {
                // 3 3B large elements plus one 4B huge (28 b/d)
                k += 4;
                cost += 1 + 3 * 3 + 4;
                counters[16] += 4;
            } else if (__hybridvbytereverse(data, k, 256 * 256 * 256, 256 * 256 * 256 * 256)) {
                // 1 3B large plus 3 4B huge (32 b/d)
                k += 4;
                cost += 1 + 3 * 4 + 3;
                counters[17] += 4;
            } else {
                // 4 4B huge elements (34 b/d)
                k += 4;
                cost += 1 + 4 * 4;
                throw new RuntimeException("really?");
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
            if (Util.maxbits(data, k, Math.min(left, 240)) <= 0) {
                k += 240;
            } else if (Util.maxbits(data, k, Math.min(left, 120)) <= 0) {
                    k += 120;
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
    public static int simplealt(int[] data) {
        double cost = 0;
        for (int k = 0; k < data.length;) {
            // we do something simple... 
            int left = data.length - k;
            cost += 0.5;
            if (Util.maxbits(data, k, Math.min(left, 256)) <= 0) {
                k += 256;
            } else if (Util.maxbits(data, k, Math.min(left, 128)) <= 0) {
                k += 128;                
            } else if (Util.maxbits(data, k, Math.min(left, 64)) <= 1) {
                k += 64;
                cost += 64/8;
            } else if (Util.maxbits(data, k, Math.min(left, 32)) <= 2) {
                k += 32;
                cost += 64/8;
            } else if (Util.maxbits(data, k, Math.min(left, 32)) <= 3) {
                k += 32;
                cost += 3*32/8;
            } else if (Util.maxbits(data, k, Math.min(left, 16)) <= 4) {
                k += 16;
                cost += 4*16/8;
            } else if (Util.maxbits(data, k, Math.min(left, 32)) <= 5) {
                k += 32;
                cost += 5*32/8;
            } else if (Util.maxbits(data, k, Math.min(left, 32)) <= 6) {
                k += 32;
                cost += 6*32/8;
            } else if (Util.maxbits(data, k, Math.min(left, 32)) <= 7) {
                k += 32;
                cost += 7*32/8;
            } else if (Util.maxbits(data, k, Math.min(left, 4)) <= 8) {
                k += 4;
                cost += 4;
            } else if (Util.maxbits(data, k, Math.min(left, 32)) <= 10) {
                k += 32;
                cost += 10*32/8;
            } else if (Util.maxbits(data, k, Math.min(left, 32)) <= 12) {
                k += 32;
                cost += 12*32/8;
            } else if (Util.maxbits(data, k, Math.min(left, 4)) <= 16) {
                k += 4;
                cost += 16*4/8;
            } else if (Util.maxbits(data, k, Math.min(left, 4)) <= 24) {
                k += 4;
                cost += 24*4/8;
            } else if (Util.maxbits(data, k, Math.min(left, 2)) <= 32) {
                k += 2;
                cost += 64;
            } else {
                k += 1;
                cost += 64;
            }

        }
        return (int) Math.round( cost);
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
        boolean verbose = false;
        int counter[] = new int[33];
        int total = 0;

        for (int k = 0; k < data.length;) {
            int left = data.length - k;
            ++total;
            if (Util.maxbits(data, k, Math.min(left, 32)) <= 0) {
                cost += 4;
                ++counter[0];
                k += 32 ;
                continue;
            }
            for (int b = 1; b <= 32; ++b) {
                if (28 / (b + 1) == 28 / b)
                    continue;
                if (Util.maxbits(data, k, Math.min(left, 28 / b)) <= b) {
                    k += 32 / b;
                    ++counter[b];
                    break;
                }
            }
            cost += 4;
        }    
        if(verbose) {
         for (int b = 0; b <= 32; ++b) {
            if(counter[b] >0)System.out.print(" b="+b+"("+Math.round(counter[b]*100.0/total)+"%)");
         }
         System.out.println();
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
    public static int exppfor(int[] data) {
        double cost = 0;
        int w = 256;
        for (int k = 0; k + w <= data.length; k += w) {
            int totalsecondmax = 0;
            int totalmax = 0;
            for(int j = 0; j<w; j+=16){
                int tmax = 0;
                int secondmax = 0;
                for (int i = 0; i<16;++i){
                    int val =data[k+j+i]; 
                    if(val>tmax) tmax = val;
                    else if(val > secondmax ) secondmax = val;
                }
                if(totalmax<tmax) totalmax = tmax;
                if(totalsecondmax<secondmax) totalsecondmax = secondmax;
            }
            //System.out.println(Util.bits(totalmax) - Util.bits(totalsecondmax) );
            double cost1 = (Util.bits(totalsecondmax) * w + 7) / 8 + 2 + w/16*(0.5+(Util.bits(totalmax) - Util.bits(totalsecondmax))/8.0);
            double cost2 = (Util.bits(totalmax) * w + 7) / 8 + 2;
            if(cost1<cost2) cost += cost1; else cost +=cost2;
        }
        return (int) Math.round(cost);
    }

    public static int fastpfor(int[] data, int w) {
        int cost = 0;
        int[] buffer = new int[33];
        int[][] used = new int[40][256];  // [exceptionWidth][numExceptions]

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
                // thiscost = chosen base bits + numExceptions * 
                //     (8-bits for position + bits necessary to store exception)
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
            else {
                cost += 3 + nofe;
            }

            // increment used[exceptionWidth][numExceptions]
            used[maxbit - ab][nofe] += 1;

            cost += (ab * w + 7) / 8;
        }

        if (false) {
            for (int i = 0; i < 8; i += 1)  {
                System.out.print( "Width " + i + ": ");
                for (int j = 0; j < 32; j += 1) {
                    System.out.print(used[i][j] + " ");
                }
                System.out.println();
            }
        }
                

        for (int k = 0; k < buffer.length; ++k) {
            cost += (buffer[k] + 31) / 32 * 32 * k / 8;
        }
        return cost;
    }
    public static int natepfor(int[] data) {
        int cost = 0;
        int w = 32;
        for (int k = 0; k + w <= data.length; k += w) {
            int maxbit = Util.maxbits(data, k, w);
            int lowestcost = maxbit * w;
            int ab = maxbit;
            int nofe = 0;
            for (int b = Math.max(0,maxbit-3); b <= maxbit; ++b) {
                int numberofexceptions = 0;
                for (int z = k; z < k + w; ++z) {
                    if (Util.bits(data[z]) > b)
                        ++numberofexceptions;
                }
                int thiscost = b * w + numberofexceptions * 8;
                if (thiscost < lowestcost) {
                    lowestcost = thiscost;
                    ab = b;
                    nofe = numberofexceptions;
                }
            }
            cost += 1 + nofe;
            cost += (ab * w + 7) / 8;
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
        if(N<4*256) {
            System.out.println("==Some models do not support very small arrays.");
        }
        java.text.DecimalFormat df = new java.text.DecimalFormat("0.00");

        System.out.println("reasonable lower bound "
                + df.format(binarypackinglowerbound(data) * 8.0 / N));
        System.out.println("using a bitmap " + df.format(Max * 1.0 / N));
        System.out.println("hybridvbyte " + df.format(hybridvbyte(data) * 8.0 / N));
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
        System.out.println("exppfor (256) "
                + df.format(exppfor(data) * 8.0 / N));
        System.out.println("natepfor (32) "
                + df.format(natepfor(data) * 8.0 / N));
        System.out.println("varint " + df.format(varint(data) * 8.0 / N));
        System.out.println("idealvarint "
                + df.format(idealvarint(data) * 8.0 / N));
        System.out.println("simple4b " + df.format(simple4b(data) * 8.0 / N));
        System.out.println("simple8b " + df.format(simple8b(data) * 8.0 / N));
        System.out.println("simple16b " + df.format(simple16b(data) * 8.0 / N));
        System.out.println("simplealt " + df.format(simplealt(data) * 8.0 / N));
        System.out.println("packedvarint4 "
                + df.format(packedvarint(data, 4) * 8.0 / N));
        System.out.println("packedvarint8 "
                + df.format(packedvarint(data, 8) * 8.0 / N));
        System.out.println("varintgb " + df.format(varintgb(data) * 8.0 / N));

        System.out.println();
    }

    public static void main(String[] args) {
        int Max = 1 << 24;
        System.out.println("We estimate the number of bits per int.");
        System.out.println("First with uniform data.");
        UniformDataGenerator udg = new UniformDataGenerator();
        double[] P = { 0.99, 0.95, .90, .85, .80, .75, .70, .65, .60, .55, .5, .45, .40, .35, .30, .25, .20, 
                       .15, 0.1, .05, .04, .03, .02, .01, .001, .0001, .00001, .000001 };
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
        for (int N = 131072; N <= 1048576; N *= 2) {
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