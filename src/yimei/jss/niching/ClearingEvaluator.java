package yimei.jss.niching;

import ec.EvolutionState;
import ec.simple.SimpleEvaluator;
import ec.util.Parameter;
import yimei.jss.ruleevaluation.AbstractEvaluationModel;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;
import yimei.jss.surrogate.Surrogate;

/**
 * The evaluator with population clearing.
 * The evaluator is used for niching.
 *
 * Created by YiMei on 3/10/16.
 */
public class ClearingEvaluator extends SimpleEvaluator implements ClearableEvaluator {

    public static final String P_RADIUS = "radius";
    public static final String P_CAPACITY = "capacity";
    public static final String P_CLEAR = "clearing";

    protected boolean clear;
    protected double radius;
    protected int capacity;

    protected PhenoCharacterisation[] phenoCharacterisation;

    @Override
    public double getRadius() {
        return radius;
    }

    @Override
    public PhenoCharacterisation getPhenoCharacterisation(int index) {
        return phenoCharacterisation[index];
    }

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        radius = state.parameters.getDoubleWithDefault(
                base.push(P_RADIUS), null, 0.0);
        capacity = state.parameters.getIntWithDefault(
                base.push(P_CAPACITY), null, 1);
        clear = state.parameters.getBoolean(
                base.push(P_CLEAR), null, true);

        String filePath = state.parameters.getString(new Parameter("filePath"), null);
        //It's a little tricky to know whether we have 1 or 2 populations here, so we will assume
        //2 for the purpose of the phenoCharacterisation, and ignore the second object if only
        //1 is used
        phenoCharacterisation = new PhenoCharacterisation[]{
                SequencingPhenoCharacterisation.defaultPhenoCharacterisation(filePath),
                RoutingPhenoCharacterisation.defaultPhenoCharacterisation(filePath)
        };
    }

    @Override
    public void evaluatePopulation(final EvolutionState state) {
        super.evaluatePopulation(state);
        if (clear) {
            Clearing.clearPopulation(state, radius, capacity,
                    phenoCharacterisation);
        }
    }

    @Override
    public void setClear(boolean clear) {
        this.clear = clear;
    }
}
