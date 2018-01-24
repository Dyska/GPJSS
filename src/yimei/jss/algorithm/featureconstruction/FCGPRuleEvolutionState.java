package yimei.jss.algorithm.featureconstruction;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.simple.SimpleStatistics;
import ec.util.Checkpoint;
import ec.util.Parameter;
import yimei.jss.feature.FeatureIgnorable;
import yimei.jss.feature.FeatureUtil;
import yimei.jss.feature.ignore.Ignorer;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.gp.TerminalsChangable;
import yimei.jss.gp.terminal.BuildingBlock;
import yimei.jss.niching.ClearingEvaluator;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;
import yimei.jss.surrogate.Surrogate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Created by Daniel Yska, adapted from FSGPRuleEvolutionState class.
 */
public class FCGPRuleEvolutionState extends GPRuleEvolutionState implements TerminalsChangable, FeatureIgnorable {

    public static final String P_IGNORER = "ignorer";
    public static final String P_PRE_GENERATIONS = "pre-generations";
    public static final String P_POP_ADAPT_FRAC_ELITES = "pop-adapt-frac-elites";
    public static final String P_POP_ADAPT_FRAC_ADAPTED = "pop-adapt-frac-adapted";
    public static final String P_DO_ADAPT = "feature-construction-adapt-population";

    private Ignorer ignorer;
    public int preGenerations;
    private double fracElites;
    private double fracAdapted;

    private double fitUB = Double.NEGATIVE_INFINITY;
    private double fitLB = Double.POSITIVE_INFINITY;
    private double worstFitnesses[] = null;
    private double bestFitnesses[] = null;
    private boolean doAdapt;

    @Override
    public Ignorer getIgnorer() {
        return ignorer;
    }

    @Override
    public void setIgnorer(Ignorer ignorer) {
        this.ignorer = ignorer;
    }

    @Override
    public void setup(EvolutionState state, Parameter base) {
        super.setup(state, base);

        ignorer = (Ignorer)(state.parameters.getInstanceForParameter(
                new Parameter(P_IGNORER), null, Ignorer.class));
        preGenerations = state.parameters.getIntWithDefault(
                new Parameter(P_PRE_GENERATIONS), null, -1);
        fracElites = state.parameters.getDoubleWithDefault(
                new Parameter(P_POP_ADAPT_FRAC_ELITES), null, 0.0);
        fracAdapted = state.parameters.getDoubleWithDefault(
                new Parameter(P_POP_ADAPT_FRAC_ADAPTED), null, 1.0);
        doAdapt = state.parameters.getBoolean(new Parameter(P_DO_ADAPT),
                null, true);
    }

    @Override
    public int evolve() {
        if (generation == preGenerations) {
            evaluator.evaluatePopulation(this);

            for (int i = 0; i < population.subpops.length; ++i) {
                Individual[] individuals = population.subpops[i].individuals;

                List<GPIndividual> selIndis =
                        FeatureUtil.selectDiverseIndis(this, individuals, i, 30);

                //these fitUB and fitLB values are meant to be ClearingKozaFitness values
                //this returns fitness() as 1/(1+fitness()), so must change values
                //this list of selIndis is currently sorted by fitness(), which means it is
                //in the reverse order - so must get last member of list

                fitUB = 1/(1+bestFitnesses[i]);
                fitLB = 1/(1+worstFitnesses[i]);

                System.out.println("");
                System.out.println("Feature construction analysis being performed for "
                        +FeatureUtil.ruleTypes[i]+" population.");

                String bbSelectionStrategy = parameters.getString(new Parameter("bbSelectionStrategy"),null);
                String contributionSelectionStrategy = parameters.getString(
                        new Parameter("contributionSelectionStrategy"),null);

                boolean preFiltering = true;
                GPNode[] features = FeatureUtil.featureConstruction(this, selIndis,
                                FeatureUtil.ruleTypes[i], fitUB, fitLB,
                        preFiltering, contributionSelectionStrategy,
                        bbSelectionStrategy);

                if (doAdapt) {
                    addTerminals(features,i);

                    //stop clearing individuals
                    ((ClearingEvaluator)evaluator).setClear(false);
                    //begin using full scheduling set, not the surrogate set
                    ((Surrogate)((RuleOptimizationProblem) evaluator.p_problem)
                            .getEvaluationModel()).useOriginal();

                    //now need to empty population and begin from scratch
                    if (statistics instanceof SimpleStatistics) {
                        ((SimpleStatistics) statistics).best_of_run[i] = null;
                    }
                    population.clear();
                    output.message("Initializing Generation 0");
                    population = initializer.initialPopulation(this, 0); // unthreaded
                }
            }
        }
        if (generation > 0) {
            output.message("Generation " + generation%preGenerations);
        }

        // EVALUATION
        statistics.preEvaluationStatistics(this);
        evaluator.evaluatePopulation(this);
        statistics.postEvaluationStatistics(this);

        // SHOULD WE QUIT?
        if (evaluator.runComplete(this) && quitOnRunComplete)
        {
            output.message("Found Ideal Individual");
            return R_SUCCESS;
        }

        // SHOULD WE QUIT?
        if (generation == numGenerations-1)
        {
            return R_FAILURE;
        }

        // PRE-BREEDING EXCHANGING
        statistics.prePreBreedingExchangeStatistics(this);
        population = exchanger.preBreedingExchangePopulation(this);
        statistics.postPreBreedingExchangeStatistics(this);

        String exchangerWantsToShutdown = exchanger.runComplete(this);
        if (exchangerWantsToShutdown!=null)
        {
            output.message(exchangerWantsToShutdown);
	        /*
	         * Don't really know what to return here.  The only place I could
	         * find where runComplete ever returns non-null is
	         * IslandExchange.  However, that can return non-null whether or
	         * not the ideal individual was found (for example, if there was
	         * a communication error with the server).
	         *
	         * Since the original version of this code didn't care, and the
	         * result was initialized to R_SUCCESS before the while loop, I'm
	         * just going to return R_SUCCESS here.
	         */

            return R_SUCCESS;
        }

        // BREEDING
        statistics.preBreedingStatistics(this);

        population = breeder.breedPopulation(this);

        // POST-BREEDING EXCHANGING
        statistics.postBreedingStatistics(this);

        // POST-BREEDING EXCHANGING
        statistics.prePostBreedingExchangeStatistics(this);
        population = exchanger.postBreedingExchangePopulation(this);
        statistics.postPostBreedingExchangeStatistics(this);

        // Generate new instances if needed
        RuleOptimizationProblem problem = (RuleOptimizationProblem) evaluator.p_problem;
        if (problem.getEvaluationModel().isRotatable()) {
            problem.rotateEvaluationModel();
        }

        // INCREMENT GENERATION AND CHECKPOINT
        generation++;
        if (checkpoint && generation%checkpointModulo == 0)
        {
            output.message("Checkpointing");
            statistics.preCheckpointStatistics(this);
            Checkpoint.setCheckpoint(this);
            statistics.postCheckpointStatistics(this);
        }

        return R_NOTDONE;
    }

    @Override
    public void adaptPopulation(int subPopNum) {
        FeatureUtil.adaptPopulationThreeParts(this, fracElites, fracAdapted, subPopNum);
    }

    public double[] getWorstFitnesses() {
        return worstFitnesses;
    }

    public void setWorstFitnesses(double[] worstFitnesses) {
        this.worstFitnesses = worstFitnesses;
    }

    public double[] getBestFitnesses() {
        return bestFitnesses;
    }

    public void setBestFitnesses(double[] bestFitnesses) {
        this.bestFitnesses = bestFitnesses;
    }
}
