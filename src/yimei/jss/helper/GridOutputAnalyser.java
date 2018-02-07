package yimei.jss.helper;

import yimei.jss.FJSSMain;
import java.io.*;
import java.nio.file.Paths;
import java.util.*;

import static yimei.jss.helper.GridResultCleaner.writeLine;

/**
 * Created by yskadani on 5/02/18.
 *
 * Not very nice code, but does the trick.
 */
public class GridOutputAnalyser {
    private static final char DEFAULT_SEPARATOR = ' ';
    private static final String GRID_PATH = (new File("")).getAbsolutePath() + "/grid_results/outputs";
    //want a record for
    private HashMap<String,Integer[]> filterResults = new HashMap();

    //eg SEQGP-0.85-max-flowtime-2-Clustering-2-Clustering-1 is key
    //then double[3] = {ORIGINAL, FILTER1, FILER2} is value
    public GridOutputAnalyser() {
        String[] gpVars = new String[] {"SeqGP","CCGP"};
        String[] objectives = new String[] {"max-flowtime","mean-flowtime","mean-weighted-flowtime"};
        double[] utilLevels = new double[] {0.85,0.95};
        int numRuns = 30;
        for (int i = 0; i < numRuns; ++i) {
            for (String gp: gpVars) {
                for (String obj: objectives) {
                    for (double utilLevel: utilLevels) {
                        String run = gp +","+utilLevel+","+obj+","+i;
                        Integer[] results;
                        if (gp.equals("SeqGP")) {
                            results = new Integer[2];
                        } else {
                            results = new Integer[4];
                        }
                        filterResults.put(run,results);
                    }
                }
            }
        }
    }

    public void readInDirectory() {
        List<String> fileNames = FJSSMain.getFileNames(new ArrayList(), Paths.get(GRID_PATH), "");
        for (String fileName: fileNames) {
            File f = new File(fileName);
            String gpVar = "SeqGP";
            String obj = "";
            double utilLevel = 0.0;
            int seed = -1;
            String run = "";
            String currentPopulation = "SEQUENCING";
            int expectedEntries = 2;

            BufferedReader br = null;
            Integer[] results = null;

            try {
                br = new BufferedReader(new FileReader(fileName));
                String sCurrentLine;
                while ((sCurrentLine = br.readLine()) != null) {
                    if (sCurrentLine.startsWith("Calling -jar")) {
                        String[] components = sCurrentLine.split(String.valueOf(DEFAULT_SEPARATOR));

                        if (components[4].contains("coevolutiongp")) {
                            gpVar = "CCGP";
                            expectedEntries = 4;
                        }
                        results = new Integer[expectedEntries];


                        if (components[6].endsWith("0.85")) {
                            utilLevel = 0.85;
                        } else if (components[6].endsWith("0.95")) {
                            utilLevel = 0.95;
                        } else {
                            int a = 1;
                        }

                        if (components[8].endsWith("max-flowtime")) {
                            obj = "max-flowtime";
                        } else if (components[8].endsWith("mean-flowtime")) {
                            obj = "mean-flowtime";
                        } else if (components[8].endsWith("mean-weighted-flowtime")) {
                            obj = "mean-weighted-flowtime";
                        } else {
                            int a = 1;
                        }

                        seed = Integer.parseInt(components[14].split("=")[1]);
                        run = gpVar +","+utilLevel+","+obj+","+seed;
                        if (!filterResults.containsKey(run)) {
                            if (seed < 30) {
                                System.out.println(run);
                            }
                        }
                    }

                    if (sCurrentLine.startsWith("Feature construction analysis being" +
                            " performed for ROUTING population")) {
                        currentPopulation = "ROUTING";
                    }

                    if (sCurrentLine.endsWith("subtrees found.")) {
                        int index = currentPopulation == "SEQUENCING" ? 0 : 2;
                        String[] components = sCurrentLine.split(String.valueOf(DEFAULT_SEPARATOR));
                        int numBeforeFiltering = Integer.parseInt(components[0]);
                        results[index] = numBeforeFiltering;
                        if (results[index] < 10) {
                            int a = 1;
                        }

                        sCurrentLine = br.readLine();
                        components = sCurrentLine.split(String.valueOf(DEFAULT_SEPARATOR));

                        int numSubtreesRemoved = Integer.parseInt(components[1]);
                        sCurrentLine = br.readLine();
                        components = sCurrentLine.split(String.valueOf(DEFAULT_SEPARATOR));
                        numSubtreesRemoved += Integer.parseInt(components[1]);
                        index++;
                        int numAfterFiltering = numBeforeFiltering - numSubtreesRemoved;
                        if (numAfterFiltering < 0) {
                            int a = 1;
                        }
                        results[index] = numAfterFiltering;
                    }
                }
                //now we have our results array
                if (filterResults.containsKey(run)) {

                    Integer[] output = filterResults.get(run);
                    if (!Arrays.equals(output, new Integer[expectedEntries])) {
                        //already an entry here, uh oh
                        if (Arrays.equals(output, results)) {
                            //duplicate results for some reason, no big deal
                        } else if (!Arrays.equals(results, new Integer[expectedEntries])) {
                            if (results[0] > output[0]) {
                                //may have done a test run with lower params, bigger values should overwrite
                                filterResults.put(run,results);
                            } else {
                                int a = 1;
                            }
                        }
                    } else {
                        filterResults.put(run,results);
                    }
                }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        List<String> results = new ArrayList<>();
        int numMissing = 0;
        //now let's check keyset for missing entries
        for (String key: filterResults.keySet()) {
            int expectedEntries = key.startsWith("CCGP") ? 4 : 2;
            Integer[] val = filterResults.get(key);
            if (Arrays.equals(val, new Integer[expectedEntries])) {
                //no entry has been added
                numMissing++;
                System.out.println(key);
            } else {
                String arrayString = "";
                for (int i = 0; i < 2; ++i) {
                    arrayString += ","+val[i];
                }
                if (expectedEntries == 4) {
                    for (int i = 2; i < 4; ++i) {
                        arrayString += "," +val[i];
                    }
                } else {
                    for (int i = 2; i < 4; ++i) {
                        arrayString += "," + 0;
                    }
                }
                //System.out.println(key+" - "+arrayString);
                results.add(key+arrayString);
            }
        }

        Collections.sort(results);

        if (numMissing > 0) {
            int a = 1;
        }

        String outputFileName ="result/bb_filtering_results.csv";

        String CSVFile = GRID_PATH + "/"+ outputFileName;

        try (FileWriter writer = new FileWriter(CSVFile)) {
            //add header first
            List<String> headers = new ArrayList<String>();
            headers.add("GP");
            headers.add("Util Level");
            headers.add("Objective");
            headers.add("Seed");
            for (int i = 0; i < 2; ++i) {
                headers.add("Seq"+i);
            }
            for (int i = 0; i < 2; ++i) {
                headers.add("Routing"+i);
            }
            writeLine(writer, headers);

            for (String result: results) {
                List<String> filteringResultsCSV = new ArrayList<>();
                filteringResultsCSV.add(result);
                writeLine(writer, filteringResultsCSV);
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param args
     */
    public static void main(String[] args) {
        GridOutputAnalyser g = new GridOutputAnalyser();
        g.readInDirectory();
    }
}
