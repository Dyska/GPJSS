#Want to peform a paired t test on each FJSS instance for two evolution options
#Each FJSS is run 30 times for each evolution option,
#Will be looking at data in /cleaned/ folders
#Will have to specify two folder names within cleaned
#eg: Rscript t_test.R static coevolve simple
#eg: Rscript t_test.R dynamic coevolution simple

args = commandArgs(trailingOnly=TRUE)
if (length(args) != 3) {
  stop("Three arguments must be supplied.", call.=FALSE)
}

simulation_type = "static"
a = "coevolve"
b = "simple"
#simulation_type = args[1]
#a = args[2]
#b = args[3]

base_directory = "/Users/dyska/Desktop/Uni/COMP489/GPJSS/"
grid_directory = paste(base_directory, "grid_results/",simulation_type,"/",sep="")
input_dir_a = paste(grid_directory,"cleaned/",a,sep="")
input_dir_b = paste(grid_directory,"cleaned/",b,sep="")
output_dir = paste(grid_directory,"processed/t_tests/",sep="")
output_file = paste(a,"-",b,"-results.csv",sep="")

#First, read all filenames from one of the input directories
setwd(input_dir_a)
filenames = list.files(input_dir_a)

#create the matrix which will store all our results
output_matrix = matrix(, nrow = length(filenames), ncol = 4)
colnames(output_matrix) = c("filename",paste(a,">",b,sep=""),paste(a,"=",b,sep=""),paste(a,"<",b,sep=""))

#lets process our results, and save them in the matrix we made

p_value_function = function(a, b) {
  constant_arrays = TRUE
  a_1 = a[1]
  b_1 = b[1]
  i = 2
  while (i <= 30 && constant_arrays) {
    if (!(a_1 == a[i] && b_1 == b[i])) {
      constant_arrays = FALSE
    }
    i = i + 1
  }
  if (constant_arrays) {
    #both arrays are constant, so t test won't work
    if (a_1 == b_1) {
      #all values in both arrays are constant - can't say one
      #is better than the other
      return (NA)
    } else if (a_1 < b_1) {
      #a has better makespans
      return (0)
    } else {
      #b has better makespans
      return (1)
    }
  } else {
    wilcox_test = wilcox.test(a,b,paired=FALSE,alternative = "l")
    return (wilcox_test$p.value)
  }
}  

i = 1
for (filename in filenames) {
  if (endsWith(filename,".csv")) {
    a_better = 0
    b_better = 0
    equal = 0
    
    setwd(input_dir_a)
    gp_results_a = read.csv(filename,header=TRUE)
    best_makespans_a = as.numeric(unlist(gp_results_a["Best"]))
    
    setwd(input_dir_b)
    #should be a filename with that exact same name
    gp_results_b = read.csv(filename,header=TRUE)
    best_makespans_b = as.numeric(unlist(gp_results_b["Best"]))
    
    p_val_a = p_value_function(best_makespans_a,best_makespans_b)
    if (is.na(p_val_a)) {
      equal = 1
    } else if (p_val_a >= 0.05) {
      #either neither a and b is better, or b is better
      p_val_b = p_value_function(best_makespans_b,best_makespans_a)
      if (p_val_b < 0.05) {
        #b is better than a for this file
        b_better = 1
      } else {
        #equal
        equal = 1
      }
    } else {
      a_better = 1
    }
    
    output_matrix[i,] = c(filename,a_better,equal,b_better)  
    i = i + 1
  }
}

#time to save these results
setwd(output_dir)
write.csv(output_matrix,file=output_file)

print(paste("Wrote t-test results to directory:",output_dir))
print(paste("Filename:",output_file))
a_better = sum(output_matrix[,2]==1)
b_better = sum(output_matrix[,4]==1)
equal = sum(output_matrix[,3]==1)
print(paste(a,"was better in",a_better,"files."))
print(paste(b,"was better in",b_better,"files."))
print(paste("They were equal in",equal,"files."))