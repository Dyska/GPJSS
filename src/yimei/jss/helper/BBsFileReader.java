package yimei.jss.helper;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import yimei.jss.FJSSMain;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static yimei.jss.FJSSMain.getDirectoryNames;
import static yimei.jss.helper.GridResultCleaner.writeLine;

/**
 * This file should go iterate through a directory, and find all .bbs.csv files in it.
 *
 * Created by dyska on 5/02/18.
 */
public class BBsFileReader {

    private static final char DEFAULT_SEPARATOR = ' ';
    private static final String EXT = ".bbs.csv";

    private static final String DIR_PATH = (new File("")).getAbsolutePath() + "/grid_results/dynamic/";
    private HashMap<String,Integer> numBBs = new HashMap();
    private HashMap<String, Integer> SeqBBCounts = new HashMap<>();
    private HashMap<String, Integer> RoutBBCounts = new HashMap<>();


    public BBsFileReader() {
        String[] gpVars = new String[] {"SeqGP","CCGP"};
        String[] objectives = new String[] {"max-flowtime","mean-flowtime","mean-weighted-flowtime"};
        double[] utilLevels = new double[] {0.85,0.95};
//        int numRuns = 30;
//        for (int i = 0; i < numRuns; ++i) {
//            for (String gp: gpVars) {
//                for (String obj: objectives) {
//                    for (double utilLevel: utilLevels) {
//                        String run = gp +","+utilLevel+","+obj+","+i;
//                        Integer[] results;
//                        if (gp.equals("SeqGP")) {
//                            results = new Integer[2];
//                        } else {
//                            results = new Integer[4];
//                        }
//                        //filterResults.put(run,results);
//                    }
//                }
//            }
//        }
    }

    public void readInDirectory(String[] directories) {
        List<List> data = new ArrayList<>();
        for (String directory: directories) {
            String workingDirectory = DIR_PATH + directory;
            List<Path> directoryNames = getDirectoryNames(new ArrayList(),Paths.get(workingDirectory),EXT);
            for (Path directoryPath: directoryNames) {
                List<String> fileNames = FJSSMain.getFileNames(new ArrayList(), directoryPath, EXT);
                int numPops = 1;
                if (directory.contains("cc")) {
                    numPops = 2;
                }
                List<String> parameters = new ArrayList<>();
                String gp = numPops == 1 ? "SeqGP" : "CCGP";
                parameters.add(gp);

                String dirName = directoryPath.toString();
                dirName = dirName.substring(workingDirectory.length()+1,dirName.length());
                double utilLevel = Double.parseDouble(dirName.substring(0,4));
                parameters.add(String.valueOf(utilLevel));
                String objective = dirName.substring(5,dirName.indexOf("flowtime")+8);
//                if (!objective.equals("mean-flowtime")) {
//                    continue;
//                }

                parameters.add(objective);
                String fcMethod = dirName.substring(dirName.indexOf(objective)+objective.length()+1);
                parameters.add(fcMethod);

                //int[] numFeaturesSeq = new int[fileNames.size()];
                DescriptiveStatistics numSeqFeatures = new DescriptiveStatistics();
                //int[] numFeaturesRout = new int[fileNames.size()];
                DescriptiveStatistics numRoutFeatures = new DescriptiveStatistics();

                for (int i = 0; i < fileNames.size(); i++) {
                    String fileName = fileNames.get(i);
                    File f = new File(fileName);

                    String jobName = fileName.substring(fileName.lastIndexOf("/")+1,fileName.length());
                    int seed = Integer.parseInt(jobName.substring(4,jobName.indexOf("-")-1));
                    String ruleType = jobName.substring(jobName.indexOf("-")+2,jobName.indexOf(EXT));

                    BufferedReader br = null;

                    int numEntries = 0;
                    try {
                        br = new BufferedReader(new FileReader(fileName));
                        String sCurrentLine;
                        while ((sCurrentLine = br.readLine()) != null) {
                            String BB = sCurrentLine.split(",")[0];
                            double vote = Double.parseDouble(sCurrentLine.split(",")[1]);
                            if (vote > 10.0) {
                                if (ruleType.equals("SEQUENCING")) {
                                    if (SeqBBCounts.containsKey(BB)) {
                                        Integer count = SeqBBCounts.get(BB);
                                        count++;
                                        SeqBBCounts.put(BB,count);
                                    } else {
                                        SeqBBCounts.put(BB,1);
                                    }
                                } else if (ruleType.equals("ROUTING")) {
                                    if (RoutBBCounts.containsKey(BB)) {
                                        Integer count = RoutBBCounts.get(BB);
                                        count++;
                                        RoutBBCounts.put(BB,count);
                                    } else {
                                        RoutBBCounts.put(BB,1);
                                    }
                                }
                            }
                            numEntries++;
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (ruleType.equals("SEQUENCING")) {
                        numSeqFeatures.addValue(numEntries);
                    } else if (ruleType.equals("ROUTING")) {
                        numRoutFeatures.addValue(numEntries);
                    } else {
                        int a = 1; //ruh roh
                    }
                }

                double seqMean = numSeqFeatures.getMean();
                double seqSD = numSeqFeatures.getStandardDeviation();

                double routMean = numRoutFeatures.getMean();
                double routSD = numRoutFeatures.getStandardDeviation();

                parameters.add(String.valueOf(seqMean));
                parameters.add(String.valueOf(seqSD));
                parameters.add(String.valueOf(routMean));
                parameters.add(String.valueOf(routSD));

                data.add(parameters);
//
            }
        }

        String outputFileName = "results.csv";

        File targetPath = new File((new File("")).getAbsolutePath()+"/out/bbs_counts/");
        if (!targetPath.exists()) {
            targetPath.mkdirs();
        }

        File CSVFile = new File(targetPath+"/"+outputFileName);

        try (FileWriter writer = new FileWriter(CSVFile)) {
            List<String> headers = new ArrayList<String>();
            headers.add("GP");
            headers.add("Util Level");
            headers.add("Objective");
            headers.add("FCMethod");
            headers.add("SeqMean");
            headers.add("SeqSD");
            headers.add("RoutMean");
            headers.add("RoutSD");

            writeLine(writer, headers);

            for (List<String> parameters: data) {
                List<String> filteringResultsCSV = new ArrayList<>();
                for (String result: parameters) {
                    filteringResultsCSV.add(result);
                }
                writeLine(writer, filteringResultsCSV);
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
//
//
//
//        List<String> fileNames = FJSSMain.getFileNames(new ArrayList(), Paths.get(DIR_PATH), "");
//        for (String fileName: fileNames) {
//            File f = new File(fileName);
//            String gpVar = "SeqGP";
//            String obj = "";
//            double utilLevel = 0.0;
//            int seed = -1;
//            String run = "";
//            String currentPopulation = "SEQUENCING";
//            int expectedEntries = 2;
//
//            BufferedReader br = null;
//            Integer[] results = null;
//
//            try {
//                br = new BufferedReader(new FileReader(fileName));
//                String sCurrentLine;
//                while ((sCurrentLine = br.readLine()) != null) {
//                    if (sCurrentLine.startsWith("Calling -jar")) {
//                        String[] components = sCurrentLine.split(String.valueOf(DEFAULT_SEPARATOR));
//
//                        if (components[4].contains("coevolutiongp")) {
//                            gpVar = "CCGP";
//                            expectedEntries = 4;
//                        }
//                        results = new Integer[expectedEntries];
//
//
//                        if (components[6].endsWith("0.85")) {
//                            utilLevel = 0.85;
//                        } else if (components[6].endsWith("0.95")) {
//                            utilLevel = 0.95;
//                        } else {
//                            int a = 1;
//                        }
//
//                        if (components[8].endsWith("max-flowtime")) {
//                            obj = "max-flowtime";
//                        } else if (components[8].endsWith("mean-flowtime")) {
//                            obj = "mean-flowtime";
//                        } else if (components[8].endsWith("mean-weighted-flowtime")) {
//                            obj = "mean-weighted-flowtime";
//                        } else {
//                            int a = 1;
//                        }
//
//                        seed = Integer.parseInt(components[14].split("=")[1]);
//                        run = gpVar +","+utilLevel+","+obj+","+seed;
//                        if (!numBBs.containsKey(run)) {
//                            if (seed < 30) {
//                                System.out.println(run);
//                            }
//                        }
//                    }
//
//                    if (sCurrentLine.startsWith("Feature construction analysis being" +
//                            " performed for ROUTING population")) {
//                        currentPopulation = "ROUTING";
//                    }
//
//                    if (sCurrentLine.endsWith("subtrees found.")) {
//                        int index = currentPopulation == "SEQUENCING" ? 0 : 2;
//                        String[] components = sCurrentLine.split(String.valueOf(DEFAULT_SEPARATOR));
//                        int numBeforeFiltering = Integer.parseInt(components[0]);
//                        results[index] = numBeforeFiltering;
//                        if (results[index] < 10) {
//                            int a = 1;
//                        }
//
//                        sCurrentLine = br.readLine();
//                        components = sCurrentLine.split(String.valueOf(DEFAULT_SEPARATOR));
//
//                        int numSubtreesRemoved = Integer.parseInt(components[1]);
//                        sCurrentLine = br.readLine();
//                        components = sCurrentLine.split(String.valueOf(DEFAULT_SEPARATOR));
//                        numSubtreesRemoved += Integer.parseInt(components[1]);
//                        index++;
//                        int numAfterFiltering = numBeforeFiltering - numSubtreesRemoved;
//                        if (numAfterFiltering < 0) {
//                            int a = 1;
//                        }
//                        results[index] = numAfterFiltering;
//                    }
//                }
//                //now we have our results array
//                if (numBBs.containsKey(run)) {
//
//                    Integer[] output = numBBs.get(run);
//                    if (!Arrays.equals(output, new Integer[expectedEntries])) {
//                        //already an entry here, uh oh
//                        if (Arrays.equals(output, results)) {
//                            //duplicate results for some reason, no big deal
//                        } else if (!Arrays.equals(results, new Integer[expectedEntries])) {
//                            if (results[0] > output[0]) {
//                                //may have done a test run with lower params, bigger values should overwrite
//                                numBBs.put(run,results);
//                            } else {
//                                int a = 1;
//                            }
//                        }
//                    } else {
//                        numBBs.put(run,results);
//                    }
//                }
//
//            } catch (FileNotFoundException e) {
//                e.printStackTrace();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//
//        List<String> results = new ArrayList<>();
//        int numMissing = 0;
//        //now let's check keyset for missing entries
//        for (String key: filterResults.keySet()) {
//            int expectedEntries = key.startsWith("CCGP") ? 4 : 2;
//            Integer[] val = filterResults.get(key);
//            if (Arrays.equals(val, new Integer[expectedEntries])) {
//                //no entry has been added
//                numMissing++;
//                System.out.println(key);
//            } else {
//                String arrayString = "";
//                for (int i = 0; i < 2; ++i) {
//                    arrayString += ","+val[i];
//                }
//                if (expectedEntries == 4) {
//                    for (int i = 2; i < 4; ++i) {
//                        arrayString += "," +val[i];
//                    }
//                } else {
//                    for (int i = 2; i < 4; ++i) {
//                        arrayString += "," + 0;
//                    }
//                }
//                //System.out.println(key+" - "+arrayString);
//                results.add(key+arrayString);
//            }
//        }
//
//        Collections.sort(results);
//
//        if (numMissing > 0) {
//            int a = 1;
//        }
//
//        String outputFileName ="result/bb_filtering_results.csv";
//
//        String CSVFile = GRID_PATH + "/"+ outputFileName;
//
//        try (FileWriter writer = new FileWriter(CSVFile)) {
//            //add header first
//            List<String> headers = new ArrayList<String>();
//            headers.add("GP");
//            headers.add("Util Level");
//            headers.add("Objective");
//            headers.add("Seed");
//            for (int i = 0; i < 2; ++i) {
//                headers.add("Seq"+i);
//            }
//            for (int i = 0; i < 2; ++i) {
//                headers.add("Routing"+i);
//            }
//            writeLine(writer, headers);
//
//            for (String result: results) {
//                List<String> filteringResultsCSV = new ArrayList<>();
//                filteringResultsCSV.add(result);
//                writeLine(writer, filteringResultsCSV);
//            }
//            writer.flush();
//            writer.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    /**
     *
     * @param args
     */
    public static void main(String[] args) {
        BBsFileReader b = new BBsFileReader();
        String[] directories = new String[] {"test/simple-fc","test/ccgp-fc"};
        b.readInDirectory(directories);
    }
}
