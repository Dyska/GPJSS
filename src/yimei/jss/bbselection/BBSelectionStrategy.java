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
        name = name.toLowerCase();
        if (name.startsWith("score-")) {
            //"Score>"+String.valueOf(threshold);
            double threshold = Double.parseDouble(name.substring("score-".length()));
            return new BBStaticThresholdStrategy(threshold);
        } else if (name.endsWith("-clustering")) {
            //numClusters+"-Clustering";
            int k = Integer.parseInt(name.substring(0,name.length()-"-clustering".length()));
            return new BBClusteringStrategy(k);
        } else if (name.startsWith("bb-") && name.contains("x")) {
            //"BB>"+proportion+"*"+totalVotingWeight;
            //This is a tricky one... - totalVotingWeight may have been set, or not
            //Will try parse the total voting weight, but if it fails, will catch the exception and
            //use other constructor
            int multIndex = name.indexOf('x');
            double proportion = Double.parseDouble(name.substring("bb-".length(),multIndex));

            try {
                double totalVotingWeight = Double.parseDouble(name.substring(multIndex+1));
                return new BBStaticProportionTVW(totalVotingWeight,proportion);
            } catch (NumberFormatException n) {
                return new BBStaticProportionTVW(proportion);
            }
        } else if (name.startsWith("top-")) {
            //"top-"+k;
            int k = Integer.parseInt(name.substring("top-".length()));
            return new TopKStrategy(k);
        }

        return null;
    }
}
