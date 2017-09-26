package yimei.jss.simulation;

import org.apache.commons.math3.random.RandomDataGenerator;
import yimei.jss.jobshop.*;
import yimei.util.random.*;
import yimei.jss.rule.AbstractRule;
import yimei.jss.simulation.event.AbstractEvent;
import yimei.jss.simulation.event.JobArrivalEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The dynamic simulation -- discrete event simulation
 *
 * Created by yimei on 22/09/16.
 */
public class DynamicSimulation extends Simulation {

    public final static int SEED_ROTATION = 10000;

    private long seed;
    private RandomDataGenerator randomDataGenerator;

    private final int minNumOperations;
    private final int maxNumOperations;
    private final double utilLevel;
    private final double dueDateFactor;
    private final boolean revisit;

    private AbstractIntegerSampler numOperationsSampler;
    private AbstractRealSampler procTimeSampler;
    private AbstractRealSampler interArrivalTimeSampler;
    private AbstractRealSampler jobWeightSampler;

    private DynamicSimulation(long seed,
                              AbstractRule sequencingRule,
                              AbstractRule routingRule,
                              int numWorkCenters,
                              int numJobsRecorded,
                              int warmupJobs,
                              int minNumOperations,
                              int maxNumOperations,
                              double utilLevel,
                              double dueDateFactor,
                              boolean revisit,
                              AbstractIntegerSampler numOperationsSampler,
                              AbstractRealSampler procTimeSampler,
                              AbstractRealSampler interArrivalTimeSampler,
                              AbstractRealSampler jobWeightSampler) {
        super(sequencingRule, routingRule, numWorkCenters, numJobsRecorded, warmupJobs);

        this.seed = seed;
        this.randomDataGenerator = new RandomDataGenerator();
        this.randomDataGenerator.reSeed(seed);

        this.minNumOperations = minNumOperations;
        this.maxNumOperations = maxNumOperations;
        this.utilLevel = utilLevel;
        this.dueDateFactor = dueDateFactor;
        this.revisit = revisit;

        this.numOperationsSampler = numOperationsSampler;
        this.procTimeSampler = procTimeSampler;
        this.interArrivalTimeSampler = interArrivalTimeSampler;
        this.jobWeightSampler = jobWeightSampler;

        setInterArrivalTimeSamplerMean();

        // Create the work centers, with empty queue and ready to go initially.
        for (int i = 0; i < numWorkCenters; i++) {
            systemState.addWorkCenter(new WorkCenter(i));
        }

        setup();
    }

    public DynamicSimulation(long seed,
                             AbstractRule sequencingRule,
                             AbstractRule routingRule,
                             int numWorkCenters,
                             int numJobsRecorded,
                             int warmupJobs,
                             int minNumOperations,
                             int maxNumOperations,
                             double utilLevel,
                             double dueDateFactor,
                             boolean revisit) {
        this(seed, sequencingRule, routingRule, numWorkCenters, numJobsRecorded, warmupJobs,
                minNumOperations, maxNumOperations, utilLevel, dueDateFactor, revisit,
                new UniformIntegerSampler(minNumOperations, maxNumOperations),
                new UniformSampler(1, 99),
                new ExponentialSampler(),
                new TwoSixTwoSampler());
    }

    public int getNumWorkCenters() {
        return numWorkCenters;
    }

    public int getNumJobsRecorded() {
        return numJobsRecorded;
    }

    public int getWarmupJobs() {
        return warmupJobs;
    }

    public int getMinNumOperations() {
        return minNumOperations;
    }

    public int getMaxNumOperations() {
        return maxNumOperations;
    }

    public double getUtilLevel() {
        return utilLevel;
    }

    public double getDueDateFactor() {
        return dueDateFactor;
    }

    public boolean isRevisit() {
        return revisit;
    }

    public RandomDataGenerator getRandomDataGenerator() {
        return randomDataGenerator;
    }

    public AbstractIntegerSampler getNumOperationsSampler() {
        return numOperationsSampler;
    }

    public AbstractRealSampler getProcTimeSampler() {
        return procTimeSampler;
    }

    public AbstractRealSampler getInterArrivalTimeSampler() {
        return interArrivalTimeSampler;
    }

    public AbstractRealSampler getJobWeightSampler() {
        return jobWeightSampler;
    }

    @Override
    public void setup() {
        numJobsArrived = 0;
        throughput = 0;
        generateJob();
    }

    @Override
    public void resetState() {
        systemState.reset();
        eventQueue.clear();

        setup();
    }

    @Override
    public void reset() {
        reset(seed);
    }

    public void reset(long seed) {
        reseed(seed);
        resetState();
    }

    public void reseed(long seed) {
        this.seed = seed;
        randomDataGenerator.reSeed(seed);
    }

    @Override
    public void rotateSeed() {
        seed += SEED_ROTATION;
        reset();
    }

    @Override
    public void generateJob() {
        //runExperiments();
        double arrivalTime = getClockTime()
                + interArrivalTimeSampler.next(randomDataGenerator);
        double weight = jobWeightSampler.next(randomDataGenerator);
        Job job = new Job(numJobsArrived, new ArrayList<>(),
                arrivalTime, arrivalTime, 0, weight);
        int numOperations = numOperationsSampler.next(randomDataGenerator);
        //System.out.println("Procedure time mean: "+procTimeSampler.getMean());

        for (int i = 0; i < numOperations; i++) {
            Operation o = new Operation(job, i);
            int numOptions = numOperationsSampler.next(randomDataGenerator);
            int[] route = randomDataGenerator.nextPermutation(numWorkCenters, numOptions);
            double procTime = procTimeSampler.next(randomDataGenerator); //use same proc time for all options for now
            for (int j = 0; j < numOptions; ++j) {
                o.addOperationOption(new OperationOption(o,j,procTime,systemState.getWorkCenter(route[j])));
            }
            job.addOperation(o);
        }

        job.linkOperations();

        //just set totalProcTime to average value, as we don't know which option will be chosen
        double totalProcTime = numOperations * procTimeSampler.getMean();
        double dueDate = job.getReleaseTime() + dueDateFactor * totalProcTime;
        job.setDueDate(dueDate);
//        if (job.getId() > 501) {
//            int a  = 1;
//        }

        systemState.addJobToSystem(job);
        numJobsArrived ++;

        eventQueue.add(new JobArrivalEvent(job));
    }

//    private void runExperiments() {
//        double interArrivalSum = 0.0;
//        double numOperationsSum = 0.0;
//        double numOptionsSum = 0.0;
//        double procTimeSum = 0.0;
//        int numRuns = 5000000;
//
//        for (int i = 0; i < numRuns; ++i) {
//            interArrivalSum += interArrivalTimeSampler.next(randomDataGenerator);
//            numOperationsSum += numOperationsSampler.next(randomDataGenerator);
//            numOptionsSum += numOperationsSampler.next(randomDataGenerator);
//            procTimeSum += procTimeSampler.next(randomDataGenerator);
//        }
//        System.out.println("Average interarrival time: "+interArrivalSum/numRuns);
//        System.out.println("Average num operations: "+numOperationsSum/numRuns);
//        System.out.println("Average num options: "+numOptionsSum/numRuns);
//        System.out.println("Average procedure time: "+procTimeSum/numRuns);
//        System.out.println();
//    }

    public double interArrivalTimeMean(int numWorkCenters,
                                             int minNumOps,
                                             int maxNumOps,
                                             double utilLevel) {
        double meanNumOps = 0.5 * (minNumOps + maxNumOps);
        double meanProcTime = procTimeSampler.getMean();

        return (meanNumOps * meanProcTime) / (utilLevel * numWorkCenters);
    }

    public void setInterArrivalTimeSamplerMean() {
        double mean = interArrivalTimeMean(numWorkCenters, minNumOperations, maxNumOperations, utilLevel);
        interArrivalTimeSampler.setMean(mean);
    }

    public List<SequencingDecisionSituation> sequencingDecisionSituations(int minQueueLength) {
        List<SequencingDecisionSituation> sequencingDecisionSituations = new ArrayList<>();

        while (!eventQueue.isEmpty() && throughput < numJobsRecorded) {
            AbstractEvent nextEvent = eventQueue.poll();

            systemState.setClockTime(nextEvent.getTime());
            nextEvent.addSequencingDecisionSituation(this, sequencingDecisionSituations, minQueueLength);
        }

        resetState();

        return sequencingDecisionSituations;
    }

    public List<RoutingDecisionSituation> routingDecisionSituations(int minQueueLength) {
        List<RoutingDecisionSituation> routingDecisionSituations = new ArrayList<>();

        while (!eventQueue.isEmpty() && throughput < numJobsRecorded) {
            AbstractEvent nextEvent = eventQueue.poll();

            systemState.setClockTime(nextEvent.getTime());
            nextEvent.addRoutingDecisionSituation(this, routingDecisionSituations, minQueueLength);
        }

        resetState();

        return routingDecisionSituations;
    }

    @Override
    public Simulation surrogate(int numWorkCenters, int numJobsRecorded,
                                       int warmupJobs) {
        int surrogateMaxNumOperations = maxNumOperations;

        AbstractIntegerSampler surrogateNumOperationsSampler = numOperationsSampler.clone();
        AbstractIntegerSampler surrogateNumOptionsSampler = numOperationsSampler.clone();
        AbstractRealSampler surrogateInterArrivalTimeSampler = interArrivalTimeSampler.clone();

        if (surrogateMaxNumOperations > numWorkCenters) {
            surrogateMaxNumOperations = numWorkCenters;
            surrogateNumOperationsSampler.setUpper(surrogateMaxNumOperations);

            surrogateInterArrivalTimeSampler.setMean(interArrivalTimeMean(numWorkCenters,
                    minNumOperations, surrogateMaxNumOperations, utilLevel));
        }

        Simulation surrogate = new DynamicSimulation(seed, sequencingRule, routingRule, numWorkCenters,
                numJobsRecorded, warmupJobs, minNumOperations, surrogateMaxNumOperations,
                utilLevel, dueDateFactor, revisit, surrogateNumOperationsSampler,
                procTimeSampler, surrogateInterArrivalTimeSampler, jobWeightSampler);

        return surrogate;
    }

    @Override
    public Simulation surrogateBusy(int numWorkCenters, int numJobsRecorded,
                                int warmupJobs) {
        double utilLevel = 1;
        int surrogateMaxNumOperations = maxNumOperations;

        AbstractIntegerSampler surrogateNumOperationsSampler = numOperationsSampler.clone();
        AbstractRealSampler surrogateInterArrivalTimeSampler = interArrivalTimeSampler.clone();

        if (surrogateMaxNumOperations > numWorkCenters) {
            surrogateMaxNumOperations = numWorkCenters;
            surrogateNumOperationsSampler.setUpper(surrogateMaxNumOperations);

            surrogateInterArrivalTimeSampler.setMean(interArrivalTimeMean(numWorkCenters,
                    minNumOperations, surrogateMaxNumOperations, utilLevel));
        }

        Simulation surrogate = new DynamicSimulation(seed, sequencingRule, routingRule, numWorkCenters,
                numJobsRecorded, warmupJobs, minNumOperations, surrogateMaxNumOperations, utilLevel,
                dueDateFactor, revisit, surrogateNumOperationsSampler, procTimeSampler,
                surrogateInterArrivalTimeSampler, jobWeightSampler);

        return surrogate;
    }

    public static DynamicSimulation standardFull(
            long seed,
            AbstractRule sequencingRule,
            AbstractRule routingRule,
            int numWorkCenters,
            int numJobsRecorded,
            int warmupJobs,
            double utilLevel,
            double dueDateFactor) {
        return new DynamicSimulation(seed, sequencingRule, routingRule, numWorkCenters, numJobsRecorded,
                warmupJobs, numWorkCenters, numWorkCenters, utilLevel,
                dueDateFactor, false);
    }

    public static DynamicSimulation standardMissing(
            long seed,
            AbstractRule sequencingRule,
            AbstractRule routingRule,
            int numWorkCenters,
            int numJobsRecorded,
            int warmupJobs,
            double utilLevel,
            double dueDateFactor) {
        return new DynamicSimulation(seed, sequencingRule, routingRule, numWorkCenters, numJobsRecorded,
                warmupJobs, 1, numWorkCenters, utilLevel, dueDateFactor, false);
    }
}
