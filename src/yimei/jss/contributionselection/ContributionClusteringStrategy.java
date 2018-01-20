package yimei.jss.contributionselection;

import ec.app.edge.func.Double;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.ArrayList;
import java.util.List;
import static yimei.jss.feature.FeatureUtil.clusterContributions;
import static yimei.jss.feature.FeatureUtil.findWorstCluster;
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

        String[] options = new String[5];
        options[0] = "-I"; // max. iterations
        options[1] = "100";
        options[2] = "-O"; //preserve order of instances
        options[3] = "-N";
        options[4] = String.valueOf(numClusters); //set number of clusters

        SimpleKMeans clusterer = new SimpleKMeans();   // new instance of clusterer
        try {
            clusterer.setOptions(options);     // set the options
            clusterer.buildClusterer(data);
        } catch (Exception e) {
            e.printStackTrace();
        }

        int getWorstIndex = getWorstClusterIndex(clusterer);

//
//
////        Dataset data = new DefaultDataset();
//        //Instances data = new Instances();
//        List<Instance> instances = new ArrayList<>();
//        int[][] instanceIDs = new int[selIndis.size()][BBs.size()];
////
//        for (int s = 0; s < selIndis.size(); s++) {
//            for (int i = 0; i < BBs.size(); i++) {
//                double c = contributions[s][i];
//
//                //we have |selIndis| * |BB| individuals
//                //want to cluster them, and assign them either large or small
//                //need to create instances to add to dataset
//                if (c > 0.0) {
//                    //no point including contribution if it has less than 0
//                    Instance instance = new SparseInstance(1);
//                    instance.setValue(0,c);
//                    instances.add(instance);
//                    instanceIDs[s][i] = 1;
//                } else {
//                    instanceIDs[s][i] = 0; //didn't get included
//                }
//            }
//        }
//
//        Dataset[] clusters = clusterContributions(data, numClusters);
//        int worstClusterIndex = findWorstCluster(clusters);
//
//        //clustering complete, now need to find which cluster has the worst values
//        //as this is the cluster which will have its contributions excluded
//
        for (int s = 0; s < selIndis.size(); s++) {
            for (int i = 0; i < BBs.size(); i++) {
                int instanceID = instanceIDs[s][i];
                if (instanceID == 0) {
                    //wasn't clustered, must have been non-positive contribution
                    BBVotingWeightStats.get(i).addValue(0);
                    //System.out.println("Contribution of subtree "+i+" to rule "+s+" was non-positive.");
                } else {
                    boolean inWorstCluster = false;
                    int cluster = -1;
                    try {
                        cluster = clusterer.getAssignments()[instanceID-1];
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    System.out.println("Cluster for "+instanceID+" = "+cluster);
//                    //was clustered, now we just need to know whether it was in bottom cluster or not
//                    Dataset worstCluster = clusters[worstClusterIndex];
//                    for (int j = 0; j < worstCluster.size() && !inWorstCluster; ++j) {
//                        if (worstCluster.get(j).getID() == instanceID) {
//                            inWorstCluster = true;
//                            BBVotingWeightStats.get(i).addValue(0);
//                        }
//                    }
//                    if (!inWorstCluster) {
//                        //should get to vote!
//                        BBVotingWeightStats.get(i).addValue(votingWeightStat.getElement(s));
//                        System.out.println("Rule "+s+" voted for building block "+i+
//                        " with weight "+votingWeightStat.getElement(s)+".");
//                    }
                }
            }
        }
        System.out.println();
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
            Attribute a = centroids.instance(i).attribute(0);
            //a.
            //if ((doub< lowestCentroidValue) {

            //}
        }

        return 0;
    }
}
