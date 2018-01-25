package yimei.jss;

import ec.Evolve;
import ec.gp.GPIndividual;
import ec.gp.GPTree;
import ec.util.ParameterDatabase;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import yimei.jss.algorithm.featureconstruction.FCGPRuleEvolutionState;
import yimei.jss.bbselection.*;
import yimei.jss.feature.FeatureUtil;
import yimei.jss.niching.ClearingMultiObjectiveFitness;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static ec.Evolve.loadParameterDatabase;

/**
 * Created by dyska on 14/12/17.
 *
 * This class should allow a set of GP rules to be read in from
 * a file, and fed into a feature construction method for testing.
 */
public class FCMain {

    static String diverseIndisDir = "/data/diverse_individuals/";
    //static String contributionsDir = "/out/subtree_contributions/";

    public static void runFeatureConstruction(double utilLevel, String objective,
                                              int seed, RuleType ruleType, String outputDir, boolean filtering) {
        List<GPIndividual> selIndis = readInSelectedIndividuals(utilLevel, objective, seed, ruleType);

        FCGPRuleEvolutionState state = initState(utilLevel, objective, seed);

        for (GPIndividual indi: selIndis) {
            FeatureUtil.initFitness(state, indi, ruleType);
        }

        //should reorder individuals - highest fitness first
        Collections.sort(selIndis, (o1, o2) -> {
            if (o1.fitness.fitness() < o2.fitness.fitness()) {
                return 1;
            } else if (o1.fitness.fitness() > o2.fitness.fitness()) {
                return -1;
            } else {
                return 0;
            }
        });
//        double fitUB = 1/(1+selIndis.get(0).fitness.fitness());
//        double fitLB = 1 - fitUB;
//
//        System.out.println("");
//        System.out.println("Feature construction analysis being performed for " + ruleType + " population.");
//        FeatureUtil.featureConstruction(state, selIndis, ruleType, fitUB, fitLB, filtering);

        //need to redo all fitness calculations here...
    }

    private static double getTotalVotingWeight(double utilLevel, String objective,
                                        int seed, RuleType ruleType) {
        List<GPIndividual> selIndis = readInSelectedIndividuals(utilLevel, objective, seed, ruleType);

        FCGPRuleEvolutionState state = initState(utilLevel, objective, seed);

        for (GPIndividual indi: selIndis) {
            FeatureUtil.initFitness(state, indi, ruleType);
        }

        //should reorder individuals - highest fitness first
        Collections.sort(selIndis, (o1, o2) -> {
            if (o1.fitness.fitness() < o2.fitness.fitness()) {
                return 1;
            } else if (o1.fitness.fitness() > o2.fitness.fitness()) {
                return -1;
            } else {
                return 0;
            }
        });

        double fitUB = 1/(1+selIndis.get(0).fitness.fitness());
        double fitLB = 1 - fitUB;

        DescriptiveStatistics stat = FeatureUtil.initVotingWeightStat(selIndis, fitUB, fitLB);

        return stat.getSum();
    }

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

    public static List<GPIndividual> readInSelectedIndividuals(double utilLevel, String objective,
                                                               int seed, RuleType ruleType) {
        String directory = (new File("")).getAbsolutePath();
        directory += diverseIndisDir+utilLevel+"-"+objective+"/";
        directory += "job."+seed+" - "+ruleType+".selIndi.csv";
        File indiFile = new File(directory);
        List<GPIndividual> individuals = new ArrayList<>();

        BufferedReader br = null;
        String line = "";

        try {
            br = new BufferedReader(new FileReader(indiFile));
            line = br.readLine(); //skip header
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
        fitness.objectives = new double[] {Double.MAX_VALUE}; //cannot use provided fitness
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

        double totalVotingWeight = getTotalVotingWeight(utilLevel, objective, seed, ruleType);

        //create our list of building block strategies
        List<BBSelectionStrategy> bbStrategies = new ArrayList<>();
        bbStrategies.add(new TopKStrategy(1));
        bbStrategies.add(new BBClusteringStrategy(2));
        bbStrategies.add(new BBClusteringStrategy(3));
        bbStrategies.add( new StaticProportionTotalVotingWeight(totalVotingWeight, 0.25));
        bbStrategies.add(new StaticProportionTotalVotingWeight(totalVotingWeight, 0.50));
        bbStrategies.add(new StaticProportionTotalVotingWeight(totalVotingWeight, 0.75));
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

    public static void main(String[] args) {
        String[] objectives = new String[] {"max-flowtime","mean-flowtime","mean-weighted-flowtime"};
        double[] utilLevels = new double[] {0.85, 0.95};

        for (String objective: objectives) {
            for (double utilLevel: utilLevels) {
                System.out.println(objective +" "+utilLevel);
                for (int seed = 0; seed < 30; ++seed) {
//                    runFeatureConstruction(utilLevel, objective, seed,
//                            RuleType.SEQUENCING, "/out/subtree_contributions/", false);
//                    runFeatureConstruction(utilLevel, objective, seed,
//                            RuleType.SEQUENCING, "/out/subtree_contributions_filtered/", true);
                    //addClusterInfomation(utilLevel, objective, seed, RuleType.SEQUENCING);
                    addBBSelectionFile(utilLevel,
                            objective,
                            seed,
                            RuleType.SEQUENCING,
                            "/out/subtree_contributions_filtered/");
                    //removeTotalVotingWeight(utilLevel,objective,seed,RuleType.SEQUENCING);
                }
            }
        }
    }
}
