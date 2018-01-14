package yimei.jss.bbselection;

import ec.gp.GPNode;
import net.sf.javaml.clustering.KMeans;
import net.sf.javaml.core.Dataset;
import net.sf.javaml.core.DefaultDataset;
import net.sf.javaml.core.DenseInstance;
import net.sf.javaml.core.Instance;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.List;

/**
 * Created by dyska on 13/01/18.
 */
public class ClusteringStrategy implements BBSelectionStrategy {
    int numClusters;

    public ClusteringStrategy(int k) {
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

        KMeans km = new KMeans(numClusters);
        Dataset[] clusters = km.cluster(data);

//        for (int i = 0; i < clusters.length; ++i) {
//            Dataset d = clusters[i];
//            d.sort((o1, o2) -> {
//                if (o1.get(0) > o2.get(0)) {
//                    return 1;
//                } else if (o1.get(0) < o2.get(0)){
//                    return -1;
//                }
//                return 0;
//            });
//            System.out.println("Cluster "+(i+1)+" has "+d.size()+
//                    " values, ranging from "+d.get(0)+" to "+d.get(d.size()-1));
//        }

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

        //Have a selection of building blocks, each with a voting score
        //Want to create k clusters, and select all BBs which fall in the top cluster

        Dataset data = new DefaultDataset();
        int startIndex = -1;

        for (int i = 0; i < BBVotingWeightStats.size(); ++i) {
            double[] score = new double[] {BBVotingWeightStats.get(i)};
            Instance d = new DenseInstance(score);
            if (data.isEmpty()) {
                startIndex = d.getID();
            }
            data.add(d);
        }

        KMeans km = new KMeans(numClusters);
        Dataset[] clusters = km.cluster(data);

//        for (int i = 0; i < clusters.length; ++i) {
//            Dataset d = clusters[i];
//            d.sort((o1, o2) -> {
//                if (o1.get(0) > o2.get(0)) {
//                    return 1;
//                } else if (o1.get(0) < o2.get(0)){
//                    return -1;
//                }
//                return 0;
//            });
//            System.out.println("Cluster "+(i+1)+" has "+d.size()+
//                    " values, ranging from "+d.get(0)+" to "+d.get(d.size()-1));
//        }

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
