package yimei.jss.feature;

import ec.EvolutionState;
import ec.Fitness;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.gp.GPTree;
import ec.multiobjective.MultiObjectiveFitness;
import net.sf.javaml.core.DefaultDataset;
import net.sf.javaml.core.DenseInstance;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import yimei.jss.feature.ignore.Ignorer;
import yimei.jss.gp.GPNodeComparator;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.gp.TerminalsChangable;
import yimei.jss.gp.terminal.BuildingBlock;
import yimei.jss.gp.terminal.ConstantTerminal;
import yimei.jss.niching.ClearingEvaluator;
import yimei.jss.niching.MultiPopCoevolutionaryClearingEvaluator;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import net.sf.javaml.core.Dataset;
import net.sf.javaml.core.Instance;
import net.sf.javaml.clustering.Clusterer;
import net.sf.javaml.clustering.KMeans;

import javax.xml.crypto.Data;

/**
 * Utility functions for feature selection and construction.
 *
 * Created by YiMei on 5/10/16.
 */
public class FeatureUtil {
    public static RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING};

    /**
     * Select a diverse set of individuals from the current population.
     * @param state the current evolution state.
     * @param archive the archive from which the set will be chosen.
     * @param n the number of individuals in the diverse set.
     * @return the selected diverse set of individuals.
     */
    public static List<GPIndividual> selectDiverseIndis(EvolutionState state, Individual[] archive,
                                                        int subPopNum, int n) {
        Arrays.sort(archive);

        PhenoCharacterisation pc = null;
        double radius = 0;
        if (state.evaluator instanceof ClearingEvaluator) {
            ClearingEvaluator clearingEvaluator = (ClearingEvaluator) state.evaluator;
            pc = clearingEvaluator.getPhenoCharacterisation()[subPopNum];
            radius = clearingEvaluator.getRadius();
        } else if (state.evaluator instanceof MultiPopCoevolutionaryClearingEvaluator) {
            MultiPopCoevolutionaryClearingEvaluator clearingEvaluator =
                    (MultiPopCoevolutionaryClearingEvaluator) state.evaluator;
            pc = clearingEvaluator.getPhenoCharacterisation()[subPopNum];
            radius = clearingEvaluator.getRadius();
        }
        RuleType ruleType = ruleTypes[subPopNum];
        pc.setReferenceRule(new GPRule(ruleType,((GPIndividual)archive[0]).trees[0]));

        List<GPIndividual> selIndis = new ArrayList<>();
        List<int[]> selIndiCharLists = new ArrayList<>();

        for (Individual indi : archive) {
            boolean tooClose = false;

            GPIndividual gpIndi = (GPIndividual) indi;

            int[] charList = pc.characterise(new GPRule(ruleType,gpIndi.trees[0]));

            for (int i = 0; i < selIndis.size(); i++) {
                double distance = PhenoCharacterisation.distance(charList, selIndiCharLists.get(i));
                if (distance <= radius) {
                    tooClose = true;
                    break;
                }
            }

            if (tooClose)
                continue;

            selIndis.add(gpIndi);
            selIndiCharLists.add(charList);

            if (selIndis.size() == n)
                break;
        }

        return selIndis;
    }

    public static void terminalsInTree(List<GPNode> terminals, GPNode tree) {
        if (tree.depth() == 0) {
            boolean duplicated = false;

            for (GPNode terminal : terminals) {
                if (terminal.toString().equals(tree.toString())) {
                    duplicated = true;
                    break;
                }
            }

            if (!duplicated)
                terminals.add(tree);
        }
        else {
            for (GPNode child : tree.children) {
                terminalsInTree(terminals, child);
            }
        }
    }

    public static List<GPNode> terminalsInTree(GPNode tree) {
        List<GPNode> terminals = new ArrayList<>();
        terminalsInTree(terminals, tree);

        return terminals;
    }

    /**
     * Calculate the contribution of a feature to an individual
     * using the current training set.
     * @param state the current evolution state (training set).
     * @param indi the individual.
     * @param feature the feature.
     * @return the contribution of the feature to the individual.
     */
    public static double contribution(EvolutionState state,
                                      GPIndividual indi,
                                      GPNode feature,
                                      RuleType ruleType) {
        RuleOptimizationProblem problem =
                (RuleOptimizationProblem)state.evaluator.p_problem;
        Ignorer ignorer = ((FeatureIgnorable)state).getIgnorer();

        MultiObjectiveFitness fit1 = (MultiObjectiveFitness) indi.fitness;
        MultiObjectiveFitness fit2 = (MultiObjectiveFitness) fit1.clone();
        GPRule rule = new GPRule(ruleType,(GPTree)indi.trees[0].clone());
        rule.ignore(feature, ignorer);

        int numSubPops = state.population.subpops.length;

        Fitness[] fitnesses = new Fitness[numSubPops];
        GPRule[] rules = new GPRule[numSubPops];
        int index = 0;
        if (ruleType == RuleType.ROUTING) {
            index = 1;
        }

        //It is important that sequencing rule is at [0] and routing rule is at [1]
        //as the CCGP evaluation model is expecting this
        fitnesses[index] = fit2;
        rules[index] = rule;

        if (numSubPops == 2) {
            //also need to get context of other rule to compare
            RuleType otherRuleType = RuleType.SEQUENCING;
            int contextIndex = 0;
            if (ruleType == RuleType.SEQUENCING) {
                otherRuleType = RuleType.ROUTING;
                contextIndex = 1;
            }

            GPIndividual contextIndi = (GPIndividual) fit2.context[contextIndex];
            MultiObjectiveFitness contextFitness = (MultiObjectiveFitness) contextIndi.fitness.clone();
            GPRule contextRule = new GPRule(otherRuleType,
                    (GPTree) (contextIndi).trees[0].clone());
            fitnesses[contextIndex] = contextFitness;
            rules[contextIndex] = contextRule;
        }

        problem.getEvaluationModel().evaluate(Arrays.asList(fitnesses), Arrays.asList(rules), state);

        return fit2.fitness() - fit1.fitness();
    }

    /**
     * Feature selection by majority voting based on feature contributions.
     * @param state the current evolution state (training set).
     * @param selIndis the selected diverse set of individuals.
     * @param fitUB the upper bound of individual fitness.
     * @param fitLB the lower bound of individual fitness.
     * @return the set of selected features.
     */
    public static GPNode[] featureSelection(EvolutionState state,
                                                List<GPIndividual> selIndis,
                                                RuleType ruleType,
                                                double fitUB, double fitLB) {
        DescriptiveStatistics votingWeightStat = new DescriptiveStatistics();

        for (GPIndividual selIndi : selIndis) {
            double normFit = (selIndi.fitness.fitness() - fitLB) / (fitUB - fitLB);

            if (normFit < 0) {
                normFit = 0;
            }
            double votingWeight = normFit;
            votingWeightStat.addValue(votingWeight);
        }

        double totalVotingWeight = votingWeightStat.getSum();

        List<DescriptiveStatistics> featureContributionStats = new ArrayList<>();
        List<DescriptiveStatistics> featureVotingWeightStats = new ArrayList<>();

        int subPopNum = 0;
        if (ruleType == RuleType.ROUTING) {
            subPopNum = 1;
        }

        GPNode[] terminals = ((TerminalsChangable)state).getTerminals(subPopNum);

        for (int i = 0; i < terminals.length; i++) {
            featureContributionStats.add(new DescriptiveStatistics());
            featureVotingWeightStats.add(new DescriptiveStatistics());
        }

        for (int s = 0; s < selIndis.size(); s++) {
            GPIndividual selIndi = selIndis.get(s);

            for (int i = 0; i < terminals.length; i++) {
                double c = contribution(state, selIndi, terminals[i], ruleType);
                featureContributionStats.get(i).addValue(c);

                if (c > 0.001) {
                    featureVotingWeightStats.get(i).addValue(votingWeightStat.getElement(s));
                }
                else {
                    featureVotingWeightStats.get(i).addValue(0);
                }
            }
        }

        long jobSeed = ((GPRuleEvolutionState)state).getJobSeed();
        String outputPath = initPath(state);
        File featureInfoFile = new File(outputPath + "job." + jobSeed +
                " - "+ ruleType.name() + ".fsinfo.csv");

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(featureInfoFile));
            writer.write("Feature,Fitness,Contribution,VotingWeights,NormFit,Size");
            writer.newLine();

            for (int i = 0; i < terminals.length; i++) {
                for (int j = 0; j < selIndis.size(); j++) {
                    writer.write(terminals[i].toString() + "," +
                            selIndis.get(j).fitness.fitness() + "," +
                            featureContributionStats.get(i).getElement(j) + "," +
                            featureVotingWeightStats.get(i).getElement(j) + "," +
                            votingWeightStat.getElement(j) + "," +
                            selIndis.get(j).size());
                    writer.newLine();
                }
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<GPNode> selFeatures = new LinkedList<>();

        for (int i = 0; i < terminals.length; i++) {
            double votingWeight = featureVotingWeightStats.get(i).getSum();

            // majority voting
            if (votingWeight > 0.5 * totalVotingWeight) {
                selFeatures.add(terminals[i]);
            }
        }

        File fsFile = new File(outputPath + "job." + jobSeed + " - "
                + ruleType.name() + ".terminals.csv");

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fsFile));

            for (GPNode terminal : selFeatures) {
                writer.write(terminal.toString());
                writer.newLine();
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return selFeatures.toArray(new GPNode[0]);
    }

    /**
     * Feature construction by majority voting based on contribution.
     * A constructed feature/building block is a depth-2 sub-tree.
     * @param state the current evolution state (training set).
     * @param selIndis the selected diverse set of individuals.
     * @param fitUB the upper bound of individual fitness.
     * @param fitLB the lower bound of individual fitness.
     * @return the constructed features (building blocks).
     */
    public static List<GPNode> featureConstruction(EvolutionState state,
                                                   List<GPIndividual> selIndis,
                                                   RuleType ruleType,
                                                   double fitUB, double fitLB) {
        List<GPNode> BBs = buildingBlocks(selIndis, 2);

        DescriptiveStatistics votingWeightStat = new DescriptiveStatistics();

        for (GPIndividual selIndi : selIndis) {
            double normFit = (selIndi.fitness.fitness() - fitLB) / (fitUB - fitLB);

            if (normFit < 0) {
                normFit = 0;
            }
            double votingWeight = normFit;
            votingWeightStat.addValue(votingWeight);
        }

        double totalVotingWeight = votingWeightStat.getSum();

        List<DescriptiveStatistics> BBContributionStats = new ArrayList<>();
        List<DescriptiveStatistics> BBVotingWeightStats = new ArrayList<>();

        for (int i = 0; i < BBs.size(); i++) {
            BBContributionStats.add(new DescriptiveStatistics());
            BBVotingWeightStats.add(new DescriptiveStatistics());
        }

        Dataset data = new DefaultDataset();
        //need to record id of instances so we can get them later
        int[][] instanceIDs = new int[selIndis.size()][BBs.size()];

        for (int s = 0; s < selIndis.size(); s++) {
            GPIndividual selIndi = selIndis.get(s);

            for (int i = 0; i < BBs.size(); i++) {
                double[] c = new double[] {contribution(state, selIndi, BBs.get(i), ruleType)};
                BBContributionStats.get(i).addValue(c[0]);

                //we have |selIndis| * |BB| individuals
                //want to cluster them, and assign them either large or small (play with this I guess)
                //need to create instances to add to dataset
                if (c[0] > 0.0) {
                    //no point including value if it has no value
                    Instance instance = new DenseInstance(c);
                    data.add(instance);
                    instanceIDs[s][i] = instance.getID();
                } else {
                    instanceIDs[s][i] = -1; //didn't get included
                }

//                if (c > 0.001) {
//                    BBVotingWeightStats.get(i).addValue(votingWeightStat.getElement(s));
//                }
//                else {
//                    BBVotingWeightStats.get(i).addValue(0);
//                }
            }
        }

        //now we have our full dataset
        //can do clustering!
        //Dataset dataset_copy_3 = data.copy();
        //Dataset dataset_copy_4 = data.copy();
//        Dataset dataset_copy_5 = data.copy();
//        Dataset dataset_copy_6 = data.copy();
//
//        Clusterer km_2 = new KMeans(2);
//        Clusterer km_3 = new KMeans(3);
        int k = 2;
        KMeans km = new KMeans(k);
//        Clusterer km_5 = new KMeans(5);
//        Clusterer km_6 = new KMeans(6);

//        Dataset[] clusters_2 = km_2.cluster(data);
//        Dataset[] clusters_3 = km_3.cluster(dataset_copy_3);
        Dataset[] clusters = km.cluster(data);

        //need to find the cluster with the lowest values
        //clusters will have distinct ranges - any value from a will be lower than any value from b
        //if a < b
        Double lowest = clusters[0].instance(0).get(0);
        int worstClusterIndex = 0;
        for (int i = 1; i < clusters.length; ++i) {
            Double instance = clusters[i].instance(0).get(0);
            if (instance < lowest) {
                worstClusterIndex = i;
                lowest = instance;
            }
        }

//        Dataset[] clusters_5 = km_5.cluster(dataset_copy_5);
//        Dataset[] clusters_6 = km_6.cluster(dataset_copy_6);

        //what do these guys look like?

//        Dataset[][] clusters = new Dataset[][] {
//                //clusters_2,
//                //clusters_3,
//                clusters_4,
//                //clusters_5,
//                //clusters_6
//        };
        for (int i = 0; i < clusters.length; ++i) {
            Dataset d = clusters[i];
            d.sort((o1, o2) -> {
                if (o1.get(0) > o2.get(0)) {
                    return 1;
                } else if (o1.get(0) < o2.get(0)){
                    return -1;
                }
                return 0;
            });
            System.out.println("Cluster "+(i+1)+" has "+d.size()+
                    " values, ranging from "+d.get(0)+" to "+d.get(d.size()-1));
        }

        //clustering complete, now need to find which cluster has the worst values
        //as this is the cluster which will have its contributions excluded

        //clustering complete, now decide which values to include
        for (int s = 0; s < selIndis.size(); s++) {
            for (int i = 0; i < BBs.size(); i++) {
                int instanceID = instanceIDs[s][i];
                if (instanceID == -1) {
                    //wasn't clustered, must have been non-positive contribution
                    BBVotingWeightStats.get(i).addValue(0);
                    //System.out.println("Contribution of subtree "+i+" to rule "+s+" was non-positive.");
                } else {
                    boolean inWorstCluster = false;
                    //was clustered, now we just need to know whether it was in bottom cluster or not
                    Dataset worstCluster = clusters[worstClusterIndex];
                    for (int j = 0; j < worstCluster.size() && !inWorstCluster; ++j) {
                        if (worstCluster.get(j).getID() == instanceID) {
                            inWorstCluster = true;
                            BBVotingWeightStats.get(i).addValue(0);
                            //System.out.println("Contribution of subtree "+i+" to rule "+s+" was in the bottom cluster.");
                        }
                    }
                    if (!inWorstCluster) {
                        //should get to vote!
                        BBVotingWeightStats.get(i).addValue(votingWeightStat.getElement(s));
                        System.out.println("Rule "+s+" voted for building block "+i+
                                " with weight "+votingWeightStat.getElement(s)+".");
                    }
                }
            }
        }

        long jobSeed = ((GPRuleEvolutionState)state).getJobSeed();
        String outputPath = initPath(state);
        File BBInfoFile = new File(outputPath + "job." + jobSeed +
                " - "+ ruleType.name() + ".fcinfo.csv");

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(BBInfoFile));
            writer.write("BB,Fitness,Contribution,VotingWeights,NormFit,Size");
            writer.newLine();

            for (int i = 0; i < BBs.size(); i++) {
                BuildingBlock bb = new BuildingBlock(BBs.get(i));

                for (int j = 0; j < selIndis.size(); j++) {
                    writer.write(bb.toString() + "," +
                            selIndis.get(j).fitness.fitness() + "," +
                            BBContributionStats.get(i).getElement(j) + "," +
                            BBVotingWeightStats.get(i).getElement(j) + "," +
                            votingWeightStat.getElement(j) + "," +
                            selIndis.get(j).size());
                    writer.newLine();
                }
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<GPNode> selBBs = new LinkedList<>();
        //System.out.println("Feature requires voting weight "+(0.5 * totalVotingWeight)+" for selection.");
        for (int i = 0; i < BBs.size(); i++) {
            double votingWeight = BBVotingWeightStats.get(i).getSum();
            //can use makeCTree as only depth 2, so not too inefficient
            System.out.println(BBs.get(i).makeCTree(false,true,true) +" - voting weight: "+votingWeight);
            //System.out.println("Feature requires voting weight "+(0.5 * totalVotingWeight)+" for selection.");
            // majority voting
            //TODO: Review this - seems unneccessarily restrictive
            if (votingWeight > 0.0) {
                selBBs.add(BBs.get(i));
            }
        }

        File fcFile = new File(outputPath + "job." + jobSeed + " - "+ ruleType.name() + ".bbs.csv");
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fcFile));

            for (GPNode BB : selBBs) {
                BuildingBlock bb = new BuildingBlock(BB);
                writer.write(bb.toString());
                writer.newLine();
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return selBBs;
    }

    /**
     * Find all the depth-k sub-tree as building blocks from a set of individuals.
     * @param indis the set of individuals.
     * @param depth the depth of the sub-trees/building blocks.
     * @return the building blocks.
     */
    public static List<GPNode> buildingBlocks(List<GPIndividual> indis, int depth) {
        List<GPNode> bbs = new ArrayList<>();
        HashMap<String, Integer> bbCounts = new HashMap();

        for (GPIndividual indi : indis) {
            collectBuildingBlocks(bbs, bbCounts, indi.trees[0].child, depth);
        }

        for (String subtree: bbCounts.keySet()) {
            System.out.println("Subtree "+subtree+
                    " appeared "+bbCounts.get(subtree)+" times.");
        }

        return bbs;
    }

    /**
     * Collect all the depth-k building blocks from a tree.
     * @param buildingBlockCounts the set of building blocks along with the number of
     *                            occurrences.
     * @param tree the tree.
     * @param depth the depth of the building blocks.
     */
    public static void collectBuildingBlocks(List<GPNode> buildingBlocks,
                                             HashMap<String, Integer> buildingBlockCounts,
                                             GPNode tree,
                                             int depth) {
        if (tree.depth() == depth) {
            boolean duplicate = false;

            for (GPNode bb : buildingBlocks) {
                if (GPNodeComparator.equals(tree, bb)) {
                    duplicate = true;
                    break;
                }
            }

            if (!duplicate)
                buildingBlocks.add(tree);

            //turn tree into usable object
            //just working for depth 2
            String treeString = tree.toString()+" "+tree.children[0].toString()+
                    " "+tree.children[1].toString();

            //add tree to hashmap, increment count if one exists
            int count = buildingBlockCounts.getOrDefault(treeString,0);

            buildingBlockCounts.put(treeString,count+1);
        }
        else {
            for (GPNode child : tree.children) {
                collectBuildingBlocks(buildingBlocks, buildingBlockCounts, child, depth);
            }
        }
    }

    /**
     * Adapt the current population into three parts based on a changed
     * terminal set.
     * @param state the current evolution state (new terminal set).
     * @param fracElites the fraction of elite (directly copy).
     * @param fracAdapted the fraction of adapted (fix the ignored features to 1.0).
     */
    public static void adaptPopulationThreeParts(EvolutionState state,
                                                 double fracElites,
                                                 double fracAdapted,
                                                 int subPopNum) {
        GPNode[] terminals = ((TerminalsChangable)state).getTerminals(subPopNum);

        Individual[] newPop = state.population.subpops[0].individuals;
        int numElites = (int)(fracElites * newPop.length);
        int numAdapted = (int)(fracAdapted * newPop.length);

        // Sort the individuals from best to worst
        Arrays.sort(newPop);

        // Part 1: keep the elites from 0 to numElite-1
//        for (int i = 0; i < numElites; i++) {
//			System.out.println("Indi " + i + ", fitness = " + newPop[i].fitness.fitness());
//        }
        // Part 2: replace the unselected terminals by 1
        for (int i = numElites; i < numElites + numAdapted; i++) {
//			System.out.println("Indi " + i + ", fitness = " + newPop[i].fitness.fitness());
            adaptTree(((GPIndividual)newPop[i]).trees[0].child, terminals);
            newPop[i].evaluated = false;
        }

        // Part 3: reinitialize the remaining individuals
        for (int i = numElites + numAdapted; i < newPop.length; i++) {
//			System.out.println("Indi " + i + ", fitness = " + newPop[i].fitness.fitness());
            newPop[i] = state.population.subpops[0].species.newIndividual(state, 0);
            newPop[i].evaluated = false;
        }
    }

    /**
     * Adapt a tree using the new terminal set.
     * @param tree the tree.
     * @param terminals the new terminal set.
     */
    private static void adaptTree(GPNode tree, GPNode[] terminals) {
        if (tree.children.length == 0) {
            // It's a terminal
            boolean selected = false;
            for (GPNode terminal : terminals) {
                if (tree.toString().equals(terminal.toString())) {
                    selected = true;
                    break;
                }
            }

            if (!selected) {
                GPNode newTree = new ConstantTerminal(1.0);
                newTree.parent = tree.parent;
                newTree.argposition = tree.argposition;
                if (newTree.parent instanceof GPNode) {
                    ((GPNode)(newTree.parent)).children[newTree.argposition] = newTree;
                }
                else {
                    ((GPTree)(newTree.parent)).child = newTree;
                }
            }
        }
        else {
            for (GPNode child : tree.children) {
                adaptTree(child, terminals);
            }
        }
    }

    private static String initPath(EvolutionState state) {
//        String outputPath = "/Users/dyska/Desktop/Uni/COMP489/GPJSS/out/terminals/";
//        if (state.population.subpops.length == 2) {
//            outputPath += "coevolution/";
//        } else {
//            outputPath += "simple/";
//        }
//        String filePath = state.parameters.getString(new Parameter("filePath"), null);
//        if (filePath == null) {
//            outputPath += "dynamic/";
//        } else {
//            outputPath += "static/";
//        }
//        return outputPath;
        return "";
    }
}
