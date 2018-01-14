package yimei.jss.bbselection;

import ec.gp.GPNode;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by dyska on 13/01/18.
 */
public class KHighestStrategy implements BBSelectionStrategy {
    private int k;

    public KHighestStrategy(int k) {
        this.k = k;
    }

    @Override
    public void selectBuildingBlocks(List<GPNode> BBs, List<GPNode> selBBs,
                                     List<DescriptiveStatistics> BBVotingWeightStats,
                                     List<Double> selBBsVotingWeights) {
        //need to sort the list of BBVotingWeightStats
        //note that the list of BBVotingWeightStats and BBs have the same index
        List<BuildingBlock> buildingBlocks = new ArrayList<>();

        for (int i = 0; i < BBVotingWeightStats.size(); ++i) {
            double score = BBVotingWeightStats.get(i).getSum();
            buildingBlocks.add(new BuildingBlock(BBs.get(i),score,i));
        }

        //sort by the score - should be highest first
        buildingBlocks.sort((bb1, bb2) -> {
            if (bb1.getScore() < bb2.getScore()) {
                return 1;
            } else if (bb1.getScore() > bb2.getScore()){
                return -1;
            }
            return 0;
        });

        //get first k building blocks out
        for (int i = 0; i < k; ++i) {
            BuildingBlock bb = buildingBlocks.get(i);
            selBBs.add(bb.getBB());
            selBBsVotingWeights.add(bb.getScore());
            System.out.println(bb.getBB().makeCTree(false,true,
                    true) +" - recieved: "+bb.getScore()+" voting weight.");
        }
    }

    @Override
    public int[] selectBuildingBlocks(List<String> BBs, List<Double> BBVotingWeightStats, boolean verbose) {
        int[] selectedBBs = new int[BBs.size()];

        //need to sort the list of BBVotingWeightStats
        //note that the list of BBVotingWeightStats and BBs have the same index
        List<BuildingBlock> buildingBlocks = new ArrayList<>();

        for (int i = 0; i < BBVotingWeightStats.size(); ++i) {
            double score = BBVotingWeightStats.get(i);
            buildingBlocks.add(new BuildingBlock(BBs.get(i),score,i));
        }

        //sort by the score - should be highest first
        buildingBlocks.sort((bb1, bb2) -> {
            if (bb1.getScore() < bb2.getScore()) {
                return 1;
            } else if (bb1.getScore() > bb2.getScore()){
                return -1;
            }
            return 0;
        });

        //get first k building blocks out
        for (int i = 0; i < k; ++i) {
            BuildingBlock bb = buildingBlocks.get(i);
            selectedBBs[bb.getOriginalIndex()] = 1;
            if (verbose) {
                System.out.println(bb.getBBstring() +" - recieved: "+bb.getScore()+" voting weight.");
            }
        }

        return selectedBBs;
    }

    @Override
    public String getName() {
        return k+"-highest";
    }

    //Wrapper class which simplifies this whole process a lot
    private class BuildingBlock {
        private GPNode BB;
        private String BBstring;
        private double score;
        private int originalIndex;

        public BuildingBlock(GPNode BB, double score, int originalIndex) {
            this.BB = BB;
            this.score = score;
            this.originalIndex = originalIndex;
        }

        public BuildingBlock(String BBString, double score, int originalIndex) {
            this.BBstring = BBString;
            this.score = score;
            this.originalIndex = originalIndex;
        }

        public GPNode getBB() { return BB; }

        public String getBBstring() { return BBstring; }

        public double getScore() { return score; }

        public int getOriginalIndex() { return originalIndex; }
    }
}
