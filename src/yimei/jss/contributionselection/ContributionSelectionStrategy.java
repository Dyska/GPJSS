package yimei.jss.contributionselection;

import ec.gp.GPIndividual;
import ec.gp.GPNode;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.List;

/**
 * Created by dyska on 18/01/18.
 */
public abstract class ContributionSelectionStrategy {

    public abstract void selectContributions(double[][] contributions,
                        List<GPIndividual> selIndis,
                        List<GPNode> BBs,
                        List<DescriptiveStatistics> BBVotingWeightStats,
                        DescriptiveStatistics votingWeightStat);

    public abstract String getName();

    public static ContributionSelectionStrategy selectStrategy(String name) {
        if (name.startsWith("Score>")) {
            //ContributionStaticThreshold
            double threshold = Double.parseDouble(name.substring("Score>".length()));
            return new ContributionStaticThresholdStrategy(threshold);
        } else if (name.endsWith("-Clustering")) {
            //ContributionClusteringStrategy
            int k = Integer.parseInt(name.substring(0,name.length()-"-Clustering".length()));
            return new ContributionClusteringStrategy(k);
        }

        return null;
    }
}
