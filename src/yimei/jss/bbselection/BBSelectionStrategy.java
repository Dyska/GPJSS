package yimei.jss.bbselection;

import ec.gp.GPNode;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.List;

/**
 * Created by dyska on 13/01/18.
 */
public abstract class BBSelectionStrategy {

    public abstract void selectBuildingBlocks(List<GPNode> BBs, List<GPNode> selBBs, List<DescriptiveStatistics> BBVotingWeightStats, List<Double> selBBsVotingWeights);

    public abstract int[] selectBuildingBlocks(List<String> BBs, List<Double> BBVotingWeightStats, boolean verbose);

    public abstract String getName();

    public static BBSelectionStrategy selectStrategy(String name) {
        if (name.startsWith("Score>")) {
            //"Score>"+String.valueOf(threshold);
            double threshold = Double.parseDouble(name.substring("Score>".length()));
            return new BBStaticThresholdStrategy(threshold);
        } else if (name.endsWith("-Clustering")) {
            //numClusters+"-Clustering";
            int k = Integer.parseInt(name.substring(0,name.length()-"-Clustering".length()));
            return new BBClusteringStrategy(k);
        } else if (name.startsWith("BB") && name.contains("*")) {
            //"BB>"+proportion+"*"+totalVotingWeight;
            int multIndex = name.indexOf('*');
            double proportion = Double.parseDouble(name.substring("BB>".length(),multIndex));
            double totalVotingWeight = Double.parseDouble(name.substring(multIndex+1));
            return new StaticProportionTotalVotingWeight(totalVotingWeight,proportion);
        } else if (name.startsWith("top-")) {
            //"top-"+k;
            int k = Integer.parseInt(name.substring("top-".length()));
            return new TopKStrategy(k);
        }

        return null;
    }
}
