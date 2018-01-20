package yimei.jss.bbselection;

import ec.gp.GPNode;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import weka.clusterers.SimpleKMeans;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dyska on 13/01/18.
 */
public class BBClusteringStrategy extends BBSelectionStrategy {
    int numClusters;

    public BBClusteringStrategy(int k) {
        this.numClusters = k;
    }

    @Override
    public void selectBuildingBlocks(List<GPNode> BBs,
                                     List<GPNode> selBBs,
                                     List<DescriptiveStatistics> BBVotingWeightStats,
                                     List<Double> selBBsVotingWeights) {
        //Have a selection of building blocks, each with a voting score
        //Want to create k clusters, and select all BBs which fall in the top cluster

        Instances data = convertToWekaFormat(BBVotingWeightStats);

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

        int bestClusterIndex = getBestClusterIndex(clusterer);
        int[] assignments = null;
        try {
            assignments = clusterer.getAssignments();
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int i = 0; i < data.numInstances(); ++i) {
            if (assignments[i] == bestClusterIndex) {
                //going to include this building block
                GPNode bb = BBs.get(i);
                double votes = BBVotingWeightStats.get(i).getSum();

                selBBs.add(bb);
                selBBsVotingWeights.add(votes);

                System.out.println(bb.makeCTree(false,true,
                        true) +" - selected with: "+votes+".");
            }
        }
    }

    @Override
    public int[] selectBuildingBlocks(List<String> BBs, List<Double> BBVotingWeightStats, boolean verbose) {
        int[] selectedBBs = new int[BBs.size()];
        //Have a selection of building blocks, each with a voting score
        //Want to create k clusters, and select all BBs which fall in the top cluster

        Instances data = convertToWekaFormatDouble(BBVotingWeightStats);

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

        int bestClusterIndex = getBestClusterIndex(clusterer);
        int[] assignments = null;
        try {
            assignments = clusterer.getAssignments();
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int i = 0; i < data.numInstances(); ++i) {
            if (assignments[i] == bestClusterIndex) {
                //going to include this building block
                String bb = BBs.get(i);
                double votes = BBVotingWeightStats.get(i);
                selectedBBs[i] = 1;
                if (verbose) {
                    System.out.println(bb +" - selected with: "+votes+".");
                }
            }
        }

        return selectedBBs;
    }

    @Override
    public String getName() {
        return numClusters+"-Clustering";
    }

    public Instances convertToWekaFormat(List<DescriptiveStatistics> BBVotingWeightStats) {
        List<Double> votingStats = new ArrayList<>();
        for (int i = 0; i < BBVotingWeightStats.size(); ++i) {
            votingStats.add(BBVotingWeightStats.get(i).getSum());
        }
        return convertToWekaFormatDouble(votingStats);
    }

    public Instances convertToWekaFormatDouble(List<Double> BBVotingWeightStats) {
        //numFeatures should be one - Contribution
        int numFeatures = 1;
        int numObs = BBVotingWeightStats.size();

        // convert the data to WEKA format
        FastVector atts = new FastVector();
        atts.addElement(new Attribute("Score", 0));

        Instances dataSet = new Instances("AAF Data", atts, numObs);
        for (int i = 0; i < numObs; i++) {
            dataSet.add(new weka.core.Instance(numFeatures));
        }

        for (int i = 0; i < BBVotingWeightStats.size(); i++) {
            double score = BBVotingWeightStats.get(i);
            dataSet.instance(i).setValue(0,score);
        }

        return dataSet;
    }

    public static int getBestClusterIndex(SimpleKMeans clusterer) {
        int bestClusterIndex = -1;
        double highestCentroidValue = Double.NEGATIVE_INFINITY;
        Instances centroids = clusterer.getClusterCentroids();

        //if the clusterer is unable to cluster with numClusters,
        //it will reduce the number of clusters until it can
        //if there are all 0's, then there will only be one cluster
        //and this should not be accepted as the 'best cluster'
        if (centroids.numInstances() == 1) {
            return 0; //don't accept the only cluster as the best cluster
        }

        //expecting numClusters
        for (int i = 0; i < centroids.numInstances(); ++i) {
            double centroidVal = centroids.instance(i).value(0);
            if (centroidVal > highestCentroidValue) {
                bestClusterIndex = i;
                highestCentroidValue = centroidVal;
            }
            //System.out.println("Cluster "+i+" had centroid value of: "+centroidVal);
        }
        //System.out.println("Best cluster "+bestClusterIndex+" with value of: "+highestCentroidValue);
        return bestClusterIndex;
    }
}
