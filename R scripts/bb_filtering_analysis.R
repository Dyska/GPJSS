#Run from command line with: "Rscript process_cleaned_results.R folder_name
#eg: Rscript process_cleaned_results_dynamic.R coevolution-fixed_test

arg <- "bb_filtering_results.csv"
GP_variations <- c("SeqGP","CCGP")
util_levels <- c(0.85,0.95)
objectives <- c("mean-flowtime","max-flowtime","mean-weighted-flowtime")
rule_types <- c("Sequencing","Routing")

base_directory <- "/Users/dyska/Desktop/Uni/COMP489/GPJSS/grid_results/outputs/result"
input_file <- paste(base_directory,arg,sep="/")
output_file <- paste(arg,"_analysis.csv",sep="")

#lets read in filenames from input directory
setwd(base_directory)

#create the matrix which will store all our results
#lets process our results, and save them in the matrix we made

results <- data.frame(read.csv(input_file,header=TRUE))
output_matrix <- matrix(, nrow = 18, ncol = 8)
colnames(output_matrix) <- c("gp","objective","util_level","rule_type","mean_before","std_dev_before","mean_after","std_dev_after")

#GP
#Util level
#Obective
i <- 1
for (gp in GP_variations) {
  for (rule_type in rule_types) {
    if (!(gp == 'SeqGP' && rule_type == 'Routing')) {
      for (objective in objectives) {
        for (util_level in util_levels) {
          matching_rows = results[results$"GP" == gp
                                  & results$"Util.Level" == util_level
                                  & results$"Objective" == objective, ]
          if (rule_type == "Sequencing") {
            mean_before <- mean(matching_rows$Seq0)
            std_dev_before <- sd(matching_rows$Seq0)
            mean_after <- mean(matching_rows$Seq1)
            std_dev_after <- sd(matching_rows$Seq1)
          } else {
            mean_before <- mean(matching_rows$Routing0)
            std_dev_before <- sd(matching_rows$Routing0)
            mean_after <- mean(matching_rows$Routing1)
            std_dev_after <- sd(matching_rows$Routing1)
          }

          output_matrix[i,] <- c(gp,objective,util_level,rule_type,
                                mean_before,std_dev_before,mean_after,std_dev_after)
          i <- i + 1
        }
      }
    }
  }
}

#time to save these results
write.csv(output_matrix,file=output_file)

print(paste("Wrote results to: ",output_file))
