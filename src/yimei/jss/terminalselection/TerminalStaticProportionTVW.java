package yimei.jss.terminalselection;

import ec.gp.GPNode;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.List;

/**
 * Created by dyska on 8/02/18.
 */
public class TerminalStaticProportionTVW extends TerminalSelectionStrategy {
    private double totalVotingWeight;
    private double proportion;

    public TerminalStaticProportionTVW(double proportion) {
        this.proportion = proportion;
    }

    public TerminalStaticProportionTVW(double totalVotingWeight, double proportion) {
        this.totalVotingWeight = totalVotingWeight;
        this.proportion = proportion;
    }

    @Override
    public void selectTerminals(List<GPNode> BBs, List<GPNode> selBBs,
                                List<DescriptiveStatistics> BBVotingWeightStats) {
        for (int i = 0; i < BBs.size(); i++) {
            double votingWeight = BBVotingWeightStats.get(i).getSum();
            if (votingWeight > proportion*totalVotingWeight) {
                selBBs.add(BBs.get(i));
//                System.out.println(BBs.get(i).makeCTree(false,true,
//                        true) +" - recieved: "+votingWeight+" voting weight.");
            }
        }
    }

    @Override
    public String getName() {
        return "Terminal-"+proportion+"x"+totalVotingWeight;
    }

    public void setTotalVotingWeight(double totalVotingWeight) {this.totalVotingWeight = totalVotingWeight; }
}
