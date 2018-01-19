package yimei.jss.contributionselection;

import ec.gp.GPIndividual;
import ec.gp.GPNode;
import net.sf.javaml.core.Dataset;
import net.sf.javaml.core.DefaultDataset;
import net.sf.javaml.core.DenseInstance;
import net.sf.javaml.core.Instance;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import java.util.List;
import static yimei.jss.feature.FeatureUtil.clusterContributions;
import static yimei.jss.feature.FeatureUtil.findWorstCluster;

/**
 * Created by dyska on 18/01/18.
 */
public class ContributionClusteringStrategy extends ContributionSelectionStrategy {

    private int numClusters;

    public ContributionClusteringStrategy(int k) {
        this.numClusters = k;
    }

    public void selectContributions(double[][] contributions,
                                    List<GPIndividual> selIndis,
                                    List<GPNode> BBs,
                                    List<DescriptiveStatistics> BBVotingWeightStats,
                                    DescriptiveStatistics votingWeightStat) {

        Dataset data = new DefaultDataset();
        int[][] instanceIDs = new int[selIndis.size()][BBs.size()];

        for (int s = 0; s < selIndis.size(); s++) {
            for (int i = 0; i < BBs.size(); i++) {
                double[] c = new double[] {contributions[s][i]};

                //we have |selIndis| * |BB| individuals
                //want to cluster them, and assign them either large or small
                //need to create instances to add to dataset
                if (c[0] > 0.0) {
                    //no point including contribution if it has less than 0
                    Instance instance = new DenseInstance(c);
                    data.add(instance);
                    instanceIDs[s][i] = instance.getID();
                } else {
                    instanceIDs[s][i] = -1; //didn't get included
                }
            }
        }

        Dataset[] clusters = clusterContributions(data, numClusters);
        int worstClusterIndex = findWorstCluster(clusters);

        //clustering complete, now need to find which cluster has the worst values
        //as this is the cluster which will have its contributions excluded

        for (int s = 0; s < selIndis.size(); s++) {
            for (int i = 0; i < BBs.size(); i++) {
                int instanceID = instanceIDs[s][i];
                if (instanceID == -1) {
                    //wasn't clustered, must have been non-positive contribution
                    BBVotingWeightStats.get(i).addValue(0);
                    //System.out.println("Contribution of subtree "+i+" to rule "+s+" was non-positive.");
                } else {
                    boolean inWorstCluster = false;
                    //was clustered, now we just need to know whether it was in bottom cluster or not
                    Dataset worstCluster = clusters[worstClusterIndex];
                    for (int j = 0; j < worstCluster.size() && !inWorstCluster; ++j) {
                        if (worstCluster.get(j).getID() == instanceID) {
                            inWorstCluster = true;
                            BBVotingWeightStats.get(i).addValue(0);
                        }
                    }
                    if (!inWorstCluster) {
                        //should get to vote!
                        BBVotingWeightStats.get(i).addValue(votingWeightStat.getElement(s));
                        System.out.println("Rule "+s+" voted for building block "+i+
                        " with weight "+votingWeightStat.getElement(s)+".");
                    }
                }
            }
        }
    }

    @Override
    public String getName() {
        return numClusters+"-Clustering";
    }
}
