package yimei.jss.gp;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static yimei.jss.FJSSMain.getFileNames;

/**
 * Created by dyska on 21/05/17.
 */
public class GPMain {

    public static void main(String[] args) {
        boolean isTest = true;
        int maxTests = 1;
        boolean isDynamic = true;

        String workingDirectory = (new File("")).getAbsolutePath();

        double[] utilLevels = new double[] {0.95};
        String[] objectives = new String[] {"max-flowtime"};
        for (double utilLevel: utilLevels) {
            for (String objective: objectives) {
                List<String> gpRunArgs = new ArrayList<>();
                //include path to params file
                gpRunArgs.add("-file");
                if (isDynamic) {
                    String contributionSelectionStrategy = "3-Clustering";
                    String bbSelectionStrategy = "top-1";

                    //Calling -jar GPJSS-FC.jar -file params/fcgp-simplegp-dynamic.params -p eval.problem.eval-model.sim-models.0.util-level=0.95 -p eval.problem.eval-model.objectives.0=max-flowtime -p contributionSelectionStrategy=2-Clustering -p bbSelectionStrategy=2-Clustering -p seed.0=13 -p stat.file=job.13.out.stat

                    //gpRunArgs.add(workingDirectory+"/src/yimei/jss/algorithm/featureconstruction/fcgp-simplegp-dynamic.params");
                    gpRunArgs.add(workingDirectory+"/src/yimei/jss/algorithm/featureconstruction/fcgp-coevolutiongp-dynamic.params");
                    //gpRunArgs.add(workingDirectory+"/src/yimei/jss/algorithm/coevolutiongp/coevolutiongp-dynamic.params");
                    //gpRunArgs.add(workingDirectory+"/src/yimei/jss/algorithm/simplegp/simplegp-dynamic.params");
                    gpRunArgs.add("-p");
                    gpRunArgs.add("eval.problem.eval-model.sim-models.0.util-level="+utilLevel);
                    gpRunArgs.add("-p");
                    gpRunArgs.add("eval.problem.eval-model.objectives.0="+objective);
                    gpRunArgs.add("-p");
                    gpRunArgs.add("bbSelectionStrategy="+bbSelectionStrategy);
                    gpRunArgs.add("-p");
                    gpRunArgs.add("contributionSelectionStrategy="+contributionSelectionStrategy);
                    gpRunArgs.add("-p");
                    for (int i = 0; i < 1 && i <= maxTests; ++i) {
                        gpRunArgs.add("seed.0="+String.valueOf(i));
                        gpRunArgs.add("-p");
                        gpRunArgs.add("stat.file=job."+String.valueOf(i)+".out.stat");
                        //convert list to array
                        GPRun.main(gpRunArgs.toArray(new String[0]));
                        //now remove the seed, we will add new value in next loop
                        gpRunArgs = gpRunArgs.subList(0,gpRunArgs.size()-3);
                    }
                } else {
                    //gpRunArgs.add("workingDirectory+/src/yimei/jss/algorithm/featureselection/fsgp-simplegp-static.params");
                    //gpRunArgs.add("workingDirectory+/src/yimei/jss/algorithm/featureselection/fsgp-coevolutiongp-static.params");
                    //gpRunArgs.add("workingDirectory+/src/yimei/jss/algorithm/simplegp/simplegp.params");
                    gpRunArgs.add("workingDirectory+/src/yimei/jss/algorithm/coevolutiongp/coevolutiongp.params");
                    //gpRunArgs.add("-p");
                    //gpRunArgs.add("terminals-from.0=static-coevolution/data-FJSS-Hurink_Data-Text-vdata-orb9-SEQUENCING.csv");
                    //gpRunArgs.add("-p");
                    //gpRunArgs.add("terminals-from.1=static-coevolution/data-FJSS-Hurink_Data-Text-vdata-orb9-ROUTING.csv");

                    //gpRunArgs.add("workingDirectory+/src/yimei/jss/algorithm/simplegp/simplegp.params");
                    //gpRunArgs.add("workingDirectory+/src/yimei/jss/algorithm/coevolutiongp/coevolutiongp.params");
                    gpRunArgs.add("-p");

                    //static FJSS, so using file paths
                    String path = "";
                    if (args.length > 0) {
                        //allow more specific folder or file paths to be used
                        path = args[0];
                    }
                    path = (new File("")).getAbsolutePath() + "/data/FJSS/" + path;

                    List<String> fileNames = getFileNames(new ArrayList(), Paths.get(path), ".fjs");

                    for (String fileName: fileNames) {
                        //worry about saving output later
                        gpRunArgs.add("filePath="+fileName);
                        gpRunArgs.add("-p");
                        for (int i = 1; i <= 30 && i <= maxTests; ++i) {
                            gpRunArgs.add("seed.0="+String.valueOf(i));
                            //convert list to array
                            GPRun.main(gpRunArgs.toArray(new String[0]));
                            //now remove the seed, we will add new value in next loop
                            gpRunArgs.remove(gpRunArgs.size()-1);
                        }
                        //now remove filePath etc
                        gpRunArgs = gpRunArgs.subList(0,3);
                        if (isTest) {
                            break;
                        }
                    }
                }
            }
        }
    }
}
