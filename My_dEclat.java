package ca.pfv.spmf.test.my_test;

import ca.pfv.spmf.datastructures.triangularmatrix.TriangularMatrix;
import ca.pfv.spmf.input.transaction_database_list_integers.TransactionDatabase;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets;
import ca.pfv.spmf.tools.MemoryLogger;
import org.roaringbitmap.RoaringBitmap;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class My_dEclat {
    /**
     * relative minimum support
     **/
    private int minsupRelative;

    /**
     * the transaction database
     **/
    protected TransactionDatabase database;

    /**
     * start time of the last execution
     */
    protected long startTimestamp;
    /**
     * end  time of the last execution
     */
    protected long endTime;

    /**
     * The  patterns that are found
     * (if the user wants to keep them into memory)
     */
    protected Itemsets frequentItemsets;

    /**
     * object to write the output file
     */
    BufferedWriter writer = null;

    /**
     * the number of patterns found
     */
    protected int itemsetCount;

    /**
     * For optimization with a triangular matrix for counting
     * / itemsets of size 2.
     */
    private TriangularMatrix matrix;

    /**
     * buffer for storing the current itemset that is mined when performing mining
     * the idea is to always reuse the same buffer to reduce memory usage.
     */
    final int BUFFERS_SIZE = 2000;

    /**
     * size of the buffer
     */
    private int[] itemsetBuffer = null;

    /**
     * if true, transaction identifiers of each pattern will be shown
     */
    boolean showTransactionIdentifiers = false;

    /**
     * Special parameter to set the maximum size of itemsets to be discovered
     */
    int maxItemsetSize = Integer.MAX_VALUE;

    //public static int count_ca=0;

    /**
     * Default constructor
     */
    public My_dEclat() {

    }


    /**
     * Run the algorithm.
     *
     * @param database                        a transaction database
     * @param output                          an output file path for writing the result or if null the result is saved into memory and returned
     * @param minsupp                         the minimum support
     * @param useTriangularMatrixOptimization if true the triangular matrix optimization will be applied.
     * @return the result
     * @throws IOException exception if error while writing the file.
     */
    public Itemsets runAlgorithm(String output, TransactionDatabase database, double minsupp,
                                 boolean useTriangularMatrixOptimization) throws IOException {

        MemoryLogger.getInstance().reset();

        // initialize the buffer for storing the current itemset
        itemsetBuffer = new int[BUFFERS_SIZE];

        // if the user wants to keep the result into memory
        if (output == null) {
            writer = null;
            frequentItemsets = new Itemsets("FREQUENT ITEMSETS");
        } else { // if the user wants to save the result to a file
            frequentItemsets = null;
            writer = new BufferedWriter(new FileWriter(output));
        }

        // reset the number of itemset found to 0
        itemsetCount = 0;

        this.database = database;

        // record the start time
        startTimestamp = System.currentTimeMillis();

        // convert from an absolute minsup to a relative minsup by multiplying
        // by the database size
        this.minsupRelative = (int) Math.ceil(minsupp * database.size());

        // (1) First database pass : calculate tidsets of each item.
        // This map will contain the tidset of each item
        // Key: item   Value :  tidset
        //final Map<Integer, Set<Integer>> mapItemCount = new HashMap<Integer, Set<Integer>>();
        // for each transaction
        final Map<Integer, BitSetSupport> mapItemTIDS = new HashMap<Integer, BitSetSupport>();
        int maxItemId = calculateSupportSingleItems(database,  mapItemTIDS);
        //int maxItemId = calculateSupportSingleItems(database, mapItemCount);
        // if the user chose to use the triangular matrix optimization
        // for counting the support of itemsets of size 2.
        if (useTriangularMatrixOptimization && maxItemsetSize >= 1) {
            // We create the triangular matrix.
            matrix = new TriangularMatrix(maxItemId + 1);
            // for each transaction, take each itemset of size 2,
            // and update the triangular matrix.
            for (List<Integer> itemset : database.getTransactions()) {
                Object[] array = itemset.toArray();
                // for each item i in the transaction
                for (int i = 0; i < itemset.size(); i++) {
                    Integer itemI = (Integer) array[i];
                    // compare with each other item j in the same transaction
                    for (int j = i + 1; j < itemset.size(); j++) {
                        Integer itemJ = (Integer) array[j];
                        // update the matrix count by 1 for the pair i, j
                        matrix.incrementCount(itemI, itemJ);
                    }
                }
            }
        }
        // (2) create the list of single items
        List<Integer> frequentitems = new ArrayList<Integer>();
        Map<String, BitSetSupport> frequent_items_map = new HashMap<String, BitSetSupport>();

        // for each item
        for (Entry<Integer, BitSetSupport> entry : mapItemTIDS.entrySet()) {
            // get the tidset of that item
            BitSetSupport tidset = entry.getValue();
            // get the support of that item (the cardinality of the tidset)
            int support = tidset.support;
            int item = entry.getKey();
            // if the item is frequent
            if (support >= minsupRelative && maxItemsetSize >= 1) {
                // add the item to the list of frequent single items
                frequentitems.add(item);

               // Set<Integer> set = mapItemCount.get(item);
                frequent_items_map.put(Integer.toString(item),tidset);
                // output the item
                saveSingleItem(item, support, tidset.bitset);
            }
        }

        // Sort the list of items by the total order of increasing support.
        // This total order is suggested in the article by Zaki.
        Collections.sort(frequentitems, new Comparator<Integer>() {
            @Override
            public int compare(Integer arg0, Integer arg1) {
                return mapItemTIDS.get(arg1).support - mapItemTIDS.get(arg0).support; //123
                //return mapItemTIDS.get(arg0).support - mapItemTIDS.get(arg1).support; //321
            }});
        System.out.println();

        List<String> frequent_items = frequentitems.stream().map(String::valueOf).collect(Collectors.toList());

        Map<String,ArrayList<ArrayList>> frequent_items_all = new HashMap<String, ArrayList<ArrayList>>();
        for (int i = 0; i < frequent_items.size(); i++) {
            //store every single layer nitemsets
            Map<String, BitSetSupport> frequent_layer_itemsetMap = new HashMap<String, BitSetSupport>();

            String item_I = frequent_items.get(i);
            //add "A : []"
            frequent_items_all.put(item_I,new ArrayList<>());
            //B itemset
            BitSetSupport item_I_set = frequent_items_map.get(item_I);
            //int supportI = item_I_set.support;
            for (int j = 0; j < i; j++) {
                HashMap<String, BitSetSupport> frequent_array_itemsetMap = new HashMap<>();

                String item_J = frequent_items.get(j); // A
                BitSetSupport item_J_set = frequent_items_map.get(item_J); // A ITEMSET
               // int supportJ = item_J_set.support;
                // Calculate the tidset of itemset "IJ" by performing the intersection of
                // the tidsets of I and the tidset of J. // d(BA)
                BitSetSupport item_IJ_set = performANDFirstTime_rb(item_I_set, item_J_set, 1);

                if (item_IJ_set.support >= this.minsupRelative) {
                    // count+=1;
                    String item_IJ = item_I + " " + item_J;
                    ArrayList<String> cc =new ArrayList<String>();
                    cc.add(item_IJ);
                    frequent_items_all.get(item_I).add(cc); // {"A":[],'B': [['B A']],'C': [['CA'],["cb"]]}
                    // mark_items.add(item_I + " " + item_J); // BA
                    frequent_layer_itemsetMap.put(item_IJ, item_IJ_set);
                    save(item_IJ, item_IJ_set.support);
                    // System.out.println(item_IJ_set.size());
                } else {
                    // {'2': [], '3': [['3 2']], '5': [['5 2'], ['5 3', '5 3 2']], '1': [['1 2'], ['1 3', '1 3 2'], ['1 5', '1 5 2', '1 5 3', '1 5 3 2']], '4': [], '6': [['6 2']]}
                    // discard_set.add(item_J);
                    continue;
                }
                //取出索引数组
                ArrayList<ArrayList> array_two = frequent_items_all.get(item_J);
                for (ArrayList<String> row : array_two) {
                    for (String items_K : row) {
                        String [] item_list = items_K.split(" ");  //B A
                        if (item_list.length == 2) {
                            String items_4 = item_I + " " + item_list[0];
                            String items_5 = item_I + " " + item_list[1];

                            if (frequent_layer_itemsetMap.containsKey(items_4) && frequent_layer_itemsetMap.containsKey(items_5)) {
                                BitSetSupport items_4_set = frequent_layer_itemsetMap.get(items_4);
                                BitSetSupport items_5_set = frequent_layer_itemsetMap.get(items_5);
                                int items_45_support;
                                BitSetSupport items_45_set;
                                if(items_4_set.support == item_I_set.support){
                                    //
                                    // System.out.println("111");
                                    items_45_set = frequent_layer_itemsetMap.get(items_5);  // ABD
                                    items_45_support = items_45_set.support;
                                }else if (items_5_set.support == item_I_set.support){
                                    items_45_set = frequent_layer_itemsetMap.get(items_4);  // ABD
                                    items_45_support = items_45_set.support;
                                } else {

                                    items_45_set = performAND_rb(items_4_set, items_5_set);  // DCBA
                                    items_45_support = items_45_set.support;
                                }

//                                BitSetSupport items_5_set = frequent_layer_itemsetMap.get(items_5);
//                                BitSetSupport items_45_set = performAND_rb(items_4_set, items_5_set);
//                                int items_45_support = items_45_set.support;
                                if (items_45_support >= this.minsupRelative) {
                                    String item_IK = item_I + " " + items_K;
                                    List<String> lastList = getLastElement(frequent_items_all.get(item_I));
                                    lastList.add(item_IK);
//                                    List<String> lastList = frequent_items_all.get(item_I).get(frequent_items_all.get(item_I).size() - 1);
//                                    lastList.add(item_IK);
                                    frequent_array_itemsetMap.put(item_IK,items_45_set);
                                    save(item_IK, items_45_support);
                                } else {
                                    if (items_K.equals(row.get(0))) {
                                        break;
                                    }
                                    else{
                                        continue;
                                    }
                                }
                            } else {
                                if (items_K.equals(row.get(0))) {
                                    break;
                                }
                                else{
                                    continue;
                                }
                            }
                        }
                        else {
                            String items_4 = constructString(item_I, item_list, item_list.length - 1);
                            String items_x = constructString(item_I, item_list, item_list.length - 2);
                            String items_5 = items_x + " " + item_list[item_list.length - 1];
                            if (frequent_array_itemsetMap.containsKey(items_4) && frequent_array_itemsetMap.containsKey(items_5)) {
                                Map<String, BitSetSupport>  flag ;
                                BitSetSupport items_4_set = frequent_array_itemsetMap.get(items_4);
                                BitSetSupport items_5_set = frequent_array_itemsetMap.get(items_5);
                                if(item_list.length == 3){
                                    flag = frequent_layer_itemsetMap;
                                }else {
                                    flag = frequent_array_itemsetMap;
                                }
                                //int items_45_support;
                                BitSetSupport items_45_set;
                                int items_x_sup = flag.get(items_x).support;
                                if(items_4_set.support == items_x_sup){
                                    //System.out.println("222");
                                    items_45_set = frequent_array_itemsetMap.get(items_5);
                                    //items_45_support = items_45_set.support;
                                }else if (items_5_set.support == items_x_sup){
                                    items_45_set = frequent_array_itemsetMap.get(items_4);
                                } else {

                                    items_45_set = performAND_rb(items_4_set,items_5_set);
                                    //items_45_support = items_45_set.support;
                                }
                                if (items_45_set.support >= minsupRelative) {
                                    String item_IK = item_I + " " + items_K;
                                    //getLastElement(frequent_items_all.get(item_I));
                                    List<String> lastList = getLastElement(frequent_items_all.get(item_I));
                                    lastList.add(item_IK);

                                    frequent_array_itemsetMap.put(item_IK, items_45_set);
                                    save(item_IK, items_45_set.support );
                                } else {
                                    if (items_K.equals(row.get(0))) {
                                        break;
                                    }
                                    else{
                                        continue;
                                    }
                                }
                            } else {
                                if (items_K.equals(row.get(0))) {
                                    break;
                                }
                                else{
                                    continue;
                                }
                            }
                        }
                    }
                }
            }
        }



        // we check the memory usage
        MemoryLogger.getInstance().checkMemory();

        // We have finish the search.
        // Therefore, we close the output file writer if the result was saved to a file
        if (writer != null) {
            writer.close();
        }

        // record the end time for statistics
        endTime = System.currentTimeMillis();

        // Return all frequent itemsets found or null if the result was saved to a file.
        return frequentItemsets;
    }
    private static String constructString(String prefix, String[] items, int endIndex) {
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < endIndex; i++) {
            sb.append(" ").append(items[i]);
        }
        return sb.toString();
    }


    public static List<String> getLastElement(ArrayList<ArrayList> twoDimensionalList) {
        if (twoDimensionalList.isEmpty()) {
            return null; // no elements
        }
        return twoDimensionalList.get(twoDimensionalList.size() - 1);
    }

    /**
     * This method scans the database to calculate the support of each single item
     * @param database the transaction database
     * //@param mapItemTIDS  a map to store the tidset corresponding to each item
     * @return the maximum item id appearing in this database
     */
    private int calculateSupportSingleItems (TransactionDatabase database,
    final Map<Integer, BitSetSupport> mapItemTIDS){
        int maxItemId = 0;
        for (int i = 0; i < database.size(); i++) {
            // for each item in that transaction
            for (Integer item : database.getTransactions().get(i)) {
                // get the current tidset of that item
                //Set<Integer> set = mapItemCount.get(item);
                BitSetSupport tids = mapItemTIDS.get(item);
                // if no tidset, then we create one
                if (tids == null) {
                    //set = new HashSet<Integer>();
                    tids = new BitSetSupport();
                    mapItemTIDS.put(item, tids);
                    // if the current item is larger than all items until
                    // now, remember that!
                    if (item > maxItemId) {
                        maxItemId = item;
                    }
                }
                // add the current transaction id (tid) to the tidset of the item
                //set.add(i);
                tids.bitset.add(i);
                // we increase the support of that item
                tids.support++;
            }
        }
        return maxItemId;
    }
    /**
     * This method performs the intersection of two tidsets.
     * @param tidsetI the first tidset
     * @param supportI  the cardinality of the first tidset
     * @param tidsetJ  the second tidset
     * @param supportJ the cardinality of the second tidset
     * @return the resulting tidset.
     */
    Set<Integer> performANDFirstTime(Set<Integer> tidsetI, int supportI,
                                     Set<Integer> tidsetJ, int supportJ) {

        // return the new tidset
        return performAND(tidsetI, supportI, tidsetJ, supportJ);
    }

    BitSetSupport performANDFirstTime_rb(BitSetSupport tidsetI,
                                      BitSetSupport tidsetJ, int supportIJ) {
        // Create the new tidset and perform the logical AND to intersect the tidset
        BitSetSupport bitsetSupportIJ = new BitSetSupport();
        //Calculate the diffset
        bitsetSupportIJ.bitset = (RoaringBitmap)tidsetI.bitset.clone();
        bitsetSupportIJ.bitset.andNot(tidsetJ.bitset);
        // Calculate the support
        bitsetSupportIJ.support = tidsetI.support - bitsetSupportIJ.bitset.getCardinality();
        // return the new tidset
        return bitsetSupportIJ;
    }
    BitSetSupport performAND_rb(BitSetSupport tidsetI,
                             BitSetSupport tidsetJ) {
        // Create the new tidset and perform the logical AND to intersect the tidset
        BitSetSupport bitsetSupportIJ = new BitSetSupport();
        // Calculate the diffset
        bitsetSupportIJ.bitset = (RoaringBitmap)tidsetJ.bitset.clone();
        bitsetSupportIJ.bitset.andNot(tidsetI.bitset);
        // Calculate the support
        bitsetSupportIJ.support = tidsetI.support - bitsetSupportIJ.bitset.getCardinality();
        // return the new diffset
        return bitsetSupportIJ;
    }
    /**
     * This method performs the intersection of two tidsets.
     * @param tidsetI the first tidset
     * @param supportI  the cardinality of the first tidset
     * @param tidsetJ  the second tidset
     * @param supportJ the cardinality of the second tidset
     * @return the resulting tidset.
     */
    Set<Integer> performAND(Set<Integer> tidsetI, int supportI,
                            Set<Integer> tidsetJ, int supportJ) {
        // Create the new tidset that will store the intersection
        //Set<Integer> tidsetIJ = new HashSet<Integer>();
        //Set<Integer> tidsetIJ=tidsetI.

        //count_ca++;
        Set<Integer> tidsetIJ = new HashSet<>(tidsetI);
        tidsetIJ.retainAll(tidsetJ);
        // To reduce the number of comparisons of the two tidsets,
        // if the tidset of I is larger than the tidset of J,
        // we will loop on the tidset of J. Otherwise, we will loop on the tidset of I
//        if(supportI > supportJ) {
//            // for each tid containing j
//            for(Integer tid : tidsetJ) {
//                // if the transaction also contains i, add it to tidset of {i,j}
//                if(tidsetI.contains(tid)) {
//                    // add it to the intersection
//                    tidsetIJ.add(tid);
//                }
//            }
//        }else {
//            // for each tid containing i
//            for(Integer tid : tidsetI) {
//                // if the transaction also contains j, add it to tidset of {i,j}
//                if(tidsetJ.contains(tid)) {
//                    // add it to the intersection
//                    tidsetIJ.add(tid);
//                }
//            }
//        }
        // return the new tidset
        return tidsetIJ;
    }

    /**
     * Save an itemset containing a single item to disk or memory (depending on what the user chose).
     * @param item the item to be saved
     * @param tidset the tidset of this itemset
     * @throws IOException if an error occurrs when writing to disk.
     */
    private void saveSingleItem(int item, int support, RoaringBitmap tidset) throws IOException {
        // increase the itemset count
        itemsetCount++;
        // if the result should be saved to memory
        if(writer == null){
            // add it to the set of frequent itemsets
            Itemset itemset = new Itemset(new int[] {item});
            itemset.setAbsoluteSupport(support);
            frequentItemsets.addItemset(itemset, itemset.size());
        }else{
            // if the result should be saved to a file
            // write it to the output file
            StringBuilder buffer = new StringBuilder();
            buffer.append(item);
            buffer.append(" #SUP: ");
            buffer.append(support);
//			if(showTransactionIdentifiers) {
//				writer.append(" #TID:");
//				for (int tid = tidset.nextSetBit(0); tid != -1; tid = tidset.nextSetBit(tid + 1)) {
//					writer.append(" " + tid);
//				}
//			}
            writer.write(buffer.toString());
            writer.newLine();
        }
    }
    public class BitSetSupport{
        //BitSet bitset = new BitSet();
        RoaringBitmap bitset = new RoaringBitmap();
        int support;
    }
//    /**
//     * Save an itemset to disk or memory (depending on what the user chose).
//     * @param prefix the prefix of the itemset to be saved
//     * @param suffixItem  the last item to be appended to the itemset
//     * @param tidset the tidset of this itemset
//     * @param prefixLength the prefix length
//     * @throws IOException if an error occurrs when writing to disk.
//     */
    private void save(String itemsets, int support) throws IOException {
        // increase the itemset count
        itemsetCount++;
        // if the result should be saved to memory
        if(writer == null){
            // append the prefix with the suffix
//            int[] itemsetArray = new int[prefixLength+1];
//            System.arraycopy(prefix, 0, itemsetArray, 0, prefixLength);
//            itemsetArray[prefixLength] = suffixItem;
            // Create an object "Itemset" and add it to the set of frequent itemsets
//            Itemset itemset = new Itemset(itemsetArray);
//            itemset.setAbsoluteSupport(support);
//            frequentItemsets.addItemset(itemset, itemset.size());
        }else{
            // if the result should be saved to a file
            // write it to the output file
            StringBuilder buffer = new StringBuilder();
//            for(int i=0; i < prefixLength; i++) {
//                int item = prefix[i];
            buffer.append(itemsets);
            buffer.append(" ");
//            }
           // buffer.append(suffixItem);
            // as well as its support
            buffer.append(" #SUP: ");
            buffer.append(support);
//            if(showTransactionIdentifiers) {
//                buffer.append(" #TID:");
//                for (Integer tid: tidset) {
//                    buffer.append(" " + tid);
//                }
//            }
            writer.write(buffer.toString());
            writer.newLine();
        }
    }
    public Map<String,Object> printStats() {
        System.out.println("=============  ECLAT v0.96r18 - STATS =============");
        long temps = endTime - startTimestamp;
        System.out.println(" Transactions count from database : "
                + database.size());
        System.out.println(" Frequent itemsets count : "
                + itemsetCount);
        System.out.println(" Total time ~ " + temps + " ms");
        System.out.println(" Maximum memory usage : "
                + MemoryLogger.getInstance().getMaxMemory() + " mb");
        System.out.println("===================================================");
        Map<String,Object> ma =new HashMap<>();
        ma.put("size",database.size());
        ma.put("ic",itemsetCount);
        ma.put("time",temps);
        ma.put("memory",MemoryLogger.getInstance().getMaxMemory());

        return ma;
    }
}