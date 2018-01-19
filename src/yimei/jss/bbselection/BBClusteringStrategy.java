package yimei.jss.bbselection;

import ec.gp.GPNode;
import net.sf.javaml.clustering.KMeans;
import net.sf.javaml.core.Dataset;
import net.sf.javaml.core.DefaultDataset;
import net.sf.javaml.core.DenseInstance;
import net.sf.javaml.core.Instance;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

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

        Dataset data = new DefaultDataset();
        int startIndex = -1;

        for (int i = 0; i < BBVotingWeightStats.size(); ++i) {
            double[] score = new double[] {BBVotingWeightStats.get(i).getSum()};
            Instance d = new DenseInstance(score);
            if (data.isEmpty()) {
                startIndex = d.getID();
            }
            data.add(d);
        }

        KMeans km = new KMeans(numClusters,100);
        Dataset[] clusters = km.cluster(data);

        int bestClusterIndex = findBestCluster(clusters);
        Dataset bestCluster = clusters[bestClusterIndex];

        for (int i = 0; i < bestCluster.size(); ++i) {
            DenseInstance instance = (DenseInstance) bestCluster.get(i);
            int id = instance.getID()-startIndex;
            GPNode bb = BBs.get(id);
            double votes = BBVotingWeightStats.get(id).getSum();

            selBBs.add(bb);
            selBBsVotingWeights.add(votes);

            System.out.println(BBs.get(id).makeCTree(false,true,
                    true) +" - selected with: "+votes+".");
        }
    }

    @Override
    public int[] selectBuildingBlocks(List<String> BBs, List<Double> BBVotingWeightStats, boolean verbose) {
        int[] selectedBBs = new int[BBs.size()];
        int numClusters = this.numClusters;

        //Have a selection of building blocks, each with a voting score
        //Want to create k clusters, and select all BBs which fall in the top cluster

        Dataset data = new DefaultDataset();
        int startIndex = -1;
        List<Double> uniqueScores = new ArrayList();

        for (int i = 0; i < BBVotingWeightStats.size(); ++i) {
            double[] score = new double[] {BBVotingWeightStats.get(i)};
            if (!uniqueScores.contains(score[0])) {
                uniqueScores.add(score[0]);
            }
            Instance d = new DenseInstance(score);
            if (data.isEmpty()) {
                startIndex = d.getID();
            }
            data.add(d);
        }
        int numUniqueScores = uniqueScores.size();

        if (numUniqueScores+1 < numClusters) {
            //if we have 10 items, 9 of which are 0.0 and 1 is 1.0
            //then trying to break it into 3 clusters will not work (below will run indefinitely)
            //doing this is a bit hacky, but works for our purposes
            System.out.println("Reducing num clusters to "+(numUniqueScores+1)+" for this run.");
            numClusters = numUniqueScores+1;
        }

        KMeans km = new KMeans(numClusters);
        Dataset[] clusters = km.cluster(data);

        int bestClusterIndex = findBestCluster(clusters);
        Dataset bestCluster = clusters[bestClusterIndex];

        for (int i = 0; i < bestCluster.size(); ++i) {
            DenseInstance instance = (DenseInstance) bestCluster.get(i);
            int id = instance.getID()-startIndex;
            String bb = BBs.get(id);
            double votes = BBVotingWeightStats.get(id);
            selectedBBs[id] = 1;
            if (verbose) {
                System.out.println(BBs.get(id) +" - selected with: "+votes+".");
            }
        }

        return selectedBBs;
    }

    @Override
    public String getName() {
        return numClusters+"-Clustering";
    }

    //helper method
    private int findBestCluster(Dataset[] clusters) {
        //need to find the cluster with the highest values
        //clusters will have distinct ranges - any value from a will be lower than any value from b
        //therefore can pick any member arbitrarily
        Double highest = clusters[0].instance(0).get(0);
        int bestClusterIndex = 0;
        for (int i = 1; i < clusters.length; ++i) {
            Double instance = clusters[i].instance(0).get(0);
            if (instance > highest) {
                bestClusterIndex = i;
                highest = instance;
            }
        }
        return bestClusterIndex;
    }
}
