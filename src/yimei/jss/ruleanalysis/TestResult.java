package yimei.jss.ruleanalysis;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ec.Fitness;
import ec.gp.koza.KozaFitness;
import ec.multiobjective.MultiObjectiveFitness;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.rule.AbstractRule;

public class TestResult {

	private List<AbstractRule[]> generationalRules;
	private List<Fitness> generationalTrainFitnesses;
	private List<Fitness> generationalValidationFitnesses;
	private List<Fitness> generationalTestFitnesses;
	private AbstractRule[] bestRules;
	private Fitness bestTrainingFitness;
	private Fitness bestValidationFitness;
	private Fitness bestTestFitness;
	private DescriptiveStatistics generationalTimeStat;

	public static final long validationSimSeed = 483561;

	public TestResult() {
		generationalRules = new ArrayList<>();
		generationalTrainFitnesses = new ArrayList<>();
		generationalValidationFitnesses = new ArrayList<>();
		generationalTestFitnesses = new ArrayList<>();
	}

	public List<AbstractRule[]> getGenerationalRules() {
		return generationalRules;
	}

	public AbstractRule[] getGenerationalRules(int idx) {
		return generationalRules.get(idx);
	}

	public List<Fitness> getGenerationalTrainFitnesses() {
		return generationalTrainFitnesses;
	}

	public Fitness getGenerationalTrainFitness(int idx) {
		return generationalTrainFitnesses.get(idx);
	}

	public List<Fitness> getGenerationalValidationFitnesses() {
		return generationalValidationFitnesses;
	}

	public Fitness getGenerationalValidationFitness(int idx) {
		return generationalValidationFitnesses.get(idx);
	}

	public List<Fitness> getGenerationalTestFitnesses() {
		return generationalTestFitnesses;
	}

	public Fitness getGenerationalTestFitness(int idx) {
		return generationalTestFitnesses.get(idx);
	}

	public AbstractRule[] getBestRules() {
		return bestRules;
	}

	public Fitness getBestTrainingFitness() {
		return bestTrainingFitness;
	}

	public Fitness getBestValidationFitness() {
		return bestValidationFitness;
	}

	public Fitness getBestTestFitness() {
		return bestTestFitness;
	}

	public DescriptiveStatistics getGenerationalTimeStat() {
		return generationalTimeStat;
	}

	public double getGenerationalTime(int gen) {
		return generationalTimeStat.getElement(gen);
	}

	public void setGenerationalRules(List<AbstractRule[]> generationalRules) {
		this.generationalRules = generationalRules;
	}

	public void addGenerationalRules(AbstractRule[] rules) {
		this.generationalRules.add(rules);
	}

	public void setGenerationalTrainFitnesses(List<Fitness> generationalTrainFitnesses) {
		this.generationalTrainFitnesses = generationalTrainFitnesses;
	}

	public void addGenerationalTrainFitness(Fitness f) {
		this.generationalTrainFitnesses.add(f);
	}

	public void setGenerationalValidationFitness(List<Fitness> generationalValidationFitnesses) {
		this.generationalValidationFitnesses = generationalValidationFitnesses;
	}

	public void addGenerationalValidationFitnesses(Fitness f) {
		this.generationalValidationFitnesses.add(f);
	}

	public void setGenerationalTestFitnesses(List<Fitness> generationalTestFitnesses) {
		this.generationalTestFitnesses = generationalTestFitnesses;
	}

	public void addGenerationalTestFitnesses(Fitness f) {
		this.generationalTestFitnesses.add(f);
	}

	public void setBestRules(AbstractRule[] bestRules) {
		this.bestRules = bestRules;
	}

	public void setBestTrainingFitness(Fitness bestTrainingFitness) {
		this.bestTrainingFitness = bestTrainingFitness;
	}

	public void setBestValidationFitness(Fitness bestValidationFitnesses) {
		this.bestValidationFitness = bestValidationFitnesses;
	}

	public void setBestTestFitness(Fitness bestTestFitnesses) {
		this.bestTestFitness = bestTestFitnesses;
	}

	public void setGenerationalTimeStat(DescriptiveStatistics generationalTimeStat) {
		this.generationalTimeStat = generationalTimeStat;
	}

	public static TestResult readFromFile(File file, RuleType ruleType, int numPopulations, boolean wasSurrogate) {
		return ResultFileReader.readTestResultFromFile(file, ruleType, ruleType.isMultiobjective(), numPopulations, wasSurrogate);
	}

	public void validate(List<Objective> objectives) {
		SchedulingSet validationSet =
				SchedulingSet.dynamicMissingSet(validationSimSeed, 0.95,
						4.0, objectives, 50);

		Fitness validationFitness;
		if (objectives.size() == 1) {
			validationFitness = new KozaFitness();
			bestValidationFitness = new KozaFitness();
		}
		else {
			validationFitness = new MultiObjectiveFitness();
            bestValidationFitness = new MultiObjectiveFitness();
		}

		bestRules = generationalRules.get(0);

		//bestRule.calcFitness(bestValidationFitness, null, validationSet, objectives);
		generationalValidationFitnesses.add(bestValidationFitness);

//		System.out.println("Generation 0: validation fitness = " + bestValidationFitness.fitness());

		for (int i = 1; i < generationalRules.size(); i++) {
			//generationalRules.get(i).calcFitness(validationFitness, null, validationSet, objectives);
			generationalValidationFitnesses.add(validationFitness);


//			System.out.println("Generation " + i + ": validation fitness = " + validationFitness.fitness());

			if (validationFitness.betterThan(bestValidationFitness)) {
				bestRules = generationalRules.get(i);
				bestTrainingFitness = generationalTrainFitnesses.get(i);
				bestValidationFitness = validationFitness;
			}
		}
	}
}
