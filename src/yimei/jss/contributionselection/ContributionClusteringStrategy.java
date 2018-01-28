package yimei.jss.contributionselection;

import ec.gp.GPIndividual;
import ec.gp.GPNode;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import java.util.List;
import weka.clusterers.SimpleKMeans;
import weka.core.*;

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
        int[][] instanceIDs = new int[selIndis.size()][BBs.size()];
        Instances data = convertMatrixToWeka(contributions, instanceIDs);

        if (data.numInstances() == 0) {
            //no positive contributions
            for (int s = 0; s < selIndis.size(); s++) {
                for (int i = 0; i < BBs.size(); i++) {
                    BBVotingWeightStats.get(i).addValue(0);
                }
            }
            return;
        }

        String[] options = new String[5];
        options[0] = "-I"; // max. iterations
        options[1] = "100";
        options[2] = "-O"; //preserve order of instances
        options[3] = "-N";
        options[4] = String.valueOf(numClusters);

        SimpleKMeans clusterer = new SimpleKMeans();
        try {
            clusterer.setOptions(options);
            clusterer.buildClusterer(data);
        } catch (Exception e) {
            e.printStackTrace();
        }

        int worstClusterIndex = getWorstClusterIndex(clusterer);

        //clustering complete, now need to find which cluster has the worst values
        //as this is the cluster which will have its contributions excluded
        for (int s = 0; s < selIndis.size(); s++) {
            for (int i = 0; i < BBs.size(); i++) {
                int instanceID = instanceIDs[s][i];
                if (instanceID == 0) {
                    //wasn't clustered, must have been non-positive contribution
                    BBVotingWeightStats.get(i).addValue(0);
                    //System.out.println("Contribution of subtree "+i+" to rule "+s+" was non-positive ("+(contributions[s][i]+")."));

                } else {
                    int clusterIndex = -1;
                    try {
                        clusterIndex = clusterer.getAssignments()[instanceID-1];
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (clusterIndex == -1) {
                        System.out.println("Clusterer couldn't find instance...");
                    }
                    if (clusterIndex == worstClusterIndex) {
                        //contribution was in bottom cluster
                        //System.out.println("Contribution of "+contributions[s][i]+" was positive, but was in the lowest cluster - index = "+clusterIndex);
                        BBVotingWeightStats.get(i).addValue(0);
                    } else {
                        //should get to vote!
                        BBVotingWeightStats.get(i).addValue(votingWeightStat.getElement(s));
                        //System.out.println("Contribution of "+contributions[s][i]+" was in cluster "+clusterIndex+" (not worst).");
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


    //Not my code!
    //Taken from: https://www.programcreek.com/java-api-examples/index.php?source_dir=lichee-master/LICHeE/src/lineage/AAFClusterer.java
    //Then adapted to suit this context
    public static Instances convertMatrixToWeka(double[][] data, int[][] instanceIDs) {
        //numFeatures should be one - Contribution
        int numFeatures = 1;
        int numRows = data.length;
        int numCols = data[0].length; //should be constant across all rows, so can arbitrarily pick first col

        int numObs = 0;
        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < numCols; j++) {
                double contribution = data[i][j];
                if (contribution > 0.0) {
                    numObs++;
                }
            }
        }

        // convert the data to WEKA format
        FastVector atts = new FastVector();
        atts.addElement(new Attribute("Contribution", 0));

        Instances dataSet = new Instances("AAF Data", atts, numObs);
        for (int i = 0; i < numObs; i++) {
            dataSet.add(new Instance(numFeatures));
        }

        int count = 0;
        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < numCols; j++) {
                double contribution = data[i][j];
                if (contribution > 0.0) {
                    dataSet.instance(count).setValue(0,contribution);
                    count++;
                    instanceIDs[i][j] = count;
                } else {
                    //didn't get selected
                    instanceIDs[i][j] = 0;
                }
            }
        }

        return dataSet;
    }

    public static int getWorstClusterIndex(SimpleKMeans clusterer) {
        int worstClusterIndex = -1;
        double lowestCentroidValue = java.lang.Double.POSITIVE_INFINITY;
        Instances centroids = clusterer.getClusterCentroids();
        //expecting numClusters
        for (int i = 0; i < centroids.numInstances(); ++i) {
            double centroidVal = centroids.instance(i).value(0);
            if (centroidVal < lowestCentroidValue) {
                worstClusterIndex = i;
                lowestCentroidValue = centroidVal;
            }
            //System.out.println("Cluster "+i+" had centroid value of: "+centroidVal);
        }
        //System.out.println("Lowest cluster "+worstClusterIndex+" with value of: "+lowestCentroidValue);
        return worstClusterIndex;
    }
}
