#Want to peform a paired t test on each FJSS instance for two evolution options
#Each FJSS is run 30 times for each evolution option,
#Will be looking at data in /cleaned/ folders
#Will have to specify two folder names within cleaned
#eg: Rscript paired_t_test.R fjss_coevolve_fixed fjss_hardcoded_results_updated

args = commandArgs(trailingOnly=TRUE)
if (length(args) != 2) {
  stop("Two arguments must be supplied.", call.=FALSE)
}

a = "fjss_coevolve_fixed"
b = "fjss_hardcoded_results_updated"

base_directory = "/Users/dyska/Desktop/Uni/COMP489/GPJSS/"
grid_directory = paste(base_directory, "grid_results/",sep="")
input_dir_a = paste(grid_directory,"cleaned/",a,sep="")
input_dir_b = paste(grid_directory,"cleaned/",b,sep="")
output_dir = paste(grid_directory,"processed/paired_t_tests/",sep="")
output_file = paste(a,"-",b,"-results.csv",sep="")

#First, read all filenames from one of the input directories
setwd(input_dir_a)
filenames = list.files(input_dir_a)

#create the matrix which will store all our results
output_matrix = matrix(, nrow = length(filenames), ncol = 2)
colnames(output_matrix) = c("filename","p-value")

#lets process our results, and save them in the matrix we made

p_value_fn <- function(a, b) {
  obj<-try(t.test(a,b,paired=TRUE), silent=TRUE)
  if (is(obj, "try-error")) return(NA) else return(obj$p.value)
}  

i = 1
for (filename in filenames) {
  if (endsWith(filename,".csv") && startsWith(filename,"FJSS")) {

    setwd(input_dir_a)
    gp_results_a = read.csv(filename,header=TRUE)
    best_makespans_a = as.numeric(unlist(gp_results_a["Best"]))
    
    setwd(input_dir_b)
    #should be a filename with that exact same name
    gp_results_b = read.csv(filename,header=TRUE)
    best_makespans_b = as.numeric(unlist(gp_results_b["Best"]))
  
    p = p_value_fn(best_makespans_a,best_makespans_b)
    output_matrix[i,] = c(filename,p)  
    i = i + 1
  }
}

#time to save these results
setwd(output_dir)
write.csv(output_matrix,file=output_file)

print(paste("Wrote t-test results to directory",output_dir,"filename: ",output_file))
