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
    public void selectTerminals(List<GPNode> terminals, List<GPNode> selTerminals,
                                List<DescriptiveStatistics> terminalVotingWeightStats) {
        for (int i = 0; i < terminals.size(); i++) {
            double votingWeight = terminalVotingWeightStats.get(i).getSum();
            if (votingWeight > proportion*totalVotingWeight) {
                selTerminals.add(terminals.get(i));
//                System.out.println(BBs.get(i).makeCTree(false,true,
//                        true) +" - recieved: "+votingWeight+" voting weight.");
            }
        }

        if (selTerminals.isEmpty()) {
            //this is not an option, will break things down the line as terminal set will be empty
            //should just pick highest voting weight out of all terminals
            double highestVotingWeight = terminalVotingWeightStats.get(0).getSum();
            GPNode bestTerminal = terminals.get(0);
            for (int i = 1; i < terminals.size(); i++) {
                double votingWeight = terminalVotingWeightStats.get(i).getSum();
                if (votingWeight > highestVotingWeight) {
                    highestVotingWeight = votingWeight;
                    bestTerminal = terminals.get(i);
                }
            }
            //as no terminals surpassed requirements of total voting weight proportion, only
            //1 will be added
            selTerminals.add(bestTerminal);
        }
    }

    @Override
    public String getName() {
        return "Terminal-"+proportion+"x"+totalVotingWeight;
    }

    public void setTotalVotingWeight(double totalVotingWeight) {this.totalVotingWeight = totalVotingWeight; }
}
