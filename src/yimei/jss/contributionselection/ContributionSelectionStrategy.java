package yimei.jss.contributionselection;

import ec.gp.GPIndividual;
import ec.gp.GPNode;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.List;

/**
 * Created by dyska on 18/01/18.
 */
public abstract class ContributionSelectionStrategy {

    public abstract void selectContributions(List<DescriptiveStatistics> featureContributionStats,
                        List<GPIndividual> selIndis,
                        List<GPNode> BBs,
                        List<DescriptiveStatistics> BBVotingWeightStats,
                        DescriptiveStatistics votingWeightStat);

    public abstract String getName();

    public static ContributionSelectionStrategy selectStrategy(String name) {
        name = name.toLowerCase();
        if (name.startsWith("score-")) {
            //ContributionStaticThreshold
            double threshold = Double.parseDouble(name.substring("score-".length()));
            return new ContributionStaticThresholdStrategy(threshold);
        } else if (name.endsWith("-clustering")) {
            //ContributionClusteringStrategy
            int k = Integer.parseInt(name.substring(0,name.length()-"-clustering".length()));
            return new ContributionClusteringStrategy(k);
        }

        return null;
    }
}
