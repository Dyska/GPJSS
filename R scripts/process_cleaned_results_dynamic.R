#Run from command line with: "Rscript process_cleaned_results.R folder_name
#eg: Rscript process_cleaned_results_dynamic.R coevolution-fixed_test

args = commandArgs(trailingOnly=TRUE)
if (length(args) == 0) {
  stop("At least one argument must be supplied", call.=FALSE)
}

arg = "simple-fixed"

base_directory = "/Users/dyska/Desktop/Uni/COMP489/GPJSS/"
grid_directory = paste(base_directory, "grid_results/dynamic/",sep="")
input_dir = paste(grid_directory,"cleaned/",sep="")
output_dir = paste(grid_directory,"processed/",sep="")
output_file = paste(arg,"-results.csv",sep="")

input_dir = paste(input_dir, arg, sep="")
output_dir = paste(output_dir, arg, sep="")

#lets read in filenames from input directory
setwd(input_dir)
filenames = list.files(input_dir)

#create the matrix which will store all our results
output_matrix = matrix(, nrow = length(filenames), ncol = 5)
colnames(output_matrix) = c("filename","best","median","mean","std dev")

#lets process our results, and save them in the matrix we made

i = 1
for (filename in filenames) {
  if (endsWith(filename,".csv")) {
    gp_results = read.csv(filename,header=TRUE)
    best_fitnesses = as.numeric(unlist(gp_results["Best"]))
    best_fitness_seed = which.min(best_fitnesses)
    best_fitness = best_fitnesses[best_fitness_seed]
    med_fitness = median(best_fitnesses)
    mean_fitness = mean(best_fitnesses)
    sd_fitness = sd(best_fitnesses)
    output_matrix[i,] = c(filename,best_fitness,med_fitness,mean_fitness,sd_fitness)
    i = i + 1
  }
}

#time to save these results
setwd(output_dir)
write.csv(output_matrix,file=output_file)

print(paste("Wrote results to directory",output_dir,"filename: ",output_file))




