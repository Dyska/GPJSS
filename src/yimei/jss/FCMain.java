package yimei.jss;

import ec.Evolve;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.gp.GPTree;
import ec.util.ParameterDatabase;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import yimei.jss.algorithm.featureconstruction.FCGPRuleEvolutionState;
import yimei.jss.bbselection.*;
import yimei.jss.feature.FeatureUtil;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.BuildingBlock;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.niching.ClearingMultiObjectiveFitness;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
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
    static String contributionsDir = "/out/subtree_contributions/";

    public static void runFeatureConstruction(double utilLevel, String objective,
                                              int seed, RuleType ruleType) {
        List<GPIndividual> selIndis = readInSelectedIndividuals(utilLevel, objective, seed, ruleType);

        FCGPRuleEvolutionState state = initState(utilLevel, objective, seed);

        for (GPIndividual indi: selIndis) {
            FeatureUtil.initFitness(state, indi, ruleType);
        }

        //should reorder individuals - lowest fitness first
        //and update fitUB and fitLB
        Collections.sort(selIndis, (o1, o2) -> {
            if (o1.fitness.fitness() > o2.fitness.fitness()) {
                return 1;
            } else if (o1.fitness.fitness() < o2.fitness.fitness()) {
                return -1;
            } else {
                return 0;
            }
        });
        double fitUB = selIndis.get(0).fitness.fitness();
        double fitLB = 1 - fitUB;

        System.out.println("");
        System.out.println("Feature construction analysis being performed for " + ruleType + " population.");
        FeatureUtil.featureConstruction(state, selIndis, ruleType, fitUB, fitLB);
    }

    private static double getTotalVotingWeight(double utilLevel, String objective,
                                        int seed, RuleType ruleType) {
        List<GPIndividual> selIndis = readInSelectedIndividuals(utilLevel, objective, seed, ruleType);

        FCGPRuleEvolutionState state = initState(utilLevel, objective, seed);

        for (GPIndividual indi: selIndis) {
            FeatureUtil.initFitness(state, indi, ruleType);
        }

        //should reorder individuals - lowest fitness first
        //and update fitUB and fitLB
        Collections.sort(selIndis, (o1, o2) -> {
            if (o1.fitness.fitness() > o2.fitness.fitness()) {
                return 1;
            } else if (o1.fitness.fitness() < o2.fitness.fitness()) {
                return -1;
            } else {
                return 0;
            }
        });
        double fitUB = selIndis.get(0).fitness.fitness();
        double fitLB = 1 - fitUB;

        return FeatureUtil.getTotalVotingWeight(selIndis, ruleType, fitUB, fitLB);
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
        fitness.objectives = new double[] {10.0}; //cannot use provided fitness
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
    public static void addClusterInfomation(double utilLevel, String objective, int seed, RuleType ruleType) {
        String directory = (new File("")).getAbsolutePath();
        directory += contributionsDir+utilLevel+"-"+objective+"/";
        directory += "job."+seed+" - "+ruleType+".fcinfo.csv";
        File file = new File(directory);
        FeatureUtil.addClusterColumn(file);
    }

    public static void addBBSelectionFile(double utilLevel, String objective, int seed, RuleType ruleType) {
        String directory = (new File("")).getAbsolutePath();
        directory += contributionsDir+utilLevel+"-"+objective+"/";
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

        //create our list of strategies
        List<BBSelectionStrategy> strategies = new ArrayList<>();
        strategies.add(new KHighestStrategy(2));
        strategies.add(new KHighestStrategy(3));
        strategies.add(new ClusteringStrategy(2));
        strategies.add(new ClusteringStrategy(3));
        strategies.add( new StaticProportionTotalVotingWeight(totalVotingWeight, 0.25));
        strategies.add(new StaticProportionTotalVotingWeight(totalVotingWeight, 0.50));
        strategies.add(new StaticProportionTotalVotingWeight(totalVotingWeight, 0.75));
        strategies.add(new StaticThresholdStrategy(5.0));
        strategies.add(new StaticThresholdStrategy(10.0));
        strategies.add(new StaticThresholdStrategy(15.0));

        //going to have BBs.size()+1 rows, and strategies.size()+1 columns
        List<String> outputRows = new ArrayList<>();

        String headerRow = "BB,VotingWeight";
        for (BBSelectionStrategy strategy: strategies) {
            headerRow += "," + strategy.getName();
        }
        outputRows.add(headerRow);

        for (int i = 0; i < BBs.size(); ++i) {
            outputRows.add(BBs.get(i)+","+BBVotingWeightStats.get(i));
        }

        boolean doPrint = false;
        for (BBSelectionStrategy strategy: strategies) {
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

    public static void main(String[] args) {
        String[] objectives = new String[] {"max-flowtime","mean-flowtime","mean-weighted-flowtime"};
        double[] utilLevels = new double[] {0.85, 0.95};

        for (String objective: objectives) {
            for (double utilLevel: utilLevels) {
                for (int seed = 0; seed < 30; ++seed) {
                    //runFeatureConstruction(utilLevel, objective, seed, RuleType.SEQUENCING);
                    //addClusterInfomation(utilLevel, objective, seed, RuleType.SEQUENCING);
                    addBBSelectionFile(utilLevel,objective,seed,RuleType.SEQUENCING);
                }
            }
        }
    }
}
