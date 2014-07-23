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
    public  static int simple8b(int[] data){
        int cost = 0;
        for(int k = 0; k<data.length; ) {
            // we do something simple... not quite simple8b
            int left = data.length - k;
            if(Util.maxbits(data, k,Math.min(left,90))<=0){
                k+=90;
            } else if(Util.maxbits(data, k,Math.min(left,60))<=1){
                k+=60;
            } else if(Util.maxbits(data, k,Math.min(left,30))<=2){
                k+=30;
            } else if(Util.maxbits(data, k,Math.min(left,20))<=3){
                k+=20;
            } else if(Util.maxbits(data, k,Math.min(left,15))<=4){
                k+=15;
            } else if(Util.maxbits(data, k,Math.min(left,12))<=5){
                k+=12;
            } else if(Util.maxbits(data, k,Math.min(left,10))<=6){
                k+=10;
            } else if(Util.maxbits(data, k,Math.min(left,8))<=7){
                k+=8;
            } else if(Util.maxbits(data, k,Math.min(left,7))<=8){
                k+=7;
            } else if(Util.maxbits(data, k,Math.min(left,6))<=10){
                k+=6;
            } else if(Util.maxbits(data, k,Math.min(left,5))<=12){
                k+=5;
            } else if(Util.maxbits(data, k,Math.min(left,4))<=15){
                k+=4;
            } else if(Util.maxbits(data, k,Math.min(left,3))<=20){
                k+=3;
            } else if(Util.maxbits(data, k,Math.min(left,2))<=30){
                k+=2;
            } else {
                k+=1;
            }
            cost += 8;
        }
        return cost;
    }
    public  static int simple16b(int[] data){
        int cost = 0;
        for(int k = 0; k<data.length; ) {
            int left = data.length - k;
            for(int b = 1; b <=32;++b){
                if(Util.maxbits(data, k,Math.min(left,128/b))<=b)
                {k+= 128/b; break;}
            }
            cost += 16+1;
        }
        return cost;
    }
    public  static int simple4b(int[] data){
        int cost = 0;
        for(int k = 0; k<data.length; ) {
            int left = data.length - k;
            for(int b = 1; b <=32;++b){
                if(28/(b+1) == 28 /b) continue;
                if(Util.maxbits(data, k,Math.min(left,28/b))<=b)
                {k+= 32/b; break;}
            }
            cost += 4;
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
    
    
    public  static int fastpfor(int[] data,int w){
        int cost = 0;
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
        int Max = 1<<25;
        System.out.println("We estimate the number of bits per int.");
        ClusteredDataGenerator cdg = new ClusteredDataGenerator();
        for(int N = 1048576; N<=524288*8;N*=4){
            System.out.println("N="+N);
            int[] data = cdg.generateClustered(N, Max);
            for(int k = data.length-1; k>0; --k)
              data[k] -= data[k-1];
            // to make things fun, I will add a few jumps...
            java.util.Random r= new java.util.Random();
            for(int k = 0; k<N*0.01;++k) {
                int loc = Math.abs(r.nextInt())%data.length; 
                data[loc]+=Math.abs(r.nextInt()) % (1<<8);
            }
            System.out.println("reasonable lower bound "+binarypackinglowerbound(data)*8.0/N);

            System.out.println("binary packing (32) "+binarypacking(data,32)*8.0/N);
            System.out.println("binary packing (128) "+binarypacking(data,128)*8.0/N);
            System.out.println("fastpfor (128) "+fastpfor(data,128)*8.0/N);
            System.out.println("fastpfor (256) "+fastpfor(data,256)*8.0/N);
            
            System.out.println("varint "+varint(data)*8.0/N);
            System.out.println("idealvarint "+idealvarint(data)*8.0/N);
            System.out.println("simple4b "+simple4b(data)*8.0/N);
            System.out.println("simple8b "+simple8b(data)*8.0/N);
            System.out.println("simple16b "+simple16b(data)*8.0/N);
            

            System.out.println();
        }
    }
    
}