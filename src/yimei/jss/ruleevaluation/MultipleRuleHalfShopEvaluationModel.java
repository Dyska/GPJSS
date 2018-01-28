package yimei.jss.ruleevaluation;

import ec.EvolutionState;
import ec.Fitness;
import ec.multiobjective.MultiObjectiveFitness;
import ec.util.Parameter;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.rule.AbstractRule;
import yimei.jss.surrogate.Surrogate;

import java.util.List;

/**
 * Created by yskadani on 28/01/18.
 */
public class MultipleRuleHalfShopEvaluationModel extends MultipleRuleEvaluationModel implements Surrogate {

    protected SchedulingSet surrogateSet;
    protected boolean useSurrogate;

    public SchedulingSet getSurrogateSet() {
        return surrogateSet;
    }

    @Override
    public void useSurrogate() {
        useSurrogate = true;
    }

    @Override
    public void useOriginal() {
        useSurrogate = false;
    }

    @Override
    public boolean getSurrogate() {
        return useSurrogate;
    }

    @Override
    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        surrogateSet = schedulingSet.surrogate(5, 500, 100, objectives);
        useSurrogate();
    }

    @Override
    public void evaluate(List<Fitness> currentFitnesses,
                         List<AbstractRule> rules,
                         EvolutionState state) {
        //expecting 2 rules here - one routing rule and one sequencing rule
        if (rules.size() != currentFitnesses.size() || rules.size() != 2) {
            System.out.println("Rule evaluation failed!");
            System.out.println("Expecting 2 rules, only 1 found.");
            return;
        }

        AbstractRule sequencingRule = rules.get(0);
        AbstractRule routingRule = rules.get(1);
        SchedulingSet set = useSurrogate ? surrogateSet : schedulingSet;

        double[] fitnesses = sequencingRule.calcFitness(currentFitnesses.get(0),state,set,routingRule,objectives);
        //sequencing rule fitness should be updated already, still need to update routing rule fitness
        MultiObjectiveFitness f = (MultiObjectiveFitness) currentFitnesses.get(1); //get routing rule fitness
        f.setObjectives(state, fitnesses);
    }

    @Override
    public void rotate() {
        super.rotate();
        surrogateSet.rotateSeed(objectives);
    }
}
