package yimei.jss;

import ec.multiobjective.MultiObjectiveFitness;
import yimei.jss.jobshop.*;
import yimei.jss.rule.AbstractRule;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.basic.*;
import yimei.jss.rule.operation.composite.*;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.rule.operation.weighted.*;
import yimei.jss.rule.workcenter.basic.*;
import yimei.jss.simulation.Simulation;
import yimei.jss.simulation.StaticSimulation;
import yimei.jss.simulation.event.AbstractEvent;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static yimei.jss.ruleevaluation.RuleComparison.EvaluateOutput;

/**
 * The main program of job shop scheduling, for basic testing.
 *
 * Created by YiMei on 27/09/16.
 */
public class FJSSMain {

    private static void calculateFitness(boolean doStore, String fileName,
                                        List<Objective> objectives,
                                        List<AbstractRule> sequencingRules,
                                        List<AbstractRule> routingRules) {
        if (sequencingRules.isEmpty() ||
                routingRules.isEmpty() ||
                objectives.isEmpty()) {
            return;
        }
        BufferedWriter writer = null;

        if (doStore) {
            writer = createFileWriter(fileName);
        }

        for (AbstractRule routingRule: routingRules) {
            MultiObjectiveFitness fitness = new MultiObjectiveFitness();
            fitness.objectives = new double[1];
            fitness.maxObjective = new double[1];
            fitness.maxObjective[0] = 1.0;
            fitness.minObjective = new double[1];
            fitness.maximize = new boolean[1];

            FlexibleStaticInstance instance = FlexibleStaticInstance.readFromAbsPath(fileName);
//            System.out.println("Num jobs "+instance.getNumJobs()+" num work centers: "+instance.getNumWorkCenters());
//            double numOperations = 0;
//            double numOptions = 0;
//            for (int i = 0; i < instance.getNumJobs(); ++i) {
//                FlexibleStaticInstance.JobInformation job = instance.getJobInformations().get(i);
//                numOperations += job.getNumOps();
//                for (int j = 0; j < job.getOperations().size(); ++j) {
//                    numOptions += job.getOperations().get(j).getOperationOptions().size();
//                }
//            }
//            System.out.println("Num operations per job "+numOperations/instance.getNumJobs());
//            System.out.println("Num options per operation "+numOptions/numOperations);

            List<Integer> replications = new ArrayList<>();
            replications.add(1);

            for (AbstractRule sequencingRule: sequencingRules) {
                List<Simulation> simulations = new ArrayList<>();
                simulations.add(new StaticSimulation(sequencingRule, routingRule, instance));
                SchedulingSet set = new SchedulingSet(simulations, replications, objectives);
                String fitnessResult = calcFitness(doStore, sequencingRule, routingRule, fitness, set, objectives);

                //store fitness result with sequencing rule and routing rule
                if (doStore) {
                    try {
                        writer.write(String.format("RR:%s SR:%s - %s", getRuleName(routingRule),
                                getRuleName(sequencingRule), fitnessResult));
                        writer.newLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        if (doStore) {
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static BufferedWriter createFileWriter(String filePath) {
        //replace /data/ with /out/test/
        String[] pathComponents = filePath.split("/data/");
        //to avoid heavy nesting in output, replace nested directory with filenames

        String output = pathComponents[0] + "/out/rule_comparisons/" + pathComponents[1].replace("/","-");

        File file = new File(output);
        try {
            //create file in this location if one does not exist
            if (!file.exists()) {
                file.createNewFile();
            }
            return new BufferedWriter(new FileWriter(file));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String getRuleName(AbstractRule rule) {
        if (rule instanceof GPRule) {
            //should return full rule, not just "GPRule"
            return ((GPRule) rule).getLispString();
        }
        return rule.getName();
    }

    private static String calcFitness(boolean doStore, AbstractRule sequencingRule,
                                      AbstractRule routingRule,
                                      MultiObjectiveFitness fitness,
                                      SchedulingSet set,
                                      List<Objective> objectives) {
        String output = "";

        sequencingRule.calcFitness(fitness, null, set, routingRule, objectives);

        if (!doStore) {
            double benchmarkMakeSpan = set.getObjectiveLowerBound(0,0);
            output += "Benchmark makespan: "+benchmarkMakeSpan+"\n";
            double ruleMakespan = fitness.fitness()*benchmarkMakeSpan;
            output += "Rule makespan: "+ruleMakespan+"\n";
        }

        output += "Fitness = " + fitness.fitnessToStringForHumans();
        //System.out.println(output);
        return output;
    }

    public static List<String> getFileNames(List<String> fileNames, Path dir, String ext) {
        if (dir.toAbsolutePath().toString().endsWith(ext)) {
            //have been passed a file
            fileNames.add(dir.toString());
        } else {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path path : stream) {
                    if (path.toFile().isDirectory()) {
                        getFileNames(fileNames, path, ".fjs");
                    } else {
                        if (path.toString().endsWith(ext)) {
                            fileNames.add(path.toString());
                        }
                    }
                }
            }
            catch(IOException e) {
                e.printStackTrace();
            }
        }
        return fileNames;
    }

    public static void main(String[] args) {
        //path may be a directory path or a file path
        //example file path: Brandimarte_Data/Text/Mk08.fjs
        String path = "";
        if (args.length > 0) {
            //allow more specific folder or file paths to be used
            path = args[0];
        }
        path = (new File("")).getAbsolutePath() + "/data/FJSS/" + path;

        boolean doStore = true;
        List<Objective> objectives = new ArrayList<>();
        List<AbstractRule> sequencingRules = new ArrayList();
        List<AbstractRule> routingRules = new ArrayList();

        objectives.add(Objective.MAKESPAN);

        //routingRules.add(GPRule.readFromLispExpression(RuleType.ROUTING," (+ (max WIQ DD) (- (/ AT PT) (min SL NOR)))"));

        //routingRules.add(new SBT(RuleType.ROUTING));
        //sequencingRules.add(GPRule.readFromLispExpression(RuleType.SEQUENCING," (+ (min (min (max (min WIQ t) (max NOR SL)) MRT) (max DD WKR)) (* (+ SL SL) (- WIQ PT)))"));
        //sequencingRules.add(GPRule.readFromLispExpression(RuleType.SEQUENCING," (/ WKR rDD)"));
//        routingRules.add(GPRule.readFromLispExpression(RuleType.ROUTING," (max (max NINQ PT) (max (- (/ (min t NINQ)" +
//                " (max AT W)) (min (max NOR FDD) (* MRT (- SL W)))) AT))"));

        sequencingRules.add(new AVPRO(RuleType.SEQUENCING));
        sequencingRules.add(new CR(RuleType.SEQUENCING));
        sequencingRules.add(new EDD(RuleType.SEQUENCING));
        sequencingRules.add(new FCFS(RuleType.SEQUENCING));
        sequencingRules.add(new FDD(RuleType.SEQUENCING));
        sequencingRules.add(new LCFS(RuleType.SEQUENCING));
        sequencingRules.add(new LPT(RuleType.SEQUENCING));
        sequencingRules.add(new LWKR(RuleType.SEQUENCING));
        sequencingRules.add(new MOPNR(RuleType.SEQUENCING));
        sequencingRules.add(new MWKR(RuleType.SEQUENCING));
        sequencingRules.add(new NPT(RuleType.SEQUENCING));
        sequencingRules.add(new PW(RuleType.SEQUENCING));
        sequencingRules.add(new SL(RuleType.SEQUENCING));
        sequencingRules.add(new Slack(RuleType.SEQUENCING));
        sequencingRules.add(new SPT(RuleType.SEQUENCING));

        sequencingRules.add(new ATC(RuleType.SEQUENCING));
        sequencingRules.add(new COVERT(RuleType.SEQUENCING));
        sequencingRules.add(new CRplusPT(RuleType.SEQUENCING));
        sequencingRules.add(new LWKRplusPT(RuleType.SEQUENCING));
        sequencingRules.add(new OPFSLKperPT(RuleType.SEQUENCING));
        sequencingRules.add(new PTplusPW(RuleType.SEQUENCING));
        sequencingRules.add(new PTplusPWplusFDD(RuleType.SEQUENCING));
        sequencingRules.add(new SlackperOPN(RuleType.SEQUENCING));
        sequencingRules.add(new SlackperRPTplusPT(RuleType.SEQUENCING));

        sequencingRules.add(new WATC(RuleType.SEQUENCING));
        sequencingRules.add(new WCOVERT(RuleType.SEQUENCING));
        sequencingRules.add(new WSPT(RuleType.SEQUENCING));

        //add work center specific rules, as other rules will always give the same values
        routingRules.add(new LBT(RuleType.ROUTING));
        routingRules.add(new LRT(RuleType.ROUTING));
        routingRules.add(new NIQ(RuleType.ROUTING));
        routingRules.add(new SBT(RuleType.ROUTING));
        routingRules.add(new SRT(RuleType.ROUTING));
        routingRules.add(new WIQ(RuleType.ROUTING));

        List<String> fileNames = getFileNames(new ArrayList(), Paths.get(path), ".fjs");
        for (int i = 0; i < fileNames.size(); ++i) {
            String fileName = fileNames.get(i);
            if (!fileName.contains("sdata")) {
                System.out.println("\nInstance "+(i+1)+" - Path: "+fileName);
                calculateFitness(doStore, fileName, objectives, sequencingRules, routingRules);
            }
        }

        EvaluateOutput("/out/rule_comparisons/", "RR");
    }
}