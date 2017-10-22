#Run from command line with: "Rscript process_cleaned_results.R folder_name
#eg: Rscript process_cleaned_results_dynamic.R coevolution-fixed_test

args = commandArgs(trailingOnly=TRUE)
if (length(args) == 0) {
  stop("At least one argument must be supplied", call.=FALSE)
}

arg = "coevolution_modified_terminal_final"

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
    print(filename)
    gp_results = read.csv(filename,header=TRUE)
    best_fitnesses = as.numeric(unlist(gp_results["Best"]))
    fitnessString = ""
    for (fitness in best_fitnesses) {
      fitnessString = paste(fitnessString,fitness,",",sep="")
    }
    print(substring(fitnessString,0,nchar(fitnessString)-1))
    i = i + 1
  }
}



