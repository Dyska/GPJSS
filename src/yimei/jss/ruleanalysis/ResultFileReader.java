package yimei.jss.ruleanalysis;

import ec.Fitness;
import ec.Individual;
import ec.gp.koza.KozaFitness;
import ec.multiobjective.MultiObjectiveFitness;
import org.apache.commons.math3.analysis.function.Abs;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import yimei.jss.rule.AbstractRule;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.util.lisp.LispSimplifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The reader of the result file.
 *
 * Created by YiMei on 12/10/16.
 */
public class ResultFileReader {

    public static TestResult readTestResultFromFile(File file,
                                                    RuleType ruleType,
                                                    boolean isMultiObjective,
                                                    int numPopulations) {
        TestResult result = new TestResult();

        String line;
        Fitness[] fitnesses = new Fitness[numPopulations];

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            while (!(line = br.readLine()).equals("Best Individual of Run:")) {
                if (line.startsWith("Generation")) {
                    br.readLine(); //Best individual:

                    GPRule[] rules = new GPRule[numPopulations];
                    GPRule[] collaborators = new GPRule[numPopulations];
                    for (int i = 0; i < numPopulations; ++i) {
                        br.readLine(); //Subpopulation i:
                        br.readLine(); //Evaluated: true
                        line = br.readLine(); //this will be either a fitness or collaborator rule
                        if (numPopulations == 2) {
                            //collaborator rule
                            line = LispSimplifier.simplifyExpression(line);

                            if (i == 0) {
                                collaborators[i] = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);
                            } else {
                                collaborators[i] = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);
                            }

                            line = br.readLine(); //read in fitness on following line
                        }
                        fitnesses[i] = readFitnessFromLine(line, isMultiObjective);

                        if (numPopulations == 2) {
                            br.readLine(); //Collaborator 1 or 0:
                        }

                        br.readLine(); //Tree 0:
                        String expression = br.readLine();

                        expression = LispSimplifier.simplifyExpression(expression);

                        if (i == 0) {
                            //subpop 0 is sequencing rules
                            rules[i] = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING,expression);
                        } else {
                            //subpop 1 is routing rules
                            rules[i] = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING,expression);
                        }
                    }

                    Fitness fitness = fitnesses[0];
                    GPRule[] bestRules = rules; //will just be single rule for 1 subpop
                    if (numPopulations == 2) {
                        //need to decide which subpop yielded better fitnesses
                        if (fitness.fitness() < fitnesses[1].fitness()) {
                            //subpop 0 was best
                            bestRules[0] = rules[0];
                            bestRules[1] = collaborators[0];
                        } else {
                            //subpop 1 was best
                            fitness = fitnesses[1];
                            bestRules[0] = rules[1];
                            bestRules[1] = collaborators[1];
                        }
                    }

                    result.addGenerationalRules(bestRules);
                    result.addGenerationalTrainFitness(fitness);
                    result.addGenerationalValidationFitnesses((Fitness) fitness.clone());
                    result.addGenerationalTestFitnesses((Fitness) fitness.clone());
                }
            }

            GPRule[] rules = new GPRule[numPopulations];
            GPRule[] collaborators = new GPRule[numPopulations];
            for (int i = 0; i < numPopulations; ++i) {
                br.readLine(); //Subpopulation i:
                br.readLine(); //Evaluated: true
                line = br.readLine(); //this will be either a fitness or collaborator rule
                if (numPopulations == 2) {
                    //collaborator rule
                    line = LispSimplifier.simplifyExpression(line);
                    if (i == 0) {
                        //subpop 0 is sequencing rules, so collab[0] is a routing rule
                        collaborators[i] = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);
                    } else {
                        //subpop 1 is routing rules, so collab[1] is a sequencing rule
                        collaborators[i] = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);
                    }

                    line = br.readLine(); //read in fitness on following line
                }
                fitnesses[i] = readFitnessFromLine(line, isMultiObjective);

                if (numPopulations == 2) {
                    br.readLine(); //Collaborator 1 or 0:
                }

                br.readLine(); //Tree 0:
                String expression = br.readLine();

                expression = LispSimplifier.simplifyExpression(expression);

                if (i == 0) {
                    //subpop 0 is sequencing rules
                    rules[i] = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING,expression);
                } else {
                    //subpop 1 is routing rules
                    rules[i] = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING,expression);
                }
            }

            Fitness fitness = fitnesses[0];
            GPRule[] bestRules = rules; //will just be single rule for 1 subpop
            if (numPopulations == 2) {
                //need to decide which subpop yielded better fitnesses
                if (fitness.fitness() < fitnesses[1].fitness()) {
                    //subpop 0 was best
                    bestRules[0] = rules[0];
                    bestRules[1] = collaborators[0];
                } else {
                    //subpop 1 was best
                    fitness = fitnesses[1];
                    bestRules[0] = rules[1];
                    bestRules[1] = collaborators[1];
                }
            }

            result.setBestRules(bestRules);
            result.setBestTrainingFitness(fitness);

        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    private static Fitness readFitnessFromLine(String line, boolean isMultiobjective) {
        if (isMultiobjective) {
            // TODO read multi-objective fitness line
            String[] spaceSegments = line.split("\\s+");
            String[] equation = spaceSegments[1].split("=");
            double fitness = Double.valueOf(equation[1]);
            KozaFitness f = new KozaFitness();
            f.setStandardizedFitness(null, fitness);

            return f;
        }
        else {
            String[] spaceSegments = line.split("\\s+");
            String[] fitVec = spaceSegments[1].split("\\[|\\]");
            double fitness = Double.valueOf(fitVec[1]);
            MultiObjectiveFitness f = new MultiObjectiveFitness();
            f.objectives = new double[1];
            f.objectives[0] = fitness;

            return f;
        }
    }

    public static DescriptiveStatistics readTimeFromFile(File file) {
        DescriptiveStatistics generationalTimeStat = new DescriptiveStatistics();

        String line;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            br.readLine();
            while(true) {
                line = br.readLine();

                if (line == null)
                    break;

                String[] commaSegments = line.split(",");
                generationalTimeStat.addValue(Double.valueOf(commaSegments[1]));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return generationalTimeStat;
    }

    public static List<String> readLispExpressionFromFile(File file,
                                                          RuleType ruleType,
                                                          boolean isMultiObjective) {
        List<String> expressions = new ArrayList<>();

        String line;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            while (!(line = br.readLine()).equals("Best Individual of Run:")) {
                if (line.startsWith("Generation")) {
                    br.readLine();
                    br.readLine();
                    br.readLine();
                    br.readLine();
                    br.readLine();
                    String expression = br.readLine();

                    expression = LispSimplifier.simplifyExpression(expression);
                    expressions.add(expression);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return expressions;
    }
}
