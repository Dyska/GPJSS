package yimei.jss.feature;

import ec.EvolutionState;
import ec.Fitness;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.gp.GPTree;
import ec.multiobjective.MultiObjectiveFitness;
import net.sf.javaml.clustering.KMeans;
import net.sf.javaml.core.Dataset;
import net.sf.javaml.core.DefaultDataset;
import net.sf.javaml.core.DenseInstance;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import yimei.jss.bbselection.BBSelectionStrategy;
import yimei.jss.bbselection.BBStaticThresholdStrategy;
import yimei.jss.contributionselection.ContributionClusteringStrategy;
import yimei.jss.contributionselection.ContributionSelectionStrategy;
import yimei.jss.contributionselection.ContributionStaticThresholdStrategy;
import yimei.jss.feature.ignore.Ignorer;
import yimei.jss.gp.GPNodeComparator;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.gp.TerminalsChangable;
import yimei.jss.gp.function.Div;
import yimei.jss.gp.function.Mul;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.BuildingBlock;
import yimei.jss.gp.terminal.ConstantTerminal;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.jobshop.Objective;
import yimei.jss.niching.*;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleevaluation.SimpleEvaluationModel;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;
import yimei.jss.simulation.DynamicSimulation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

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

        int numSubPops;
        if (state.population == null) {
            //happens if we're entering through FCMain
            numSubPops = 1;
        } else {
            numSubPops = state.population.subpops.length;
        }

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

        //It is important that sequencing rule is at [0] and routing rule is at [1]
        //as the CCGP evaluation model is expecting this
        fitnesses[index] = fit2;
        rules[index] = rule;

        problem.getEvaluationModel().evaluate(Arrays.asList(fitnesses), Arrays.asList(rules), state);

        return fit2.fitness() - fit1.fitness();
    }

    public static void initFitness(EvolutionState state,
                                   GPIndividual indi,
                                   RuleType ruleType) {
        RuleOptimizationProblem problem =
                (RuleOptimizationProblem)state.evaluator.p_problem;

        MultiObjectiveFitness fit = (MultiObjectiveFitness) indi.fitness;

        if (fit.fitness() != Double.MAX_VALUE) {
            System.out.println("Expecting to be here because fitness has not be initialised, what's going on?...");
            return;
        }

        //fitness hasn't been initialised
        GPRule originalRule = new GPRule(ruleType,(GPTree)indi.trees[0].clone());

        Fitness[] fitnessesOriginal = new Fitness[] {fit};
        GPRule[] rulesOriginal = new GPRule[] {originalRule};

        problem.getEvaluationModel().evaluate(Arrays.asList(fitnessesOriginal), Arrays.asList(rulesOriginal), state);
        //System.out.println("Fitness of rule: "+fit.fitness());
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
            double normFit = (1/(1+selIndi.fitness.fitness()) - fitLB) / (fitUB - fitLB);

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
        String outputPath = initPath(state,false);
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

    public static DescriptiveStatistics initVotingWeightStat(List<GPIndividual> selIndis,
                                              double fitUB, double fitLB) {
        DescriptiveStatistics votingWeightStat = new DescriptiveStatistics();

        for (GPIndividual selIndi : selIndis) {
            //expecting normalised fitness, this is required unfortunately
            double normFit = (1/(1+selIndi.fitness.fitness()) - fitLB) / (fitUB - fitLB);

            if (normFit < 0) {
                normFit = 0;
            }
            double votingWeight = normFit;
            votingWeightStat.addValue(votingWeight);
        }

        return votingWeightStat;
    }


    /**
     * Feature construction by majority voting based on contribution.
     * A constructed feature/building block is a depth-2 sub-tree.
     * This method overloads the featureConstruction method below,
     * providing default contribution and building block selection
     * strategies.
     * @param state the current evolution state (training set).
     * @param selIndis the selected diverse set of individuals.
     * @param fitUB the upper bound of individual fitness.
     * @param fitLB the lower bound of individual fitness.
     * @param preFiltering whether or not to filter building blocks.
     * @param ruleType which type of GP rules we are analysing.
     * @return the constructed features (building blocks).
     */
    public static List<GPNode> featureConstruction(EvolutionState state,
                                                   List<GPIndividual> selIndis,
                                                   RuleType ruleType,
                                                   double fitUB, double fitLB,
                                                   boolean preFiltering) {
        ContributionSelectionStrategy contributionStrategy =
                new ContributionStaticThresholdStrategy(0.001);
        BBSelectionStrategy bbStrategy = new BBStaticThresholdStrategy(0.0);
        return featureConstruction(state, selIndis, ruleType, fitUB, fitLB,
                preFiltering, contributionStrategy, bbStrategy);
    }

    /**
     * Feature construction by majority voting based on contribution.
     * A constructed feature/building block is a depth-2 sub-tree.
     * This method overloads the featureConstruction method below,
     * providing the ability to pass string representations of
     * building block and contribution selection strategies.
     * This is useful for calling from FCGPRuleEvolutionState.
     * @param state the current evolution state (training set).
     * @param selIndis the selected diverse set of individuals.
     * @param fitUB the upper bound of individual fitness.
     * @param fitLB the lower bound of individual fitness.
     * @param preFiltering whether or not to filter building blocks.
     * @param ruleType which type of GP rules we are analysing.
     * @param contributionStrategyStr name of strategy for selection of meaningful contributions.
     * @param bbStrategyStr name of strategy for selection of meaningful building blocks.
     * @return the constructed features (building blocks).
     */
    public static List<GPNode> featureConstruction(EvolutionState state,
                                                   List<GPIndividual> selIndis,
                                                   RuleType ruleType,
                                                   double fitUB, double fitLB,
                                                   boolean preFiltering,
                                                   String contributionStrategyStr,
                                                   String bbStrategyStr) {
        ContributionSelectionStrategy contributionStrategy =
                ContributionSelectionStrategy.selectStrategy(contributionStrategyStr);
        BBSelectionStrategy bbStrategy = BBSelectionStrategy.selectStrategy(bbStrategyStr);
        return featureConstruction(state, selIndis, ruleType, fitUB, fitLB,
                preFiltering, contributionStrategy, bbStrategy);
    }

    /**
     * Feature construction by majority voting based on contribution.
     * A constructed feature/building block is a depth-2 sub-tree.
     * @param state the current evolution state (training set).
     * @param selIndis the selected diverse set of individuals.
     * @param fitUB the upper bound of individual fitness.
     * @param fitLB the lower bound of individual fitness.
     * @param preFiltering whether or not to filter building blocks.
     * @param ruleType which type of GP rules we are analysing.
     * @param contributionStrategy strategy for selection of meaningful contributions.
     * @param bbStrategy strategy for selection of meaningful building blocks.
     * @return the constructed features (building blocks).
     */
    public static List<GPNode> featureConstruction(EvolutionState state,
                                                   List<GPIndividual> selIndis,
                                                   RuleType ruleType,
                                                   double fitUB, double fitLB,
                                                   boolean preFiltering,
                                                   ContributionSelectionStrategy contributionStrategy,
                                                   BBSelectionStrategy bbStrategy) {
        DescriptiveStatistics votingWeightStat = initVotingWeightStat(selIndis, fitUB, fitLB);

        List<GPNode> BBs = buildingBlocks(selIndis, 2);

        if (preFiltering) {
            BBs = prefilterBBs(BBs);
        }

        List<DescriptiveStatistics> BBContributionStats = new ArrayList<>();
        List<DescriptiveStatistics> BBVotingWeightStats = new ArrayList<>();

        for (int i = 0; i < BBs.size(); i++) {
            BBContributionStats.add(new DescriptiveStatistics());
            BBVotingWeightStats.add(new DescriptiveStatistics());
        }

        double[][] contributions = new double[selIndis.size()][BBs.size()];

        for (int s = 0; s < selIndis.size(); s++) {
            //System.out.println("Finding contributions for rule "+s);
            GPIndividual selIndi = selIndis.get(s);

            for (int i = 0; i < BBs.size(); i++) {
                double c = contribution(state, selIndi, BBs.get(i), ruleType);
                contributions[s][i] = c;
                BBContributionStats.get(i).addValue(c);
            }
        }

        //select which contributions to count
        contributionStrategy.selectContributions(contributions,selIndis,BBs,BBVotingWeightStats, votingWeightStat);

        long jobSeed = ((GPRuleEvolutionState)state).getJobSeed();
        String outputPath = initPath(state, preFiltering);
        File BBInfoFile = new File(outputPath + "job." + jobSeed +
                " - "+ ruleType.name() + ".fcinfo.csv");

        writeContributions(BBInfoFile,BBs,selIndis,BBContributionStats,BBVotingWeightStats,votingWeightStat);

        //Now select which building blocks to record!

        List<GPNode> selBBs = new LinkedList<>();
        List<Double> selBBsVotingWeights = new LinkedList<>();

        //update these empty lists with results
        bbStrategy.selectBuildingBlocks(BBs,selBBs,BBVotingWeightStats,selBBsVotingWeights);

        //write to file
        File fcFile = new File(outputPath + "job." + jobSeed + " - "+ ruleType.name() + ".bbs.csv");
        writeBBs(fcFile, selBBs, selBBsVotingWeights);

        return selBBs;
    }

    public static void writeBBs(File fcFile, List<GPNode> selBBs, List<Double> selBBsVotingWeights) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fcFile));

            for (int i = 0; i < selBBs.size(); ++i) {
                GPNode BB = selBBs.get(i);
                BuildingBlock bb = new BuildingBlock(BB);

                writer.write(bb.toString()+","+selBBsVotingWeights.get(i));
                writer.newLine();
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeContributions(File BBInfoFile,
                                          List<GPNode> BBs,
                                          List<GPIndividual> selIndis,
                                          List<DescriptiveStatistics> BBContributionStats,
                                          List<DescriptiveStatistics> BBVotingWeightStats,
                                          DescriptiveStatistics votingWeightStat) {
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
    }

    public static Dataset[] clusterContributions(Dataset data, int k) {
        KMeans km = new KMeans(k);
        Dataset[] clusters = km.cluster(data);

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
        return clusters;
    }

    public static int findWorstCluster(Dataset[] clusters) {
        //need to find the cluster with the lowest values
        //clusters will have distinct ranges - any value from a will be lower than any value from b
        //therefore can pick any member arbitrarily
        Double lowest = clusters[0].instance(0).get(0);
        int worstClusterIndex = 0;
        for (int i = 1; i < clusters.length; ++i) {
            Double instance = clusters[i].instance(0).get(0);
            if (instance < lowest) {
                worstClusterIndex = i;
                lowest = instance;
            }
        }
        return worstClusterIndex;
    }

    /**
     * This method should read in .fcinfo.csv files, add the contributions
     * from the file into a dataset, perform clustering and then record
     * the clustering information in the original files.
     */
    public static void addClusterColumn(File file, int k) {
        //should be a .fcinfo.csv file
        System.out.println(file.getAbsolutePath());

        final BufferedReader br;
        Dataset data = new DefaultDataset();
        List<Integer> ids = new ArrayList<>();

        try {
            br = new BufferedReader(new FileReader(file));
            String sCurrentLine;
            sCurrentLine = br.readLine(); //skip headers
            if (sCurrentLine.endsWith("Cluster")) {
                if (sCurrentLine.endsWith("Cluster,Cluster")) {

                    //messed up, have at least two Cluster columns
                    //need to close buffered reader before writing to file, won't need it anymore anyway
                    br.close();
                    try {
                        List<String> newLines = new ArrayList<>();
                        for (String line : Files.readAllLines(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8)) {
                            if (line.contains("BB,Fitness,Contribution,VotingWeights,NormFit,Size")) {
                                //replace whatever was there before
                                newLines.add("BB,Fitness,Contribution,VotingWeights,NormFit,Size,Cluster");
                            } else {
                                newLines.add(line);
                            }
                        }
                        Files.write(Paths.get(file.getAbsolutePath()), newLines, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                //this file has already had cluster information included, no need to process
                return;
            }

            while ((sCurrentLine = br.readLine()) != null) {
                String[] components = sCurrentLine.split(String.valueOf(','));
                double[] contribution = new double[]{ Double.parseDouble(components[2]) };
                if (contribution[0] > 0.0) {
                    DenseInstance i = new DenseInstance(contribution);
                    ids.add(i.getID());
                    data.add(i);
                } else {
                    //so we can keep track of which contribution belongs to which row
                    ids.add(-1);
                }
            }
            br.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //contributions have all been read in, now can cluster them
        Dataset[] clusters = clusterContributions(data,k);
        int worstClusterIndex = findWorstCluster(clusters);

        //now can write back to files - if cluster = worstCluster - write 0, otherwise write 1
        List<String> newLines = new ArrayList<>();
        int count = 0;
        try {
            for (String line : Files.readAllLines(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8)) {
                if (line.contains("BB,Fitness,Contribution,VotingWeights,NormFit,Size")) {
                    newLines.add(line+",Cluster");
                } else {
                    int id = ids.get(count);
                    if (id != -1) {
                        //was included in cluster
                        //need to check which cluster it was in
                        boolean inWorstCluster = false;
                        //was clustered, now we just need to know whether it was in bottom cluster or not
                        Dataset worstCluster = clusters[worstClusterIndex];
                        for (int j = 0; j < worstCluster.size() && !inWorstCluster; ++j) {
                            if (worstCluster.get(j).getID() == id) {
                                inWorstCluster = true;
                                newLines.add(line+",0");
                            }
                        }
                        if (!inWorstCluster) {
                            //should get to vote!
                            newLines.add(line+",1");
                        }
                    } else {
                        //not a valid contribution
                        newLines.add(line+",0");
                    }
                    count++;
                }
            }
            Files.write(Paths.get(file.getAbsolutePath()), newLines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //This method should have two components.
    //The first component should simply remove all BBs which
    //have two of the same terminals eg PT max PT - meaningless
    //The second component should remove all BBs which compare
    //two terminals of separate dimensions.
    public static List<GPNode> prefilterBBs(List<GPNode> BBs) {
        List<GPNode> dupTerminalsRemovedBBs = filterDuplicateTerminalBBs(BBs);
        List<GPNode> conflictingDimensionsRemovedBBs = filterConflictingDimensionBBs(dupTerminalsRemovedBBs);
        return conflictingDimensionsRemovedBBs;
    }

    //Should remove all subtrees with duplicate terminals being used.
    private static List<GPNode> filterDuplicateTerminalBBs(List<GPNode> BBs) {
        List<GPNode> filteredBBs = new ArrayList<>();
        for (GPNode node: BBs) {
            //expecting trees of depth 2, so will just have two children which are terminals
            if (node.depth() != 2) {
                //no point continuing, the code below will not operate as expected
                System.out.println("Assumption of all depth 2 subtrees not met.");
                return null;
            }

            GPNode[] children = node.children;
            if (!children[0].equals(children[1])) {
                //different terminals
                filteredBBs.add(node);
            } else {
                //System.out.println("Removing subtree "+node.makeCTree(false,true,true));
            }
        }
        System.out.println("Removed "+(BBs.size()-filteredBBs.size())+" subtrees with duplicate terminals.");
        return filteredBBs;
    }

    //Should remove all subtrees which +, -, max & min two terminals which
    //are from different dimensions. These dimensions are based on
    private static List<GPNode> filterConflictingDimensionBBs(List<GPNode> BBs) {
        List<GPNode> filteredBBs = new ArrayList<>();
        for (GPNode node: BBs) {
            if (!(node instanceof Mul || node instanceof Div)) {
                //operator should be add, sub, max or min
                //dimensions of attributes should therefore be equal for this to make sense
                //expecting subtrees of depth 2, so children will be terminals
                JobShopAttribute js1 = ((AttributeGPNode) node.children[0]).getJobShopAttribute();
                JobShopAttribute js2 = ((AttributeGPNode) node.children[1]).getJobShopAttribute();
                if (js1.dimension() == js2.dimension()) {
                    filteredBBs.add(node);
                } else {
                    //System.out.println("Removing subtree with conflicting dimensions: "
                            //+node.makeCTree(false,true,true));
                }
            } else {
                filteredBBs.add(node);
            }
        }
        System.out.println("Removed "+(BBs.size()-filteredBBs.size())+" subtrees with conflicting dimensions.");
        return filteredBBs;
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
//
//        for (String subtree: bbCounts.keySet()) {
//            System.out.println("Subtree "+subtree+
//                    " appeared "+bbCounts.get(subtree)+" times.");
//        }
        System.out.println(bbs.size()+" subtrees found.");
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

    private static String initPath(EvolutionState state, boolean preFiltering) {
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
        SimpleEvaluationModel evaluationModel = (SimpleEvaluationModel) ((RuleOptimizationProblem)
                state.evaluator.p_problem).getEvaluationModel();
        Objective objective = evaluationModel.getObjectives().get(0); //expecting only one objective
        DynamicSimulation d = (DynamicSimulation) evaluationModel.getSchedulingSet().getSimulations().get(0);
        double utilLevel = d.getUtilLevel();
        String path = "out/subtree_contributions/";
        if (preFiltering) {
            path = "out/subtree_contributions_filtered/";
        }
        return path+utilLevel+"-"+objective.getName().toLowerCase()+"/";
    }
}
