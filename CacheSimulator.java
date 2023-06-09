import java.io.File;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
class Line {
    long time;
    char validBit;
    int tag;
    byte[] data;

    public Line(int blockSize) {
        this.data = new byte[blockSize];
    }
}

class Set {
    Line[] lines;

    public Set(int E, int blockSize) {
        this.lines = new Line[E];
        for (int i = 0; i < E; i++) {
            this.lines[i] = new Line(blockSize);
        }
    }
}

public class CacheSimulator {

    static int L1s, L1E, L1b, L2s, L2E, L2b, L1S, L1B, L2S, L2B;
    static String fileName;

    static int L1I_hit = 0, L1I_miss = 0, L1I_eviction = 0;
    static int L1D_hit = 0, L1D_miss = 0, L1D_eviction = 0;
    static int L2_hit = 0, L2_miss = 0, L2_eviction = 0;

    static int i, j;

    public static void main(String[] args) {

        if (args.length == 0) {
            System.out.println("You didn't enter any command-line arguments. Please enter them.");
            return;
        } else if (args.length < 14) {
            System.out.println("You entered " + (args.length - 1) + " number of command-line arguments, but you need to "
                    + "enter 14 values.");
            return;
        }

        setGlobalVariables(args);
        Set[] L1I = new Set[L1S];
        Set[] L1D = new Set[L1S];
        Set[] L2 = new Set[L2S];

        createCaches(L1I, L1D, L2);
        setValidBitsToZero(L1I, L1D, L2);
        traceFile(L1I, L1D, L2);

        System.out.println();
        printSummary("L1I", L1I_hit, L1I_miss, L1I_eviction);
        printSummary("L1d", L1D_hit, L1D_miss, L1D_eviction);
        printSummary("L2", L2_hit, L2_miss, L2_eviction);
        System.out.println();

        cleanUp(L1I, L1D, L2);
    }

    public static void setGlobalVariables(String[] args) {

        L1s = Integer.parseInt(args[1]);
        L1E = Integer.parseInt(args[3]);
        L1b = Integer.parseInt(args[5]);
        L2s = Integer.parseInt(args[7]);
        L2E = Integer.parseInt(args[9]);
        L2b = Integer.parseInt(args[11]);
        fileName = args[13];

        L1S = (int) Math.pow(2, L1s);
        L2S = (int) Math.pow(2, L2s);

        L1B = (int) Math.pow(2, L1b);
        L2B = (int) Math.pow(2, L2b);
    }

    public static void createCaches(Set[] L1I, Set[] L1D, Set[] L2) {

        for (i = 0; i < L1S; i++) {
            L1I[i] = new Set(L1E, L1B);
            L1D[i] = new Set(L1E, L1B);
        }

        for (i = 0; i <L2S; i++) {
            L2[i] = new Set(L2E, L2B);
        }
    }

    public static void setValidBitsToZero(Set[] L1I, Set[] L1D, Set[] L2) {

        for (i = 0; i < L1S; i++) {
            for (j = 0; j < L1E; j++) {

                L1I[i].lines[j].validBit = 0;
                L1D[i].lines[j].validBit = 0;
            }
        }

        for (i = 0; i < L2S; i++) {
            for (j = 0; j < L2E; j++) {

                L2[i].lines[j].validBit = 0;
            }
        }
    }

    public static void traceFile(Set[] L1I, Set[] L1D, Set[] L2) {

        File file = new File(fileName);

        if (!file.exists()) {
            System.out.println(fileName + " does not exist. Please make sure it exists before starting this "
                    + "program.");
            return;
        }

        char operation;
        int size;
        String address, data;

        try {
            RandomAccessFile raf = new RandomAccessFile(file, "r");

            while ((operation = raf.readChar()) != -1) {

                switch (operation) {

                    case 'I':

                        address = raf.readUTF();
                        size = raf.readInt();
                        raf.readChar(); // read the newline character

                        System.out.println("\nI " + address + ", " + size);
                        loadInstructionOrData(true, "L1I", L1I, L1D, L2, address, size);
                        break;
                    case 'L':

                        address = raf.readUTF();
                        size = raf.readInt();
                        raf.readChar(); // read the newline character

                        System.out.println("\nL " + address + ", " + size);
                        loadInstructionOrData(false, "L1D", L1I, L1D, L2, address, size);
                        break;
                    case 'S':

                        address = raf.readUTF();
                        size = raf.readInt();
                        data = raf.readUTF();
                        raf.readChar(); // read the newline character

                        System.out.println("\nS " + address + ", " + size + ", " + data);
                        dataStore(L1D, L2, address, size, data);
                        break;
                    case 'M':

                        address = raf.readUTF();
                        size = raf.readInt();
                        data = raf.readUTF();
                        raf.readChar(); // read the newline character

                        System.out.println("\nM " + address + ", " + size + ", " + data);
                        dataModify(L1I, L1D, L2, address, size, data);
                        break;
                    default:

                        System.out.println("\nThe program encountered an invalid operation type, "
                                + "exiting...");
                        return;
                }
            }

            raf.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadInstructionOrData(boolean loadInstruction,
                                             String cacheName,
                                             Set[] L1I, Set[] L1D, Set[] L2, String address, int size) {
        Set setToUse;

        if (loadInstruction)
            setToUse = L1I;
        else
            setToUse = L1D;

        int int_address = new BigInteger(address, 16).intValue();

        int blockNumL1 = int_address & ((int) Math.pow(2, L1b) - 1);
        int setNumL1 = (int_address >>> L1b) & ((int) Math.pow(2, L1s) - 1);
        int tagSizeL1 = 32 - (L1s + L1b);
        int tagL1 =
                (int_address >>> (L1s + L1b)) & ((int) Math.pow(2, tagSizeL1) - 1);

        int blockNumL2 = int_address & ((int) Math.pow(2, L2b) - 1);
        int setNumL2 = (int_address >>> L2b) & ((int) Math.pow(2, L2s) - 1);
        int tagSizeL2 = 32 - (L2s + L2b);
        int tagL2 =
                (int_address >>> (L2s + L2b)) & ((int) Math.pow(2, tagSizeL2) - 1);

        if (blockNumL1 + size > L1B || blockNumL2 + size > L2B) {

            System.out.println("\nCan't fit the data for the size of wanted block. Please edit "
                    + "your trace file...");
            System.exit(0);
        }

        System.out.print("\n   ");

        int L1_location = returnLineNumberIfStored(setToUse, setNumL1, tagL1);
        int L2_location = returnLineNumberIfStored(L2, setNumL2, tagL2);

        if (L1_location != -1) {

            System.out.print(cacheName + " hit");
            if (loadInstruction)
                L1I_hit++;
            else
                L1D_hit++;

            setToUse[setNumL1].lines[L1_location].time = System.currentTimeMillis();

            if (L2_location != -1) {

                System.out.println(", L2 hit");
                L2_hit++;

                L2[setNumL2].lines[L2_location].time = System.currentTimeMillis();

            } else {

                System.out.println(", L2 miss");
                L2_miss++;

                L2_eviction += copyInstructionBetweenCaches(
                        setToUse, L2, L1_location, setNumL1, setNumL2, tagL2, L2E, L2B);
            }

        } else {

            System.out.print(cacheName + " miss");
            if (loadInstruction)
                L1I_miss++;
            else
                L1D_miss++;

            if (L2_location != -1) {

                System.out.println(", L2 hit");
                L2_hit++;

                L2[setNumL2].lines[L2_location].time = System.currentTimeMillis();

                int eviction_counter = copyInstructionBetweenCaches(
                        L2, setToUse, L2_location, setNumL2, setNumL1, tagL1, L1E, L1B);

                if (loadInstruction)
                    L1I_eviction += eviction_counter;
                else
                    L1D_eviction += eviction_counter;

            } else {

                System.out.println(", L2 miss");
                L2_miss++;

                L2_location =
                        copyInstructionFromRamToL2Cache(int_address, L2, setNumL2, tagL2);
                int eviction_counter = copyInstructionBetweenCaches(
                        L2, setToUse, L2_location, setNumL2, setNumL1, tagL1, L1E, L1B);

                if (loadInstruction)
                    L1I_eviction += eviction_counter;
                else
                    L1D_eviction += eviction_counter;
            }
        }

        System.out.println("   Place in L2 set " + setNumL2 + ", " + cacheName + " set " + setNumL1);
    }

    public static void dataStore(Set[] L1D, Set[] L2, String address, int size,
                                 String data) {

        int int_address = new BigInteger(address, 16).intValue();

        byte[] byte_data = new byte[size];
        String subString;

        for (int a = 0; a < size; a++) {

            subString = data.substring(a * 2, a * 2 + 2);
            byte_data[a] = new BigInteger(subString, 16).byteValue();
        }

        int blockNumL1 = int_address & ((int) Math.pow(2, L1b) - 1);
        int setNumL1 = (int_address >>> L1b) & ((int) Math.pow(2, L1s) - 1);
        int tagSizeL1 = 32 - (L1s + L1b);
        int tagL1 =
                (int_address >>> (L1s + L1b)) & ((int) Math.pow(2, tagSizeL1) - 1);

        int blockNumL2 = int_address & ((int) Math.pow(2, L2b) - 1);
        int setNumL2 = (int_address >>> L2b) & ((int) Math.pow(2, L2s) - 1);
        int tagSizeL2 = 32 - (L2s + L2b);
        int tagL2 =
                (int_address >>> (L2s + L2b)) & ((int) Math.pow(2, tagSizeL2) - 1);

        if (blockNumL1 + size > L1B || blockNumL2 + size > L2B) {

            System.out.println("\nCan't fit the data for the size of wanted block. Please edit "
                    + "your trace file...");
            System.exit(0);
        }

        System.out.print("\n   ");

        int L1D_location = returnLineNumberIfStored(L1D, setNumL1, tagL1);
        int L2_location = returnLineNumberIfStored(L2, setNumL2, tagL2);

        if (L1D_location != -1) {

            System.out.print("L1D hit");
            L1D_hit++;

            // Now, we need to store our data in L1D.
            L1D[setNumL1].lines[L1D_location].time = System.currentTimeMillis();
            System.arraycopy(byte_data, 0,
                    L1D[setNumL1].lines[L1D_location].data,
                    blockNumL1, size);

            // Now, lets check if the data is in L2 cache.
            if (L2_location != -1) {

                System.out.println(", L2 hit");
                L2_hit++;

                L2[setNumL2].lines[L2_location].time = System.currentTimeMillis();
                System.arraycopy(byte_data, 0,
                        L2[setNumL2].lines[L2_location].data,
                        blockNumL2, size);

            } else {

                System.out.println(", L2 miss");
                L2_miss++;

                L2_eviction += copyInstructionBetweenCaches(
                        L1D, L2, L1D_location, setNumL1, setNumL2, tagL2, L2E, L2B);
            }

        } else {

            System.out.print("L1D miss");
            L1D_miss++;

            if (L2_location != -1) {

                System.out.println(", L2 hit");
                L2_hit++;

                L1D_eviction += copyInstructionBetweenCaches(
                        L2, L1D, L2_location, setNumL2, setNumL1, tagL1, L1E, L1B);

            } else {

                System.out.println(", L2 miss");
                L2_miss++;

                L2_location =
                        copyInstructionFromRamToL2Cache(int_address, L2, setNumL2, tagL2);
                L1D_eviction = copyInstructionBetweenCaches(
                        L2, L1D, L2_location, setNumL2, setNumL1, tagL1, L1E, L1B);
            }

            L1D_location = returnLineNumberIfStored(L1D, setNumL1, tagL1);

            L1D[setNumL1].lines[L1D_location].time = System.currentTimeMillis();
            System.arraycopy(byte_data, 0,
                    L1D[setNumL1].lines[L1D_location].data,
                    blockNumL1, size);

            copyInstructionBetweenCaches(L1D, L2, L1D_location,
                    setNumL1, setNumL2,
                    tagL2, L2E, L2B);
        }

        copyDataToRAM(int_address, size, byte_data);

        System.out.println("   Store in L1D, L2, RAM");
    }

    public static void dataModify(Set[] L1I, Set[] L1D, Set[] L2,
                                  String address, int size,
                                  String data) {

        loadInstructionOrData(false, "L1D", L1I, L1D, L2, address, size);
        dataStore(L1D, L2, address, size, data);
    }

    public static int returnLineNumberIfStored(Set[] L, int setNum,
                                               int tag) {

        for (i = 0; i < L[setNum].lines.length; i++) {

            if (L[setNum].lines[i].validBit == 1 && L[setNum].lines[i].tag == tag)
                return i;
        }

        return -1;
    }

    public static int copyInstructionBetweenCaches(Set[] source,
                                                   Set[] dest,
                                                   int sourceLineNumber,
                                                   int sourceSetNum,
                                                   int destSetNum,
                                                   int destTag,
                                                   int destTotalLine,
                                                   int destBlockSize) {

        int returnValue = 0;

        boolean freeSlot = false;
        int lineToPlace = 0;

        for (i = 0; i < destTotalLine; i++) {

            if (dest[destSetNum].lines[i].validBit == 0) {
                freeSlot = true;
                lineToPlace = i;
                break;
            }
        }

        if (!freeSlot) {

            returnValue++;

            long current = System.currentTimeMillis();

            for (i = 0; i < destTotalLine; i++) {

                if (current > dest[destSetNum].lines[i].time) {
                    current = dest[destSetNum].lines[i].time;
                    lineToPlace = i;
                }
            }
        }

        dest[destSetNum].lines[lineToPlace].validBit = 1;
        dest[destSetNum].lines[lineToPlace].tag = destTag;
        dest[destSetNum].lines[lineToPlace].time = System.currentTimeMillis();
        System.arraycopy(source[sourceSetNum].lines[sourceLineNumber].data,
                0,
                dest[destSetNum].lines[lineToPlace].data,
                0,
                destBlockSize);

        return returnValue;
    }

    public static int copyInstructionFromRamToL2Cache(int int_address,
                                                      Set[] L2,
                                                      int setNumL2,
                                                      int tagL2) {

        File RAM = new File("RAM.dat");

        try {
            RandomAccessFile raf = new RandomAccessFile(RAM, "r");

            int address = int_address & (0xFFFFFFFF ^ (L2B - 1));

            raf.seek(address);

            byte[] readData = new byte[L2B];
            raf.read(readData);

            raf.close();

            boolean freeSlot = false;
            int lineToPlace = 0;

            for (i = 0; i < L2E; i++) {

                if (L2[setNumL2].lines[i].validBit == 0) {
                    freeSlot = true;
                    lineToPlace = i;
                    break;
                }
            }

            if (!freeSlot) {

                L2_eviction++;

                long current = System.currentTimeMillis();

                for (i = 0; i < L2E; i++) {

                    if (current > L2[setNumL2].lines[i].time) {
                        current = L2[setNumL2].lines[i].time;
                        lineToPlace = i;
                    }
                }
            }

            L2[setNumL2].lines[lineToPlace].validBit = 1;
            L2[setNumL2].lines[lineToPlace].tag = tagL2;
            L2[setNumL2].lines[lineToPlace].time = System.currentTimeMillis();
            System.arraycopy(readData, 0,
                    L2[setNumL2].lines[lineToPlace].data,
                    0,
                    L2B);

            return lineToPlace;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;
    }

    public static void copyDataToRAM(int int_address, int size,
                                     byte[] data) {

        File RAM = new File("RAM.dat");

        try {
            RandomAccessFile raf = new RandomAccessFile(RAM, "rw");

            raf.seek(int_address);

            raf.write(data, 0, size);

            raf.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void printSummary(String cacheName, int hit, int miss,
                                    int eviction) {

        System.out.println("\n" + cacheName + "-hits: " + hit + " " + cacheName
                + "-misses: " + miss + " " + cacheName + "-evictions: " + eviction);
    }

    public static void cleanUp(Set[] L1I, Set[] L1D, Set[] L2) {

        for (i = 0; i < L1S; i++) {

            for (j = 0; j < L1E; j++) {
                L1I[i].lines[j].data = null;
                L1D[i].lines[j].data = null;
            }

            L1I[i].lines = null;
            L1D[i].lines = null;
        }

        for (i = 0; i < L2S; i++) {

            for (j = 0; j < L2E; j++)
                L2[i].lines[j].data = null;
            L2[i].lines = null;
        }
    }
}
