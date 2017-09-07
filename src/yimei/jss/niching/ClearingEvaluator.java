package yimei.jss.niching;

import ec.EvolutionState;
import ec.simple.SimpleEvaluator;
import ec.util.Parameter;

/**
 * The evaluator with population clearing.
 * The evaluator is used for niching.
 *
 * Created by YiMei on 3/10/16.
 */
public class ClearingEvaluator extends SimpleEvaluator {

    public static final String P_RADIUS = "radius";
    public static final String P_CAPACITY = "capacity";

    protected boolean clear = true;

    protected double radius;
    protected int capacity;

    protected PhenoCharacterisation phenoCharacterisation;

    public double getRadius() {
        return radius;
    }

    public int getCapacity() {
        return capacity;
    }

    public PhenoCharacterisation getPhenoCharacterisation() {
        return phenoCharacterisation;
    }

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        radius = state.parameters.getDoubleWithDefault(
                base.push(P_RADIUS), null, 0.0);
        capacity = state.parameters.getIntWithDefault(
                base.push(P_CAPACITY), null, 1);
        String filePath = state.parameters.getString(new Parameter("filePath"), null);
        if (filePath == null) {
            //dynamic simulation
            phenoCharacterisation =
                    PhenoCharacterisation.defaultPhenoCharacterisation();
        } else {
            //static simulation
            phenoCharacterisation =
                    PhenoCharacterisation.defaultPhenoCharacterisation(filePath);
        }
    }

    @Override
    public void evaluatePopulation(final EvolutionState state) {
        super.evaluatePopulation(state);

        if (clear) {
            Clearing.clearPopulation(state, radius, capacity,
                    phenoCharacterisation);
        }
    }

    public void setClear(boolean clear) {
        this.clear = clear;
    }
}
