package yimei.jss.terminalselection;

import ec.gp.GPNode;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.List;

/**
 * Created by dyska on 8/02/18.
 */
public abstract class TerminalSelectionStrategy {

    public abstract void selectTerminals(List<GPNode> BBs, List<GPNode> selBBs, List<DescriptiveStatistics> BBVotingWeightStats);

    public abstract String getName();



    public static TerminalSelectionStrategy selectStrategy(String name) {
        name = name.toLowerCase();
        if (name.startsWith("terminal-") && name.contains("x")) {
            //"terminal>"+proportion+"*"+totalVotingWeight;
            //This is a tricky one... - totalVotingWeight may have been set, or not
            //Will try parse the total voting weight, but if it fails, will catch the exception and
            //use other constructor
            int multIndex = name.indexOf('x');
            double proportion = Double.parseDouble(name.substring("terminal-".length(), multIndex));

            try {
                double totalVotingWeight = Double.parseDouble(name.substring(multIndex + 1));
                return new TerminalStaticProportionTVW(totalVotingWeight, proportion);
            } catch (NumberFormatException n) {
                return new TerminalStaticProportionTVW(proportion);
            }
        }

        return null;
    }

}
