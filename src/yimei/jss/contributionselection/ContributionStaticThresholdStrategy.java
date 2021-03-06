package yimei.jss.contributionselection;

import ec.gp.GPIndividual;
import ec.gp.GPNode;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import java.util.List;

/**
 * Created by dyska on 18/01/18.
 */
public class ContributionStaticThresholdStrategy extends ContributionSelectionStrategy {

    private double threshold;

    public ContributionStaticThresholdStrategy(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public void selectContributions(double[][] contributions,
                                    List<GPIndividual> selIndis,
                                    List<GPNode> BBs,
                                    List<DescriptiveStatistics> BBVotingWeightStats,
                                    DescriptiveStatistics votingWeightStat) {
        for (int s = 0; s < selIndis.size(); s++) {
            for (int i = 0; i < BBs.size(); i++) {
                double c = contributions[s][i];

                if (c > threshold) {
                    BBVotingWeightStats.get(i).addValue(votingWeightStat.getElement(s));
                    System.out.println("Rule "+s+" voted for building block "+i+
                            " with weight "+votingWeightStat.getElement(s)+" - c: "+c+".");
                }
                else {
                    BBVotingWeightStats.get(i).addValue(0);
                }
            }
        }
    }

    @Override
    public String getName() {
        return "Score-"+String.valueOf(threshold);
    }
}
