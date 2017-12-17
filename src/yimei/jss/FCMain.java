package yimei.jss;

import ec.Evolve;
import ec.gp.GPIndividual;
import ec.gp.GPTree;
import ec.util.ParameterDatabase;
import yimei.jss.algorithm.featureconstruction.FCGPRuleEvolutionState;
import yimei.jss.feature.FeatureUtil;
import yimei.jss.niching.ClearingMultiObjectiveFitness;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static ec.Evolve.loadParameterDatabase;

/**
 * Created by dyska on 14/12/17.
 *
 * This class should allow a set of GP rules to be read in from
 * a file, and fed into a feature construction method for testing.
 */
public class FCMain {

    static String workingDirectory = "/data/diverse_individuals/";

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
        directory += workingDirectory+utilLevel+"-"+objective+"/";
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

    public static void main(String[] args) {
        String[] objectives = new String[] {"mean-flowtime", "max-flowtime", "mean-weighted-flowtime"};
        double[] utilLevels = new double[] {0.85, 0.95};

        for (String objective: objectives) {
            for (double utilLevel: utilLevels) {
                for (int seed = 0; seed < 30; ++seed) {
                    runFeatureConstruction(utilLevel, objective, seed, RuleType.SEQUENCING);
                }
            }
        }
    }
}
