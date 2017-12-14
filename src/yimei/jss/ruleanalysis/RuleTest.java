package yimei.jss.ruleanalysis;

import ec.gp.GPNode;
import ec.multiobjective.MultiObjectiveFitness;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.rule.AbstractRule;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.rule.workcenter.basic.WIQ;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RuleTest {

    public static final long simSeed = 968356;

	protected String trainPath;
    protected RuleType ruleType;
    protected int numRuns;
    protected String testScenario;
    protected String testSetName;
    protected List<Objective> objectives; // The objectives to test.
    protected int numPopulations;

    public RuleTest(String trainPath, RuleType ruleType, int numRuns,
                    String testScenario, String testSetName,
                    List<Objective> objectives, int numPopulations) {
        this.trainPath = trainPath;
        this.ruleType = ruleType;
        this.numRuns = numRuns;
        this.testScenario = testScenario;
        this.testSetName = testSetName;
        this.objectives = objectives;
        this.numPopulations = numPopulations;
    }

    public RuleTest(String trainPath, RuleType ruleType, int numRuns,
                    String testScenario, String testSetName, int numPopulations) {
        this(trainPath, ruleType, numRuns, testScenario, testSetName, new ArrayList<>(), numPopulations);
    }

    public String getTrainPath() {
        return trainPath;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public int getNumRuns() {
        return numRuns;
    }

    public int getNumPopulations() { return numPopulations; }

    public String getTestScenario() {
        return testScenario;
    }

    public List<Objective> getObjectives() {
        return objectives;
    }

    public void setObjectives(List<Objective> objectives) {
		this.objectives = objectives;
	}

	public void addObjective(Objective objective) {
		this.objectives.add(objective);
	}

	public void addObjective(String objective) {
		addObjective(Objective.get(objective));
	}

	public SchedulingSet generateTestSet() {
        return SchedulingSet.generateSet(simSeed, testScenario,
                testSetName, objectives, 50);
    }

	public void writeToCSV() {
        SchedulingSet testSet = generateTestSet();

        File targetPath = new File(trainPath + "test");
        if (!targetPath.exists()) {
            targetPath.mkdirs();
        }

        File csvFile = new File(targetPath + "/" + testSetName + ".csv");

        List<TestResult> testResults = new ArrayList<>();

        for (int i = 0; i < numRuns; i++) {
            File sourceFile = new File(trainPath + "job." + i + ".out.stat");

            TestResult result = TestResult.readFromFile(sourceFile, ruleType, numPopulations);

            File timeFile = new File(trainPath + "job." + i + ".time.csv");
            result.setGenerationalTimeStat(ResultFileReader.readTimeFromFile(timeFile));

//            long start = System.currentTimeMillis();
//
////            result.validate(objectives);
//
//            for (int j = 0; j < result.getGenerationalRules().size(); j++) {
//                AbstractRule[] generationalRules = result.getGenerationalRules(j);
//                if (numPopulations == 2) {
//                    generationalRules[0].calcFitness(
//                            result.getGenerationalTestFitness(j), null,
//                            testSet, generationalRules[1], objectives);
//                } else {
//                    AbstractRule routingRule = new WIQ(yimei.jss.rule.RuleType.ROUTING);
//                    generationalRules[0].calcFitness(result.getGenerationalTestFitness(j), null,
//                            testSet, routingRule, objectives);
//                }
//
//                System.out.println("Generation " + j + ": test fitness = " +
//                        result.getGenerationalTestFitness(j).fitness());
//            }
//
//            long finish = System.currentTimeMillis();
//            long duration = finish - start;
//            System.out.println("Duration = " + duration + " ms.");

            testResults.add(result);
        }

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile.getAbsoluteFile()));
            writer.write("Run,Generation,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
                    "RoutRuleUniqueTerminals,Obj,TrainFitness,TestFitness");
            writer.newLine();
            for (int i = 0; i < numRuns; i++) {
                TestResult result = testResults.get(i);

                for (int j = 0; j < result.getGenerationalRules().size(); j++) {

                    MultiObjectiveFitness trainFit =
                            (MultiObjectiveFitness)result.getGenerationalTrainFitness(j);
                    MultiObjectiveFitness testFit =
                            (MultiObjectiveFitness)result.getGenerationalTestFitness(j);
                    GPRule[] rules = (GPRule[]) result.getGenerationalRules(j);
                    GPRule seqRule = null;
                    GPRule routRule = null;
                    if (numPopulations == 2) {
                        if (rules[0].getType() == yimei.jss.rule.RuleType.SEQUENCING) {
                            seqRule = rules[0];
                            routRule = rules[1];
                        } else {
                            seqRule = rules[1];
                            routRule = rules[0];
                        }
                    } else {
                        seqRule = rules[0];
                    }


                    UniqueTerminalsGatherer gatherer = new UniqueTerminalsGatherer();
                    int numUniqueTerminalsSeq = seqRule.getGPTree().child.numNodes(gatherer);
                    int seqRuleSize = seqRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);

                    int numUniqueTerminalsRout = 0;
                    int routRuleSize = 0;
                    if (numPopulations == 2) {
                        gatherer = new UniqueTerminalsGatherer();
                        numUniqueTerminalsRout = routRule.getGPTree().child.numNodes(gatherer);
                        routRuleSize = routRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);
                    }

                    if (objectives.size() == 1) {
                        writer.write(i + "," + j + "," +
                                seqRuleSize + "," +
                                numUniqueTerminalsSeq + "," +
                                routRuleSize +"," +
                                numUniqueTerminalsRout +",0," +
                                trainFit.fitness() + "," +
                                testFit.fitness());
                        writer.newLine();
                    }
                    else {
//                        writer.write(i + "," + j + "," +
//                                rule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL) + "," +
//                                numUniqueTerminals + ",");

                        for (int k = 0; k < objectives.size(); k++) {
                            writer.write(k + "," +
                                    trainFit.getObjective(k) + "," +
                                    testFit.getObjective(k) + ",");
                        }
                        writer.newLine();
                    }
                }
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Call this main method with several parameters
     *
     * /home/yskadani/eclipse-workspace/GPJSS/grid_results/dynamic/raw/ccgp/ccgp-0.85-max-flowtime/
     * simple-rule
     * 30
     * dynamic-job-shop
     * missing-0.85-4
     * 2
     * 1
     * max-flowtime
     */
	public static void main(String[] args) {
		int idx = 0;
		String trainPath = args[idx];
        idx ++;
        RuleType ruleType = RuleType.get(args[idx]);
		idx ++;
        int numRuns = Integer.valueOf(args[idx]);
        idx ++;
        String testScenario = args[idx];
        idx ++;
        String testSetName = args[idx];
        idx ++;
        int numPopulations = Integer.valueOf(args[idx]);
        idx ++;
		int numObjectives = Integer.valueOf(args[idx]);
		idx ++;
		RuleTest ruleTest = new RuleTest(trainPath, ruleType, numRuns, testScenario, testSetName, numPopulations);
		for (int i = 0; i < numObjectives; i++) {
			ruleTest.addObjective(args[idx]);
			idx ++;
		}

		ruleTest.writeToCSV();
	}
}
