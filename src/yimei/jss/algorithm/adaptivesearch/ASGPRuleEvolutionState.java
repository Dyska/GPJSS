package yimei.jss.algorithm.adaptivesearch;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import ec.util.Checkpoint;
import ec.util.Parameter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.NotImplementedException;
import yimei.jss.algorithm.featureconstruction.FCGPRuleEvolutionState;
import yimei.jss.feature.FeatureIgnorable;
import yimei.jss.feature.FeatureUtil;
import yimei.jss.feature.ignore.Ignorer;
import yimei.jss.gp.TerminalsChangable;
import yimei.jss.niching.ClearableEvaluator;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;
import java.util.List;

/**
 * Created by dyska on 8/02/18.
 */
public class ASGPRuleEvolutionState extends FCGPRuleEvolutionState implements TerminalsChangable, FeatureIgnorable {

    public static final String P_IGNORER = "ignorer";
    public static final String P_BATCH_SIZE = "adaptive-selection-batch-size";
    public static final String P_PRE_ADAPTIVE_GENS = "adaptive-selection-pre-adapative-generations";

    private Ignorer ignorer;
    private int batchSize;
    private int preAdapativeGenerations;

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
    }

    @Override
    public int evolve() {
        if (batchSize == 0) {
            //won't have been initisalised yet, should read this in now
            batchSize = parameters.getInt(new Parameter(P_BATCH_SIZE),null,1);
            preAdapativeGenerations = parameters.getInt(new Parameter(P_PRE_ADAPTIVE_GENS),
                    null,0);
        }

        if (generation%batchSize == 0 && generation >= preAdapativeGenerations) {
            if (evaluator instanceof ClearableEvaluator) { ((ClearableEvaluator)evaluator).setClear(true); }
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

                output.message("");
                output.message("Adaptive search - "+FeatureUtil.ruleTypes[i]+
                        " population, generation "+generation+".");

                boolean doFiltering = true;
                GPNode[] constructedFeatures = FeatureUtil.featureConstruction(this, selIndis,
                        FeatureUtil.ruleTypes[i], fitUB, fitLB, doFiltering);
                GPNode[] selFeatures = FeatureUtil.featureSelection(this, selIndis,
                        FeatureUtil.ruleTypes[i], fitUB, fitLB);

                GPNode[] updatedTerminalSet = ArrayUtils.addAll(constructedFeatures,selFeatures);
                setTerminals(updatedTerminalSet,i);
            }
            //stop clearing individuals again
            if (evaluator instanceof ClearableEvaluator) { ((ClearableEvaluator)evaluator).setClear(false); }
        }

        if (generation > 0) {
            output.message("Generation " + generation);
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
        throw new NotImplementedException("Adapt population not implemented for adaptive search.");
    }
}
