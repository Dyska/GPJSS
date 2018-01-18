package yimei.jss.contributionselection;

import ec.gp.GPIndividual;
import ec.gp.GPNode;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.List;

/**
 * Created by dyska on 18/01/18.
 */
public interface ContributionSelectionStrategy {

    void selectContributions(double[][] contributions,
                        List<GPIndividual> selIndis,
                        List<GPNode> BBs,
                        List<DescriptiveStatistics> BBVotingWeightStats,
                        DescriptiveStatistics votingWeightStat);

    String getName();
}
