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
    private final int minNumOptions;
    private final int maxNumOptions;
    private final double utilLevel;
    private final double dueDateFactor;
    private final boolean revisit;

    private AbstractIntegerSampler numOperationsSampler;
    private AbstractIntegerSampler numOptionsSampler;
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
                              int minNumOptions,
                              int maxNumOptions,
                              double utilLevel,
                              double dueDateFactor,
                              boolean revisit,
                              AbstractIntegerSampler numOperationsSampler,
                              AbstractIntegerSampler numOptionsSampler,
                              AbstractRealSampler procTimeSampler,
                              AbstractRealSampler interArrivalTimeSampler,
                              AbstractRealSampler jobWeightSampler) {
        super(sequencingRule, routingRule, numWorkCenters, numJobsRecorded, warmupJobs);

        this.seed = seed;
        this.randomDataGenerator = new RandomDataGenerator();
        this.randomDataGenerator.reSeed(seed);

        this.minNumOperations = minNumOperations;
        this.maxNumOperations = maxNumOperations;
        this.minNumOptions = minNumOptions;
        this.maxNumOptions = maxNumOptions;
        this.utilLevel = utilLevel;
        this.dueDateFactor = dueDateFactor;
        this.revisit = revisit;

        this.numOperationsSampler = numOperationsSampler;
        this.numOptionsSampler = numOptionsSampler;
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
                             int minNumOptions,
                             int maxNumOptions,
                             double utilLevel,
                             double dueDateFactor,
                             boolean revisit) {
        this(seed, sequencingRule, routingRule, numWorkCenters, numJobsRecorded, warmupJobs,
                minNumOperations, maxNumOperations, minNumOptions, maxNumOptions, utilLevel,
                dueDateFactor, revisit,
                new UniformIntegerSampler(minNumOperations, maxNumOperations),
                new UniformIntegerSampler(minNumOptions, maxNumOptions),
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

    public int getMinNumOptions() {
        return minNumOptions;
    }

    public int getMaxNumOptions() {
        return maxNumOptions;
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

    public AbstractIntegerSampler getNumOptionsSampler() {
        return numOptionsSampler;
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
        double arrivalTime = getClockTime()
                + interArrivalTimeSampler.next(randomDataGenerator);
        double weight = jobWeightSampler.next(randomDataGenerator);
        Job job = new Job(numJobsArrived, new ArrayList<>(),
                arrivalTime, arrivalTime, 0, weight);
        int numOperations = numOperationsSampler.next(randomDataGenerator);

        double totalProcTime = 0.0;
        for (int i = 0; i < numOperations; i++) {
            Operation o = new Operation(job, i);
            int numOptions = numOperationsSampler.next(randomDataGenerator);
            double longestProcTime = Double.NEGATIVE_INFINITY;
            int[] route = randomDataGenerator.nextPermutation(numWorkCenters, numOptions);
            for (int j = 0; j < numOptions; ++j) {
                double procTime = procTimeSampler.next(randomDataGenerator);
                if (j > route.length) {
                    System.out.println("wut");
                }
                o.addOperationOption(new OperationOption(o,j,procTime,systemState.getWorkCenter(route[j])));
                if (procTime > longestProcTime) {
                    longestProcTime = procTime;
                }
            }
            if (longestProcTime == Double.NEGATIVE_INFINITY) {
                System.out.println("Should be at least 1 option");
            }
            totalProcTime += longestProcTime;

            job.addOperation(o);
        }

        job.linkOperations();

        double dueDate = job.getReleaseTime() + dueDateFactor * totalProcTime;
        job.setDueDate(dueDate);

        systemState.addJobToSystem(job);
        numJobsArrived ++;

        eventQueue.add(new JobArrivalEvent(job));
    }

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

    public List<DecisionSituation> decisionSituations(int minQueueLength) {
        List<DecisionSituation> decisionSituations = new ArrayList<>();

        while (!eventQueue.isEmpty() && throughput < numJobsRecorded) {
            AbstractEvent nextEvent = eventQueue.poll();

            systemState.setClockTime(nextEvent.getTime());
            nextEvent.addDecisionSituation(this, decisionSituations, minQueueLength);
        }

        resetState();

        return decisionSituations;
    }

    @Override
    public Simulation surrogate(int numWorkCenters, int numJobsRecorded,
                                       int warmupJobs) {
        int surrogateMaxNumOperations = maxNumOperations;
        int surrogateMaxNumOptions = maxNumOptions;

        AbstractIntegerSampler surrogateNumOperationsSampler = numOperationsSampler.clone();
        AbstractIntegerSampler surrogateNumOptionsSampler = numOptionsSampler.clone();
        AbstractRealSampler surrogateInterArrivalTimeSampler = interArrivalTimeSampler.clone();

        if (surrogateMaxNumOperations > numWorkCenters) {
            surrogateMaxNumOperations = numWorkCenters;
            surrogateNumOperationsSampler.setUpper(surrogateMaxNumOperations);

            surrogateInterArrivalTimeSampler.setMean(interArrivalTimeMean(numWorkCenters,
                    minNumOperations, surrogateMaxNumOperations, utilLevel));
        }
        if (surrogateMaxNumOptions > numWorkCenters) {
            surrogateMaxNumOptions = numWorkCenters;
            surrogateNumOptionsSampler.setUpper(surrogateMaxNumOptions);
        }

        Simulation surrogate = new DynamicSimulation(seed, sequencingRule, routingRule, numWorkCenters,
                numJobsRecorded, warmupJobs, minNumOperations, surrogateMaxNumOperations,
                minNumOptions, surrogateMaxNumOptions, utilLevel, dueDateFactor, revisit,
                surrogateNumOperationsSampler, surrogateNumOptionsSampler,
                procTimeSampler, surrogateInterArrivalTimeSampler, jobWeightSampler);

        return surrogate;
    }

    @Override
    public Simulation surrogateBusy(int numWorkCenters, int numJobsRecorded,
                                int warmupJobs) {
        double utilLevel = 1;
        int surrogateMaxNumOperations = maxNumOperations;
        int surrogateMaxNumOptions = maxNumOptions;

        AbstractIntegerSampler surrogateNumOperationsSampler = numOperationsSampler.clone();
        AbstractIntegerSampler surrogateNumOptionsSampler = numOptionsSampler.clone();
        AbstractRealSampler surrogateInterArrivalTimeSampler = interArrivalTimeSampler.clone();

        if (surrogateMaxNumOperations > numWorkCenters) {
            surrogateMaxNumOperations = numWorkCenters;
            surrogateNumOperationsSampler.setUpper(surrogateMaxNumOperations);

            surrogateInterArrivalTimeSampler.setMean(interArrivalTimeMean(numWorkCenters,
                    minNumOperations, surrogateMaxNumOperations, utilLevel));
        }
        if (surrogateMaxNumOptions > numWorkCenters) {
            surrogateMaxNumOptions = numWorkCenters;
            surrogateNumOptionsSampler.setUpper(surrogateMaxNumOptions);
        }

        Simulation surrogate = new DynamicSimulation(seed, sequencingRule, routingRule, numWorkCenters,
                numJobsRecorded, warmupJobs, minNumOperations, surrogateMaxNumOperations,
                minNumOptions, surrogateMaxNumOptions, utilLevel, dueDateFactor, revisit,
                surrogateNumOperationsSampler, surrogateNumOptionsSampler,
                procTimeSampler, surrogateInterArrivalTimeSampler, jobWeightSampler);

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
                warmupJobs, numWorkCenters, numWorkCenters, numWorkCenters, numWorkCenters, utilLevel,
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
                warmupJobs, 2, numWorkCenters, 1, numWorkCenters, utilLevel, dueDateFactor, false);
    }
}
