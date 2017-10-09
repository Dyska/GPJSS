package yimei.jss.gp;

import java.io.*;
import java.util.*;

import ec.Individual;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.DoubleERC;
import yimei.jss.gp.terminal.JobShopAttribute;
import ec.EvolutionState;
import ec.gp.GPNode;
import ec.simple.SimpleEvolutionState;
import ec.util.Checkpoint;
import ec.util.Parameter;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;

/**
 * The evolution state of evolving dispatching rules with GP.
 *
 * @author yimei
 *
 */

public class GPRuleEvolutionState extends SimpleEvolutionState {

	/**
	 * Read the file to specify the terminals.
	 */
	public final static String P_TERMINALS_FROM = "terminals-from";
	public final static String P_INCLUDE_ERC = "include-erc";

	protected String[] terminalsFrom;
	protected boolean[] includeErc;
	protected long jobSeed;
	protected GPNode[][] terminals;

	List<Double> genTimes = new ArrayList<>();

	public GPNode[][] getTerminals() {
		return terminals;
	}

    public GPNode[] getTerminals(int subPopNum) {
        return terminals[subPopNum];
    }

    public long getJobSeed() {
		return jobSeed;
	}

	public void setTerminals(GPNode[][] terminals) {
		this.terminals = terminals;
	}

    public void setTerminals(GPNode[] terminals, int subPopNum) {
        this.terminals[subPopNum] = terminals;
    }

    /**
	 * Initialize the terminal set with all the job shop attributes.
	 */
	public void initTerminalSet() {
        int numSubPops = parameters.getInt(new Parameter("pop.subpops"),null);
        this.terminals = new GPNode[numSubPops][];

        for (int subPopNum = 0; subPopNum < numSubPops; ++subPopNum) {
            String terminalFrom = terminalsFrom[subPopNum];
            boolean includeErc = this.includeErc[subPopNum];
            if (terminalFrom.equals("basic")) {
                initBasicTerminalSet(subPopNum);
            }
            else if (terminalFrom.equals("relative")) {
                initRelativeTerminalSet(subPopNum);
            }
            else {
                String terminalFile = "terminals/" + terminalFrom;
                initTerminalSetFromCsv(new File(terminalFile), subPopNum);
            }

            if (includeErc) {
                //terminals.add(new DoubleERC());
                //TODO: Implement this
                System.out.println("INCLUDE ERC NOT IMPLEMENTED");
            }
        }
	}

	public void initBasicTerminalSet(int subPopNum) {
		LinkedList<GPNode> terminals = new LinkedList<GPNode>();
		for (JobShopAttribute a : JobShopAttribute.basicAttributes()) {
            GPNode attribute = new AttributeGPNode(a);
            terminals.add(attribute);
		}
		this.terminals[subPopNum] = terminals.toArray(new GPNode[0]);

	}

	public void initRelativeTerminalSet(int subPopNum) {
        LinkedList<GPNode> terminals = new LinkedList<GPNode>();
		for (JobShopAttribute a : JobShopAttribute.relativeAttributes()) {
		    GPNode attribute = new AttributeGPNode(a);
			terminals.add(attribute);
		}
        this.terminals[subPopNum] = terminals.toArray(new GPNode[0]);
    }

	public void initTerminalSetFromCsv(File csvFile, int subPopNum) {
        LinkedList<GPNode> terminals = new LinkedList<GPNode>();

		BufferedReader br = null;
        String line = "";

        try {
            br = new BufferedReader(new FileReader(csvFile));
            while ((line = br.readLine()) != null) {
            	JobShopAttribute a = JobShopAttribute.get(line);
                GPNode attribute = new AttributeGPNode(a);
                terminals.add(attribute);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        this.terminals[subPopNum] = terminals.toArray(new GPNode[0]);
    }

	/**
	 * Return the index of an attribute in the terminal set.
	 * @param attribute the attribute.
	 * @return the index of the attribute in the terminal set.
	 */
	public int indexOfAttribute(JobShopAttribute attribute, int subPopNum) {
	    GPNode[] terminals = this.terminals[subPopNum];
		for (int i = 0; i < terminals.length; i++) {
			JobShopAttribute terminalAttribute = ((AttributeGPNode)terminals[i]).getJobShopAttribute();
			if (terminalAttribute == attribute) {
				return i;
			}
		}

		return -1;
	}

	/**
	 * Randomly pick a terminal from the terminal set.
	 * @return the selected terminal, which is a GPNode.
	 */
	public GPNode pickTerminalRandom(int subPopNum) {
    	int index = random[0].nextInt(terminals[subPopNum].length);
    	return terminals[subPopNum][index];
    }

	// the best individual in subpopulation
	public Individual bestIndi(int subpop) {
		int best = 0;
		for(int x = 1; x < population.subpops[subpop].individuals.length; x++)
			if (population.subpops[subpop].individuals[x].fitness.betterThan(
			        population.subpops[subpop].individuals[best].fitness))
				best = x;

		return population.subpops[subpop].individuals[best];
	}

	@Override
	public void setup(EvolutionState state, Parameter base) {
		Parameter p;

		// Get the job seed.
		p = new Parameter("seed").push(""+0);
		jobSeed = parameters.getLongWithDefault(p, null, 0);

        setupTerminals();

		super.setup(this, base);
	}

	@Override
	public void run(int condition)
    {
		double totalTime = 0;

		if (condition == C_STARTED_FRESH) {
			startFresh();
        }
		else {
			startFromCheckpoint();
        }

		int result = R_NOTDONE;
		while ( result == R_NOTDONE )
        {
			long start = yimei.util.Timer.getCpuTime();

			result = evolve();

			long finish = yimei.util.Timer.getCpuTime();
			double duration = (finish - start) / 1000000000;
			genTimes.add(duration);
			totalTime += duration;

			output.message("Generation " + (generation-1) + " elapsed " + duration + " seconds.");
        }

		output.message("The whole program elapsed " + totalTime + " seconds.");

		finish(result);
    }

    @Override
	public int evolve() {
	    if (generation > 0)
	        output.message("Generation " + generation);

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
		RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
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

	private void setupTerminals() {
        Parameter p;

        //Need to know how many populations we're expecting here, as will need
        //one terminal set per population
        int numSubPops = parameters.getInt(new Parameter("pop.subpops"),null);

        if (numSubPops == 1) {
            p = new Parameter(P_TERMINALS_FROM);

            terminalsFrom = new String[]{parameters.getStringWithDefault(p,
                    null, "relative")};

            p = new Parameter(P_INCLUDE_ERC);
            includeErc = new boolean[]{parameters.getBoolean(p, null, false)};
            initTerminalSet();
        } else if (numSubPops == 2) {
            terminalsFrom = new String[numSubPops];
            includeErc = new boolean[numSubPops];
            int subPopNum = 0;

            p = new Parameter(P_TERMINALS_FROM + "." + subPopNum);
            String subPop1TerminalSet = parameters.getStringWithDefault(p,
                    null, null);
            if (subPop1TerminalSet == null) {
                //might have provided other value by mistake, we should check for this
                p = new Parameter(P_TERMINALS_FROM);
                subPop1TerminalSet = parameters.getStringWithDefault(p,
                        null, "relative");
                output.warning("No terminal set for subpopulation 1 specified - using "+subPop1TerminalSet+".");

            }
            terminalsFrom[subPopNum] = subPop1TerminalSet;

            subPopNum++;
            p = new Parameter(P_TERMINALS_FROM + "." + subPopNum);
            String subPop2TerminalSet = parameters.getStringWithDefault(p,
                    null, null);
            if (subPop2TerminalSet == null) {
                //use whatever we settled on for first population
                subPop2TerminalSet = subPop1TerminalSet;
                output.warning("No terminal set for subpopulation 2 specified - using terminal set for subpopulation 1.");
            }
            terminalsFrom[subPopNum] = subPop2TerminalSet;
            //TODO: Add support for erc - will be false by default

            initTerminalSet();
        }
    }
}
