package ca.pfv.spmf.test.my_test;

import ca.pfv.spmf.input.transaction_database_list_integers.TransactionDatabase;
import ca.pfv.spmf.test.MainTestEclat_saveToFile;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Map;

public class MainMy_Eclat_saveToFile {
    public static void main(String [] arg) throws IOException {

        // the file paths
        //String input = fileToPath("./datasets/T40I10D100K.dat.txt");  // the database
        //String input = fileToPath("./datasets/mushroom.dat.txt");
        //String input = fileToPath("./datasets/accidents.dat.txt");
        //String input = fileToPath("./datasets/chess.dat.txt");
        String input = fileToPath("./datasets/connect.dat.txt");
        //String input = fileToPath("./datasets/retail.dat.txt");
        //String input = fileToPath("./datasets/pumsb_star.dat.txt");
        //String input = fileToPath("./datasets/T10I4D100K.dat.txt");
        //String input = fileToPath("./datasets/kosarak.dat.txt");
        String output = "./result/output.txt";  // the path for saving the frequent itemsets found
        // minimum support
        //double minsup = 0.75; // means a minsup of 2 transaction (we used a relative support)

        // Loading the transaction database
//        TransactionDatabase database = new TransactionDatabase();
//        try {
//            database.loadFile(input);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//		context.printContext();

        // Applying the ECLAT algorithm


        // Uncomment the following line to set the maximum pattern length (number of items per itemset)
//		algo.setMaximumPatternLength(3);

////		// Set this variable to true to show the transaction identifiers where patterns appear in the output file
//		algo.setShowTransactionIdentifiers(true);

        //algo.runAlgorithm(output, database, minsup, false);
        // if you change use "true" in the line above, ECLAT will use
        // a triangular matrix  for counting support of itemsets of size 2.
        // For some datasets it should make the algorithm faster.

        //algo.printStats();
        //System.out.println(algo.count_ca);

        //FileWriter path = "./datasets/HFI-Eclat.csv";
        My_Eclat algo = new My_Eclat();

        double[] sup_values = {0.975};

        try (BufferedWriter results = new BufferedWriter(new FileWriter("./myEclat.txt"))) {
            results.write("min_sup,total_time,peak_memory,db_size,fis_count");
            results.newLine();

            // Assuming input is a string variable of the database file path
            //String input = "path_to_your_database_file"; // Replace with your database file path
            TransactionDatabase database = new TransactionDatabase();
            try {
                database.loadFile(input);
            } catch (IOException e) {
                e.printStackTrace();
            }

            for (double minsup : sup_values) {

                //String output = "../output/DP-dEclat-PRO-" + sup + ".txt";
                algo.runAlgorithm(output, database, minsup, false);
                Map<String,Object> res=algo.printStats();
                long total_time = (Long) res.get("time");
                double memory = (double)res.get("memory");
                int db_size = (int)res.get("size");
                int fi_count = (int)res.get("ic");
                System.out.println(algo.count_ca);
                results.write(String.format("\n%f,%d,%f,%d,%d", minsup, total_time, memory, db_size, fi_count));

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestEclat_saveToFile.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
    }
}
