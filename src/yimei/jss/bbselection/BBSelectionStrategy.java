package yimei.jss.bbselection;

import ec.gp.GPNode;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.List;

/**
 * Created by dyska on 13/01/18.
 */
public interface BBSelectionStrategy {

    void selectBuildingBlocks(List<GPNode> BBs, List<GPNode> selBBs, List<DescriptiveStatistics> BBVotingWeightStats, List<Double> selBBsVotingWeights);

    int[] selectBuildingBlocks(List<String> BBs, List<Double> BBVotingWeightStats, boolean verbose);

    String getName();
}
