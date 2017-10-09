#Run from command line with: "Rscript process_cleaned_results.R folder_name
#eg: Rscript process_results_by_directory.R coevolve

args = commandArgs(trailingOnly=TRUE)
if (length(args) == 0) {
  stop("At least one argument must be supplied", call.=FALSE)
}

a = args[1]

base_directory = "/Users/dyska/Desktop/Uni/COMP489/GPJSS/"
grid_directory = paste(base_directory, "grid_results/static/",sep="")
input_dir = paste(grid_directory,"cleaned/",sep="")
output_dir = paste(grid_directory,"processed/",sep="")
bounds_dir = paste(base_directory,"fjss_bounds/",sep="")
output_file = paste(a,"-results-directory.csv",sep="")

input_dir = paste(input_dir, a, sep="")
output_dir = paste(output_dir, a, sep="")

#lets load in the lower and upper bounds for each fjss instance
setwd(bounds_dir)
fjss_bounds = read.csv("fjss_bounds.csv",header=TRUE)

#now lets read in filenames from input directory
setwd(input_dir)
filenames = list.files(input_dir)

#create the matrix which will store all our results
output_matrix = matrix(, nrow = 7, ncol = 4)
colnames(output_matrix) = c("file directory","mean best/lb","mean med/lb","mean mean/lb")

#lets process our results, and save them in the matrix we made
#for each data set, we want the mean best/lb, mean med/lb, mean mean/lb, mean std dev

i = 1
current_directory = "Barnes"
sum_best_lb = 0
sum_med_lb = 0
sum_mean_lb = 0
sum_std_dev = 0
num_vals = 0
for (filename in filenames) {
  if (endsWith(filename,".csv") && startsWith(filename,"FJSS")) {
    #shorten the file name to match instance names in bounds table
    instance_name = substring(filename,6,nchar(filename)-4)
    
    #get the bounds out of the table
    bounds = fjss_bounds[which(fjss_bounds["File"] == instance_name),]
    lb = bounds[["LB"]]
    ub = bounds[["UB"]]
    
    gp_results = read.csv(filename,header=TRUE)
    best_makespans = as.numeric(unlist(gp_results["Best"]))
    best_makespan_seed = which.min(best_makespans)
    best_makespan = best_makespans[best_makespan_seed]
    best_by_lb = best_makespan/lb
    med_makespan = median(best_makespans)
    med_by_lb = med_makespan/lb
    mean_makespan = mean(best_makespans)
    mean_by_lb = mean_makespan/lb
    sd_makespan = sd(best_makespans)
    
    if (startsWith(instance_name,current_directory)) {
      #increment current counters
      sum_best_lb = sum_best_lb + best_by_lb
      sum_med_lb = sum_med_lb + med_by_lb
      sum_mean_lb = sum_mean_lb + mean_by_lb
      num_vals = num_vals + 1
    } else {
      #add existing results to matrix
      avg_best_lb = sum_best_lb / num_vals
      avg_med_lb = sum_med_lb / num_vals
      avg_mean_lb = sum_mean_lb / num_vals
      output_matrix[i,] = c(current_directory,avg_best_lb,
                            avg_med_lb,avg_mean_lb)
      i = i + 1
      #change file directory we are tracking
      if (startsWith(instance_name,"Brandimarte")) {
        current_directory = "Brandimarte"
      } else if (startsWith(instance_name,"Dauzere")) {
        current_directory = "Dauzere"
      } else if (startsWith(instance_name,"Hurink_Data-Text-edata")) {
        current_directory = "Hurink_Data-Text-edata"
      } else if (startsWith(instance_name,"Hurink_Data-Text-rdata")) {
        current_directory = "Hurink_Data-Text-rdata"
      } else if (startsWith(instance_name,"Hurink_Data-Text-sdata")) {
        current_directory = "Hurink_Data-Text-sdata"
      } else if (startsWith(instance_name,"Hurink_Data-Text-vdata")) {
        current_directory = "Hurink_Data-Text-vdata"
      } else {
        print("Something has gone wrong...")
      }
      #reset counters
      sum_best_lb = 0
      sum_med_lb = 0
      sum_mean_lb = 0
      num_vals = 0
    }
  }
}

#will have vdata data to store
#add existing results to matrix
avg_best_lb = sum_best_lb / num_vals
avg_med_lb = sum_med_lb / num_vals
avg_mean_lb = sum_mean_lb / num_vals
output_matrix[i,] = c(current_directory,avg_best_lb,
                      avg_med_lb,avg_mean_lb)

#time to save these results
setwd(output_dir)
write.csv(output_matrix,file=output_file)

print(paste("Wrote results to directory",output_dir,"filename: ",output_file))




