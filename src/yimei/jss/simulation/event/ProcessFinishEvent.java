package yimei.jss.simulation.event;

import yimei.jss.jobshop.*;
import yimei.jss.jobshop.Process;
import yimei.jss.simulation.DynamicSimulation;
import yimei.jss.simulation.DecisionSituation;
import yimei.jss.simulation.Simulation;

import java.util.List;

/**
 * Created by yimei on 22/09/16.
 */
public class ProcessFinishEvent extends AbstractEvent {

    private Process process;

    public ProcessFinishEvent(double time, Process process) {
        super(time);
        this.process = process;
    }

    public ProcessFinishEvent(Process process) {
        this(process.getFinishTime(), process);
    }

    @Override
    public void trigger(Simulation simulation) {
        WorkCenter workCenter = process.getWorkCenter();
        process.getOperation().getJob().addProcessFinishEvent(this);

        if (!workCenter.getQueue().isEmpty()) {
            DecisionSituation decisionSituation =
                    new DecisionSituation(workCenter.getQueue(), workCenter,
                            simulation.getSystemState());

            OperationOption dispatchedOp =
                    simulation.getSequencingRule().priorOperation(decisionSituation);

            workCenter.removeFromQueue(dispatchedOp);

            //must wait for machine to be ready
            //TODO: Review this
            double processStartTime = Math.max(workCenter.getReadyTime(), time);

            Process nextP = new Process(workCenter, process.getMachineId(),
                    dispatchedOp, processStartTime);
            simulation.addEvent(new ProcessStartEvent(nextP));
        }

        OperationOption nextOp = process.getOperation().getNext(simulation.getSystemState());

        if (nextOp == null) {
            Job job = process.getOperation().getJob();
            job.setCompletionTime(process.getFinishTime());
            simulation.completeJob(job);
        }
        else {
            simulation.addEvent(new OperationVisitEvent(time, nextOp));
        }
    }

    @Override
    public void addDecisionSituation(DynamicSimulation simulation,
                                     List<DecisionSituation> situations,
                                     int minQueueLength) {
        WorkCenter workCenter = process.getWorkCenter();

        if (!workCenter.getQueue().isEmpty()) {
            DecisionSituation decisionSituation =
                    new DecisionSituation(workCenter.getQueue(), workCenter,
                            simulation.getSystemState());

            if (workCenter.getQueue().size() >= minQueueLength) {
                situations.add(decisionSituation.clone());
            }

            OperationOption dispatchedOp =
                    simulation.getSequencingRule().priorOperation(decisionSituation);

            workCenter.removeFromQueue(dispatchedOp);
            Process nextP = new Process(workCenter, process.getMachineId(),
                    dispatchedOp, time);
            simulation.addEvent(new ProcessStartEvent(nextP));
        }

        OperationOption nextOp = process.getOperation().getNext(simulation.getSystemState());
        if (nextOp == null) {
            Job job = process.getOperation().getJob();
            job.setCompletionTime(process.getFinishTime());
            simulation.completeJob(job);
        }
        else {
            simulation.addEvent(new OperationVisitEvent(time, nextOp));
        }
    }

    @Override
    public String toString() {
        return String.format("%.1f: job %d op %d finished on work center %d.\n",
                time,
                process.getOperation().getJob().getId(),
                process.getOperation().getOperation().getId(),
                process.getWorkCenter().getId());
    }

    @Override
    public int compareTo(AbstractEvent other) {
        if (time < other.time)
            return -1;

        if (time > other.time)
            return 1;

        if (other instanceof ProcessFinishEvent) {
            ProcessFinishEvent otherPFE = (ProcessFinishEvent)other;

            if (process.getWorkCenter().getId() < otherPFE.process.getWorkCenter().getId())
                return -1;

            if (process.getWorkCenter().getId() > otherPFE.process.getWorkCenter().getId())
            return 1;
        }

        return 1;
    }

    public Process getProcess() {
        return process;
    }
}
