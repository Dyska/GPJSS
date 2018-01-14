package yimei.jss.bbselection;

import ec.gp.GPNode;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.List;

/**
 * Created by dyska on 13/01/18.
 */
public class StaticThresholdStrategy implements BBSelectionStrategy {
    private double threshold;

    public StaticThresholdStrategy(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public void selectBuildingBlocks(List<GPNode> BBs, List<GPNode> selBBs,
                                     List<DescriptiveStatistics> BBVotingWeightStats,
                                     List<Double> selBBsVotingWeights) {
        for (int i = 0; i < BBs.size(); i++) {
            double votingWeight = BBVotingWeightStats.get(i).getSum();
            if (votingWeight > threshold) {
                selBBs.add(BBs.get(i));
                selBBsVotingWeights.add(votingWeight);
                System.out.println(BBs.get(i).makeCTree(false,true,
                        true) +" - recieved: "+votingWeight+" voting weight.");
            }
        }
    }

    @Override
    public int[] selectBuildingBlocks(List<String> BBs, List<Double> BBVotingWeightStats, boolean verbose) {
        int[] selectedBBs = new int[BBs.size()];

        for (int i = 0; i < BBs.size(); i++) {
            double votingWeight = BBVotingWeightStats.get(i);
            if (votingWeight > threshold) {
                selectedBBs[i] = 1;
                if (verbose) {
                    System.out.println(BBs.get(i) +" - recieved: "+votingWeight+" voting weight.");
                }
            }
        }
        return selectedBBs;
    }

    @Override
    public String getName() {
        return "Score>"+String.valueOf(threshold);
    }
}
