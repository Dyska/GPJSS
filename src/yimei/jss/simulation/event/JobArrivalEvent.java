package yimei.jss.simulation.event;

import yimei.jss.jobshop.Job;
import yimei.jss.jobshop.Operation;
import yimei.jss.jobshop.OperationOption;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.workcenter.basic.WIQ;
import yimei.jss.simulation.RoutingDecisionSituation;
import yimei.jss.simulation.SequencingDecisionSituation;
import yimei.jss.simulation.DynamicSimulation;
import yimei.jss.simulation.Simulation;

import java.util.List;

/**
 * Created by yimei on 22/09/16.
 */
public class JobArrivalEvent extends AbstractEvent {

    private Job job;

    public JobArrivalEvent(double time, Job job) {
        super(time);
        this.job = job;
    }

    public JobArrivalEvent(Job job) {
        this(job.getArrivalTime(), job);
    }

    @Override
    public void trigger(Simulation simulation) {
        //Job has just arrived, get first operation out
        Operation operation = job.getOperation(0);
        RoutingDecisionSituation decisionSituation = new RoutingDecisionSituation(
                operation.getOperationOptions(), simulation.getSystemState());
        OperationOption operationOption =
                simulation.getRoutingRule().nextOperationOption(decisionSituation);
        operationOption.setReadyTime(job.getReleaseTime());

        simulation.addEvent(new OperationVisitEvent(job.getReleaseTime(), operationOption));
        simulation.generateJob();
    }

    @Override
    public void addDecisionSituation(DynamicSimulation simulation,
                                     List<SequencingDecisionSituation> situations,
                                     int minQueueLength) {
        trigger(simulation);
    }

    @Override
    public String toString() {
        return String.format("%.1f: job %d arrives.\n", time, job.getId());
    }

    @Override
    public int compareTo(AbstractEvent other) {
        if (time < other.time)
            return -1;

        if (time > other.time)
            return 1;

        if (other instanceof JobArrivalEvent) {
            JobArrivalEvent otherJAE = (JobArrivalEvent)other;

            if (job.getId() < otherJAE.job.getId())
                return -1;

            if (job.getId() > otherJAE.job.getId())
                return 1;
        }

        return -1;
    }
}
