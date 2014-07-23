public class RunCostModels {
    
    // this estimates the number of bytes used by binary packing with with w
    // to compress data
    public  static int binarypacking(int[] data, int w){
        int cost = 0;
        for(int k = 0; k+w <= data.length; k += w) {
            cost += 1; // store bit width
            cost += (Util.maxbits(data, k,w)*w+7)/8; 
        } 
        return cost;
    }
    public  static int binarypackinglowerbound(int[] data){
        int cost = 0;
        for(int k = 0; k < data.length;++k) {
            cost += Util.bits(data[k]);
        } 
        return (cost+7)/8;
    }

    public  static int varint(int[] data){
        int cost = 0;
        for(int v : data) {
            if(v<(1<<7)){
                cost += 1;
             } else if (v<(1<<14)){
                cost += 2;
             } else if(v<(1<<21)){
                cost += 3;
             } else cost += 4;
        }
        return cost;
    }
    public  static int idealvarint(int[] data){
        int cost = 0;
        for(int v : data) {
            if(v==0){
                cost += 0;
             } else if (v<(1<<8)){
                cost += 1;
             } else if(v<(1<<16)){
                cost += 2;
             } else if(v<(1<<24)){
                cost += 3;
             } else
                 cost += 4;
        }
        return cost;
    }
    
    
    public  static int fastpfor(int[] data){
        int cost = 0;
        int w = 128;
        for(int k = 0; k+w <= data.length; k += w) {
            int maxbit = Util.maxbits(data, k,w);
            int lowestcost = maxbit * w;
            int ab = maxbit;
            int nofe = 0;
            for(int b = 0; b<=maxbit; ++b) {
                int numberofexceptions = 0;
                for(int z = k;z<k+w; ++z) {
                    if(Util.bits(data[z])>b) ++ numberofexceptions;
                }
                int thiscost = b*w + numberofexceptions * (8 + maxbit-b);
                if(thiscost < lowestcost) {
                    lowestcost = thiscost;
                    ab = b;
                    nofe = numberofexceptions;
                }
            }
            cost += (nofe * (8 + maxbit-ab) +7)/8;
            if(nofe==0) 
              cost +=2;
            else 
              cost += 3 + nofe; 
            
            cost += (ab*w+7)/8; 
        } 
        return cost;
    }
    
    
    public static void main(String[] args) {
        int Max = 1<<24;
        System.out.println("We estimate the number of bits per int.");
        ClusteredDataGenerator cdg = new ClusteredDataGenerator();
        for(int N = 4096; N<131072;N*=2){
            System.out.println("N="+N);
            int[] data = cdg.generateClustered(N, Max);
            for(int k = data.length-1; k>0; --k)
              data[k] -= data[k-1];
            System.out.println("reasonable lower bound "+binarypackinglowerbound(data)*8.0/N);

            System.out.println("binary packing (32) "+binarypacking(data,32)*8.0/N);
            System.out.println("binary packing (128) "+binarypacking(data,128)*8.0/N);
            System.out.println("fastpfor (128) "+fastpfor(data)*8.0/N);
            System.out.println("varint "+varint(data)*8.0/N);
            System.out.println("idealvarint "+idealvarint(data)*8.0/N);

            System.out.println();
        }
    }
    
}