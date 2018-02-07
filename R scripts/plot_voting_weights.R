#Read in libraries
library(lattice)

#Set constants
util_level = 0.85
objective = "mean-flowtime"

base_dir = "~/Desktop/Uni/COMP489/GPJSS/out/subtree_contributions/"
directory = paste(base_dir,paste(util_level,objective,sep="-"),sep="")
setwd(directory)

#Define functions that will be used below

#Will need to perform some ugly string manipulation to find seed from filename
#first 4 characters of filename will be 'job.'
#Expecting seed to be 1 or 2 digits

parse_seed <- function(filename) {
  end_digit = 6
  if (substr(filename, 6, 6) == " ") {
    end_digit = 5
  }
  as.numeric(substr(filename, 5, end_digit))
}

#Read in files ending in .bbs.csv
#Expecting 30 files, want to store voting weights of all BB's in each
my_list = list()

filenames = list.files()
for (filename in filenames) {
  if (endsWith(filename,"bbs.csv")) {
    seed = parse_seed(filename)

    BBs = read.csv(filename,header=FALSE)
    #BBs will contain two columns - first are BBs, second are voting weights
    #Only care about voting weights for now
    BBs_weights = (data.frame(as.numeric(BBs[,2])))
    my_list[seed] = BBs_weights
  }
}

print(my_list)

df <- data.frame(matrix(unlist(my_list[24]), nrow=1, byrow=T),stringsAsFactors=FALSE)

stripplot(df)








