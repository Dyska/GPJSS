package yimei.jss;

import ec.Evolve;
import ec.Fitness;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.gp.GPNodeParent;
import ec.gp.GPTree;
import ec.util.ParameterDatabase;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import yimei.jss.algorithm.featureconstruction.FCGPRuleEvolutionState;
import yimei.jss.bbselection.*;
import yimei.jss.contributionselection.ContributionClusteringStrategy;
import yimei.jss.contributionselection.ContributionSelectionStrategy;
import yimei.jss.contributionselection.ContributionStaticThresholdStrategy;
import yimei.jss.feature.FeatureUtil;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.ConstantTerminal;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.jobshop.Objective;
import yimei.jss.niching.ClearingMultiObjectiveFitness;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.util.lisp.LispSimplifier;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static ec.Evolve.loadParameterDatabase;
import static yimei.jss.FJSSMain.getDirectoryNames;
import static yimei.jss.FJSSMain.getFileNames;
import static yimei.jss.feature.FeatureUtil.buildingBlocks;

/**
 * Created by dyska on 14/12/17.
 *
 * This class should allow a set of GP rules to be read in from
 * a file, and fed into a feature construction method for testing.
 */
public class FCMain {

    static String diverseIndisDir = "/data/filter_tests/";
    //static String contributionsDir = "/out/subtree_contributions/";

    /**
     * This method should read in the file of diverse individuals corresponding to
     * the provided utilLevel, objective, seed and ruleType. With this set of diverse individuals,
     * different contribution selection and building block selection strategies to be supplied, to
     * find the differing results.
     * @param utilLevel utilLevel used when producing selected individuals,
     * @param objective optimised objective when producing selected individuals,
     * @param seed seed used when producing selected individuals,
     * @param ruleType type of selected individuals,
     * @param outputDir directory to output results.
     */
    public static void runFeatureConstruction(double utilLevel, String objective,
                                              int seed, RuleType ruleType,
                                              String outputDir) {
        String directory = (new File("")).getAbsolutePath();
        String localDir = diverseIndisDir+utilLevel+"-"+objective+"/";
        String outDir = outputDir+utilLevel+"-"+objective+"/";
        String fileName = "job."+seed+" - "+ruleType+".csv";

        File indiFile = new File(directory+localDir+fileName);
        File outFile = new File(directory+outDir+fileName);

        //region <Read in params from file.>
        List<GPIndividual> selIndis = new ArrayList<>();
        List<GPNode> BBs = new ArrayList<>();
        double[][] contributions = null;
        DescriptiveStatistics votingWeightStat = new DescriptiveStatistics();

        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(indiFile));
            String line = br.readLine(); //first row should say SelIndis
            while (!(line = br.readLine()).equals("BBs")) {
                String[] vals = line.split(",");
                selIndis.add(initGPIndi(vals,ruleType));
            }
            //reading in building blocks now
            while (!(line = br.readLine()).equals("Contributions")) {
                //selIndis.add(initGPIndi(vals,ruleType));
                GPTree tree = GPRule.readFromLispExpression(ruleType,
                        line).getGPTree();
                BBs.add(tree.child);
            }
            contributions = new double[selIndis.size()][BBs.size()];
            int rowNum = 0;
            while (!(line = br.readLine()).equals("VotingWeightStat")) {
                double[] row = new double[BBs.size()];
                String[] vals = line.split(",");
                for (int i = 0; i < vals.length; ++i) {
                    row[i] = Double.parseDouble(vals[i]);
                }
                contributions[rowNum] = row;
                rowNum++;
            }
            while ((line = br.readLine()) != null) {
                String[] vals = line.split(",");
                for (int i = 0; i < vals.length; ++i) {
                    votingWeightStat.addValue(Double.parseDouble(vals[i]));
                }
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
        //endregion

        //FCGPRuleEvolutionState state = initState(utilLevel, objective, seed);

        //region <Define strategies>
        ContributionSelectionStrategy baselineContributionSelectionStrat =
                new ContributionStaticThresholdStrategy(0.001);
        BBSelectionStrategy baselineBBSelectionStrat =
                new StaticProportionTotalVotingWeight(0.5);

        List<ContributionSelectionStrategy> contributionStrategies = new ArrayList<>();
        contributionStrategies.add(new ContributionClusteringStrategy(2));
        contributionStrategies.add(new ContributionClusteringStrategy(3));

        List<BBSelectionStrategy> bbStrategies = new ArrayList<>();
        bbStrategies.add(new TopKStrategy(1));
        bbStrategies.add(new BBClusteringStrategy(2));
        bbStrategies.add(new BBClusteringStrategy(3));
        bbStrategies.add(new StaticProportionTotalVotingWeight(0.25));
        //endregion

        List<DescriptiveStatistics> BBVotingWeightStats = new ArrayList<>();
        for (int i = 0; i < BBs.size(); i++) {
            BBVotingWeightStats.add(new DescriptiveStatistics());
        }

        List<GPNode> filteredBBs = FeatureUtil.prefilterBBs(BBs);

        List<List<String>> outputMatrix = initMatrix(BBs,filteredBBs);

        //this is the baseline method
        GPNode[] selBBs = featureConstructionMock(contributions,selIndis,BBs,BBVotingWeightStats,votingWeightStat,
                baselineContributionSelectionStrat,baselineBBSelectionStrat);
        addToMatrix(outputMatrix,baselineContributionSelectionStrat,baselineBBSelectionStrat,selBBs);

        for (ContributionSelectionStrategy c: contributionStrategies) {
            for (BBSelectionStrategy b: bbStrategies) {
                selBBs = featureConstructionMock(contributions,selIndis,BBs,BBVotingWeightStats,votingWeightStat,c,b);
                addToMatrix(outputMatrix,c,b,selBBs);
            }
        }
        writeFilteringMatrix(outFile,outputMatrix);
    }

    /**
     * This method is the second half of the FeatureUtil.FeatureConstruction method, with the writing to files
     * removed.
     * @param contributions
     * @param selIndis
     * @param BBs
     * @param BBVotingWeightStats
     * @param votingWeightStat
     * @param contributionStrategy
     * @param bbStrategy
     * @return selectedBuildingblocks.
     */
    private static GPNode[] featureConstructionMock(double[][] contributions, List<GPIndividual> selIndis,
                                                    List<GPNode> BBs, List<DescriptiveStatistics> BBVotingWeightStats,
                                                    DescriptiveStatistics votingWeightStat,
                                                    ContributionSelectionStrategy contributionStrategy,
                                                    BBSelectionStrategy bbStrategy) {
        //select which contributions to count
        contributionStrategy.selectContributions(contributions,selIndis,BBs,BBVotingWeightStats,votingWeightStat);

        //Now select which building blocks to record!

        List<GPNode> selBBs = new LinkedList<>();
        List<Double> selBBsVotingWeights = new LinkedList<>();

        //ugly, sorry
        //total voting weight will not be known if we are running the whole process from scratch (ie generating
        //diverse set of individuals, not reading them in from a file)
        if (bbStrategy instanceof StaticProportionTotalVotingWeight) {
            ((StaticProportionTotalVotingWeight) bbStrategy).setTotalVotingWeight(votingWeightStat.getSum());
        }

        //update these empty lists with results
        bbStrategy.selectBuildingBlocks(BBs,selBBs,BBVotingWeightStats,selBBsVotingWeights);

        return selBBs.toArray(new GPNode[0]);
    }

//    private static double getTotalVotingWeight(double utilLevel, String objective,
//                                        int seed, RuleType ruleType) {
//        String directory = (new File("")).getAbsolutePath();
//        directory += diverseIndisDir+utilLevel+"-"+objective+"/";
//        directory += "job."+seed+" - "+ruleType+".csv";
//        File indiFile = new File(directory);
//        List<GPIndividual> selIndis = readInSelectedIndividuals(indiFile, ruleType);
//
//        FCGPRuleEvolutionState state = initState(utilLevel, objective, seed);
//
//        for (GPIndividual indi: selIndis) {
//            FeatureUtil.initFitness(state, indi, ruleType);
//        }
//
//        //should reorder individuals - highest fitness first
//        Collections.sort(selIndis, (o1, o2) -> {
//            if (o1.fitness.fitness() < o2.fitness.fitness()) {
//                return 1;
//            } else if (o1.fitness.fitness() > o2.fitness.fitness()) {
//                return -1;
//            } else {
//                return 0;
//            }
//        });
//
//        double fitUB = 1/(1+selIndis.get(0).fitness.fitness());
//        double fitLB = 1 - fitUB;
//
//        DescriptiveStatistics stat = FeatureUtil.initVotingWeightStat(selIndis, fitUB, fitLB);
//
//        return stat.getSum();
//    }

    public static FCGPRuleEvolutionState initState(double utilLevel, String objective, int seed) {
        List<String> args = new ArrayList();

        args.add("-file");
        args.add((new File("")).getAbsolutePath()+"/src/yimei/jss/algorithm/featureconstruction/fcgp-simplegp-dynamic.params");
        args.add("-p");
        args.add("eval.problem.eval-model.sim-models.0.util-level="+utilLevel);
        args.add("-p");
        args.add("eval.problem.eval-model.objectives.0="+objective);
        args.add("-p");
        args.add("seed.0=" + seed);

        ParameterDatabase parameters = loadParameterDatabase(args.toArray(new String[0]));

        FCGPRuleEvolutionState state = (FCGPRuleEvolutionState) Evolve.initialize(parameters, 0);
        state.setup(state,null);

        return state;
    }

    public static double[] readInFitnessBounds(File indiFile) {
        double[] fitnessBounds = new double[2];

        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(indiFile));
            String line = br.readLine(); //skip first row, which should be fitUB and fitLB
            String[] bounds = line.split(",");
            fitnessBounds[0] = Double.parseDouble(bounds[0]);
            fitnessBounds[1] = Double.parseDouble(bounds[1]);
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
        return fitnessBounds;
    }

    public static List<GPIndividual> readInSelectedIndividuals(File indiFile, RuleType ruleType) {
        List<GPIndividual> individuals = new ArrayList<>();

        BufferedReader br = null;
        String line = "";

        try {
            br = new BufferedReader(new FileReader(indiFile));
            line = br.readLine(); //skip first row, which should be fitUB and fitLB
            while ((line = br.readLine()) != null) {
                String[] vals = line.split(",");
                individuals.add(initGPIndi(vals,ruleType));
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
        return individuals;
    }

    public static GPIndividual initGPIndi(String[] vals, RuleType ruleType) {
        GPIndividual g = new GPIndividual();
        g.trees = new GPTree[] {GPRule.readFromLispExpression(ruleType,
                vals[0]).getGPTree()};
        ClearingMultiObjectiveFitness fitness = new ClearingMultiObjectiveFitness();
        fitness.objectives = new double[] {10.0}; //shouldn't be using this fitness
        fitness.maxObjective = new double[] {1.0};
        fitness.minObjective = new double[] {0.0};
        fitness.maximize = new boolean[] {false};
        g.fitness = fitness;
        g.evaluated = true;
        //hopefully species is not important...
        return g;
    }

    /**
     * This method should update the .fcinfo.csv files by adding a column
     * containing information about which cluster a contribution was added to.
     * This method sho
     */
    public static void addClusterInfomation(double utilLevel, String objective,
                                            int seed, RuleType ruleType,
                                            String outputDir, int k) {
        String directory = (new File("")).getAbsolutePath();
        directory += outputDir+utilLevel+"-"+objective+"/";
        directory += "job."+seed+" - "+ruleType+".fcinfo.csv";
        File file = new File(directory);

        FeatureUtil.addClusterColumn(file, k);
    }

    public static void addBBSelectionFile(double utilLevel, String objective,
                                          int seed, RuleType ruleType,
                                          String outputDir) {
        String directory = (new File("")).getAbsolutePath();
        directory += outputDir+utilLevel+"-"+objective+"/";
        directory += "job."+seed+" - "+ruleType;

        File fcFile = new File(directory+".fcinfo.csv");
        File bbsFile = new File(directory+".bbs.csv");
        File fcSelectionFile = new File(directory+".fcsel.csv");

        //have the fcinfo file, now need to use this to read in state parameters
        List<String> BBs = new ArrayList<>();
        readBBs(fcFile, BBs);

        List<Double> BBVotingWeightStats = new ArrayList<>();
        for (int i = 0; i < BBs.size(); ++i) {
            BBVotingWeightStats.add(0.0);
        }
        readVotingScores(bbsFile, BBVotingWeightStats, BBs);

        //double totalVotingWeight = getTotalVotingWeight(utilLevel, objective, seed, ruleType);

        //create our list of building block strategies
        List<BBSelectionStrategy> bbStrategies = new ArrayList<>();
        bbStrategies.add(new TopKStrategy(1));
        bbStrategies.add(new BBClusteringStrategy(2));
        bbStrategies.add(new BBClusteringStrategy(3));
        bbStrategies.add( new StaticProportionTotalVotingWeight(0.25));
        bbStrategies.add(new StaticProportionTotalVotingWeight(0.50));
        bbStrategies.add(new StaticProportionTotalVotingWeight(0.75));
        bbStrategies.add(new BBStaticThresholdStrategy(5.0));
        bbStrategies.add(new BBStaticThresholdStrategy(10.0));
        bbStrategies.add(new BBStaticThresholdStrategy(15.0));

        //going to have BBs.size()+1 rows, and strategies.size()+1 columns
        List<String> outputRows = new ArrayList<>();

        String headerRow = "BB,VotingWeight";
        for (BBSelectionStrategy strategy: bbStrategies) {
            headerRow += "," + strategy.getName();
        }
        outputRows.add(headerRow);

        for (int i = 0; i < BBs.size(); ++i) {
            outputRows.add(BBs.get(i)+","+BBVotingWeightStats.get(i));
        }

        boolean doPrint = false;
        for (BBSelectionStrategy strategy: bbStrategies) {
            int[] selectedBBs = strategy.selectBuildingBlocks(BBs,BBVotingWeightStats,doPrint);
            //go down the output rows and append binary value for that BB
            for (int i = 0; i < selectedBBs.length; ++i) {
                String row = outputRows.get(i+1); //accounting for header row
                row += ","+selectedBBs[i];
                outputRows.set(i+1,row);
            }
        }

        //write these rows to file
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fcSelectionFile));
            for (String row: outputRows) {
                writer.write(row);
                writer.newLine();
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void readBBs(File fcFile, List<String> BBs) {
        BufferedReader br = null;
        String line = "";
        try {
            br = new BufferedReader(new FileReader(fcFile));
            br.readLine(); //skip header
            int count = 0;
            int numRuns = 30;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (count%numRuns == 0) {
                    BBs.add(values[0]); //building block is duplicated 30 times in a row in fcinfo file
                }
                count++;
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
    }

    private static void readVotingScores(File bbsFile,
                                         List<Double> BBVotingWeightStats,
                                         List<String> BBs) {
        BufferedReader br = null;
        String line = "";
        try {
            br = new BufferedReader(new FileReader(bbsFile));
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                String bb = values[0];
                Double votingScore = Double.valueOf(values[1]);
                //not many items in BBs, can just iterate through
                int index = BBs.indexOf(bb);
                if (index != -1) {
                    BBVotingWeightStats.set(index,votingScore);
                } else {
                    System.out.println("Expected to find building block "+bb+" in fcinfo file...");
                }
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
    }

    private static void removeTotalVotingWeight(double utilLevel, String objective,
                                                int seed, RuleType ruleType, String outputDir) {
        String directory = (new File("")).getAbsolutePath();
        directory += outputDir+utilLevel+"-"+objective+"/";
        directory += "job."+seed+" - "+ruleType;

        File bbsFile = new File(directory+".bbs.csv");

        final BufferedReader br;

        try {
            br = new BufferedReader(new FileReader(bbsFile));
            String sCurrentLine;
            List<String> newLines = new ArrayList<>();
            for (String line : Files.readAllLines(Paths.get(bbsFile.getAbsolutePath()), StandardCharsets.UTF_8)) {
                if (line.contains(",")) {
                    newLines.add(line);
                } else {
                    //else this will be the total voting weight, which is being removed
                    System.out.println(bbsFile);
                    System.out.println("Removing line: "+line);
                }
            }
            Files.write(Paths.get(bbsFile.getAbsolutePath()), newLines, StandardCharsets.UTF_8);
            br.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<List<String>> initMatrix(List<GPNode> BBs, List<GPNode> filteredBBs) {
        //should be as many rows as there are building blocks
        List matrix = new ArrayList<>();
        //add an initial row
        List<String> headerRow = new ArrayList<String>();
        headerRow.add("BB");
        headerRow.add("Filtered");
        matrix.add(headerRow);
        //want to put filteredBBs as the top rows, and the rest of the BBs after
        for (GPNode f: filteredBBs) {
            List<String> row = new ArrayList<String>();
            row.add(f.makeCTree(false,true,true));
            row.add(String.valueOf(1)); //included when filtering used
            matrix.add(row);
        }
        //should check whether lists are same size - will be the case when filtering = true
        if (BBs.size() != filteredBBs.size()) {
            for (GPNode b: BBs) {
                if (!filteredBBs.contains(b)) { //don't want to include same building block twice
                    List<String> row = new ArrayList<String>();
                    row.add(b.makeCTree(false,true,true));
                    row.add(String.valueOf(0)); //not included when filtering used
                    matrix.add(row);
                }
            }
        }
        return matrix;
    }

    private static void addToMatrix(List<List<String>> matrix, ContributionSelectionStrategy c,
                                    BBSelectionStrategy b, GPNode[] selBBs) {
        //first want to add this combination of strategies to header
        String header = c.getName()+"-"+b.getName();
        List<String> headerRow = matrix.get(0);
        headerRow.add(header);
        matrix.set(0,headerRow);

        for (int i = 1; i < matrix.size(); ++i) {
            List<String> row = matrix.get(i);
            String bb = row.get(0); //always first item in row
            boolean didSelectBB = false;
            for (int j = 0; j < selBBs.length && !didSelectBB; ++j) {
                GPNode gpNode = selBBs[j];
                if (bb.equals(gpNode.makeCTree(false,true,true))) {
                    //selected this building block!
                    didSelectBB = true;
                }
            }
            if (didSelectBB) {
                row.add("1");
            } else {
                row.add("0");
            }
            matrix.set(i,row);
        }
    }

    private static void writeFilteringMatrix(File outFile, List<List<String>> outputMatrix) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(outFile));
            for (List<String> row: outputMatrix) {
                String rowString = "";
                for (String entry: row) {
                    rowString += entry + ",";
                }
                rowString = rowString.substring(0,rowString.length()-1); //remove lagging ','
                writer.write(rowString);
                writer.newLine();
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method should be run over the job.x.out.stat files. Because of a previous bug, the depth 2 subtrees
     * that had been added to the terminal set (eg. (+ MWT PT)) were being represented in the rule only by their
     * root (eg. +). This leads to parsing problems, as an operator should never be a terminal. This method
     * should iterate through the provided directory, read in the correct building blocks for each seed, and
     * find which rules have incorrectly represented those building blocks.
     *
     * This will not work for a file with multiple building blocks with the same root operator, as there will
     * be no way to know which operator was meant to be used. These files will need to be rerun.
     */
    private static void fixTerminalSubtreeRules(String directory) {
        //eg. simple-fc-train
        String workingDirectory = (new File("")).getAbsolutePath();
        workingDirectory += "/grid_results/dynamic/raw/";
        workingDirectory += directory;

        List<Path> directoryNames = getDirectoryNames(new ArrayList(),Paths.get(workingDirectory),".out.stat");
        for (Path directoryPath: directoryNames) {
            for (int seed = 0; seed < 30; ++seed) {
                boolean broken = false;
                File bbsFile = new File(directoryPath.toString() + "/job." + seed + " - SEQUENCING.bbs.csv");
                File statFile = new File(directoryPath.toString() + "/job." + seed + ".out.stat");
                List<String> bbs = readBuildingBlocks(bbsFile);
                if (bbs.isEmpty()) {
                    //can't have affected any rules
                    continue;
                }

                List<String> allLines = null;
                try {
                    allLines = Files.readAllLines(Paths.get(statFile.getAbsolutePath()), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                List<GPRule> rules = readRules(statFile);
                for (GPRule r : rules) {
                    GPTree ruleTree = r.getGPTree();
                    String oldRule = r.getLispString();
                    List<GPNode> terminals = FeatureUtil.faultyTerminalsInTree(ruleTree.child);
                    for (GPNode terminal : terminals) {
                        //System.out.println("Subtree of type "+terminal.toString()+" found.");
                        List<String> matchingBBs = new ArrayList<>();
                        for (String bb : bbs) {
                            if (bb.contains(terminal.toString())) {
                                matchingBBs.add(bb);
                            }
                        }
                        if (matchingBBs.size() == 0) {
                            System.out.println("Terminal not found, what happened here?");
                        } else if (matchingBBs.size() > 1) {
                            //System.out.println("Unable to determine which BB this operator belongs to, nothing to do here.");
                            broken = true;
                        } else {
                            String fullSubtree = matchingBBs.get(0); //expecting something like "(max TIS OWT)
                            fullSubtree = fullSubtree.substring(1, fullSubtree.length() - 1);
                            String[] args = fullSubtree.split(" ");
                            terminal.children = new GPNode[2];
                            terminal.children[0] = new AttributeGPNode(JobShopAttribute.get(args[1]));
                            terminal.children[1] = new AttributeGPNode(JobShopAttribute.get(args[2]));
                            terminal.children[0].children = new GPNode[0];
                            terminal.children[1].children = new GPNode[0];
                            terminal.children[0].parent = terminal;
                            terminal.children[1].parent = terminal;
                            terminal.children[0].argposition = 0;
                            terminal.children[1].argposition = 1;

                            GPNode root = terminal;
                            while (root.parent != null) {
                                root = (GPNode) root.parent;
                            }
                            String newRule = root.makeLispTree();

                            for (int i = 0; i < allLines.size(); ++i) {
                                String line = allLines.get(i);
                                if (line.contains(oldRule)) {
                                    System.out.println("Changing "+oldRule+" to "+newRule);
                                    allLines.set(i, " " + newRule);
                                }
                            }
                            oldRule = newRule;
                        }
                    }
                }
                if (broken) {
                    System.out.println(bbsFile.toString());
                }
                try {
                    Files.write(Paths.get(statFile.getAbsolutePath()), allLines, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static List<String> readBuildingBlocks(File bbFile) {
        String line = "";
        BufferedReader br = null;
        List<String> bbs = new ArrayList<>();
        try {
            br = new BufferedReader(new FileReader(bbFile));
            while ((line = br.readLine()) != null) {
                String[] vals = line.split(",");
                bbs.add(vals[0]);
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
        return bbs;
    }

    private static List<GPRule> readRules(File statFile) {
        //The below code is taken from RuleTest.writeToCSV
        String line;
        List<GPRule> rules = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(statFile))) {
            while (!(line = br.readLine()).equals("Best Individual of Run:")) {
                if (line.startsWith("Generation")) {
                    br.readLine(); //Best individual:
                    br.readLine(); //Subpopulation i:
                    br.readLine(); //Evaluated: true
                    line = br.readLine(); //this will be either a fitness or collaborator rule

                    br.readLine(); //Tree 0:
                    String expression = br.readLine();

                    GPRule rule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, expression);
                    rules.add(rule);
                }
            }

            br.readLine(); //Subpopulation i:
            br.readLine(); //Evaluated: true
            line = br.readLine(); //this will be either a fitness or collaborator rule

            br.readLine(); //Tree 0:
            String expression = br.readLine();

            expression = LispSimplifier.simplifyExpression(expression);

            //subpop 0 is sequencing rules
            GPRule bestRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, expression);
            rules.add(bestRule);

        } catch (IOException e){
            e.printStackTrace();
        }
        //can get rid of first 51 rules
        rules = rules.subList(50,rules.size());
        return rules;
    }

    private static void fixFile(String fileName) {
        String workingDirectory = (new File("")).getAbsolutePath();
        File file = new File(workingDirectory+"/"+fileName);
        try {
            List<String> newLines = new ArrayList<>();
            for (String line : Files.readAllLines(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8)) {
                //qsub -t 1-30 run-gpjss-fc-dynamic-simple.sh fcgp-simplegp-dynamic 0.85 max-flowtime 3-Clustering BB-0.25xTVW/job.10
                if (line.contains("job")) {
                    int seed = Integer.parseInt(line.substring(line.lastIndexOf('.')+1))+1;
                    line = line.replaceAll("1-30",seed+"-"+seed);
                    line = line.substring(0,line.lastIndexOf('/'));
                }
                newLines.add(line);
            }
            Files.write(Paths.get(file.getAbsolutePath()), newLines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String[] objectives = new String[] {"max-flowtime","mean-flowtime","mean-weighted-flowtime"};
        double[] utilLevels = new double[] {0.85,0.95};

        fixTerminalSubtreeRules("simple-fc-train");
        //fixFile("yi-batch-rungrid-fc-dynamic-simple.sh");

//        for (String objective: objectives) {
//            for (double utilLevel: utilLevels) {
//                System.out.println(objective +" "+utilLevel);
//                for (int seed = 0; seed < 1; ++seed) {
//                    runFeatureConstruction(utilLevel, objective, seed,
//                            RuleType.SEQUENCING, "/data/filter_results/");
////                    runFeatureConstruction(utilLevel, objective, seed,
////                            RuleType.SEQUENCING, "/out/subtree_contributions_filtered/", true);
//                    //addClusterInfomation(utilLevel, objective, seed, RuleType.SEQUENCING);
////                    addBBSelectionFile(utilLevel,
////                            objective,
////                            seed,
////                            RuleType.SEQUENCING,
////                            "/out/subtree_contributions_filtered/");
//                    //removeTotalVotingWeight(utilLevel,objective,seed,RuleType.SEQUENCING);
//                }
//            }
//        }
    }
}
