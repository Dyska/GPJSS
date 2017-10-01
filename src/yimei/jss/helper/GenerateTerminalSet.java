package yimei.jss.helper;

import ec.gp.GPNode;
import ec.rule.Rule;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.rule.RuleType;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static yimei.jss.FJSSMain.getFileNames;
import static yimei.jss.helper.GridResultCleaner.writeLine;

/**
 * Created by dyska on 30/09/17.
 *
 */
public class GenerateTerminalSet {


    public static void main(String args[]) {
        //Should specify a directory path which will contain
        //csv files containing the chosen terminals - one for each job
        //then we should decide which ones to keep, and output these
        //features into a csv file in the /terminal/ directory

        // grid_results/static/raw/simple_feature_selection/
        // static/raw/simple_feature_selection/
        String path = "";
        String outputDirectory = "";
        if (args.length > 0) {
            //allow more specific folder or file paths to be used
            path = args[0];
            outputDirectory = args[1];
            path = (new File("")).getAbsolutePath() + "/grid_results/" + path;
        } else {
            System.out.println("Please specify a directory path.");
            return;
        }

        List<Path> directoryNames = getDirectoryNames(Paths.get(path));
        for (Path d : directoryNames) {
            System.out.println("Terminal counts for "+d.toString());
            List<String> terminalCSVs = getFileNames(new ArrayList<>(), d, ".terminals.csv");
            chooseTerminals(outputDirectory, d, RuleType.SEQUENCING, terminalCSVs);
            //if no routing files, will not do anything, so can safely call this also
            chooseTerminals(outputDirectory, d, RuleType.ROUTING, terminalCSVs);
        }
    }

    public static List<Path> getDirectoryNames(Path dir) {
        List directoryNames = new ArrayList();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                if (path.toFile().isDirectory()) {
                    directoryNames.add(path);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return directoryNames;
    }

    public static List<String> readFromFile(String fileName) {
        File csvFile = new File(fileName);
        LinkedList<String> terminals = new LinkedList<String>();

        BufferedReader br = null;
        String line = "";

        try {
            br = new BufferedReader(new FileReader(csvFile));
            while ((line = br.readLine()) != null) {
                JobShopAttribute a = JobShopAttribute.get(line);
                AttributeGPNode attribute = new AttributeGPNode(a);
                terminals.add(attribute.toString());
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return terminals;
    }

    public static void chooseTerminals(String outputDirectory, Path d,
                                       RuleType ruleType, List<String> terminalCSVs) {
        HashMap<String, Integer> terminalCounts = new HashMap<>();
        if (terminalCSVs.size() != 30 && terminalCSVs.size() != 60) {
            System.out.println("Only "+terminalCSVs.size()+" terminal files for "+d.toString());
        }
        for (String fileName: terminalCSVs) {
            if (fileName.contains(ruleType.name())) {
                List<String> terminals = readFromFile(fileName);
                for (String terminal: terminals) {
                    Integer count = terminalCounts.get(terminal);
                    if (count == null) {
                        count = 0;
                    }
                    terminalCounts.put(terminal,count+1);
                }
            }
        }
        //System.out.println(ruleType.name() + " terminals.");
        List<String> chosenTerminals = new ArrayList<>();
        if (!terminalCounts.keySet().isEmpty()) {
            //allows us to be lazy and call this for routing rules if there are none
            //without creating output files
            for (String terminal: terminalCounts.keySet()) {
                //System.out.println(terminal +": "+terminalCounts.get(terminal));
                if (terminalCounts.get(terminal) >= 15) {
                    chosenTerminals.add(terminal);
                }
            }
            File outputFile = createFileName(outputDirectory, d, ruleType);
            //now output this list of chosen terminals to a file
            outputToFile(outputFile, chosenTerminals);
        }
    }

    public static File createFileName(String outputDirectory, Path d, RuleType ruleType) {
        outputDirectory = (new File("")).getAbsolutePath()+"/terminals/"+outputDirectory+"/";
        String path = d.toString();
        String fileName = outputDirectory + path.split("/")[path.split("/").length-1];
        fileName += "-"+ruleType.name()+".csv";
        return new File(fileName);
    }

    public static void outputToFile(File csvFile, List<String> chosenTerminals) {
        try (FileWriter writer = new FileWriter(csvFile)) {
            for (String terminal: chosenTerminals) {
                List<String> terminalLine = new ArrayList<String>();
                terminalLine.add(terminal);
                writeLine(writer, terminalLine);
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

