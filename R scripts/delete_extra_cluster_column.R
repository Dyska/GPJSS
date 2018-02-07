#Simply want to read in csv file and show distribution of contributions
require(ggplot2)
library(easyGgplot2)

#Set constants
util_levels <- c(0.85,0.95)
objectives <- c("mean-flowtime","max-flowtime","mean-weighted-flowtime")
thresholds <- c(0, 0.0001, 0.001, 0.01, 0.1, 0.2)

num_thresholds <- length(thresholds)
contributions_exceeding_thresholds <- numeric(num_thresholds)

base_dir <- "~/Desktop/Uni/COMP489/GPJSS/out/subtree_contributions_filtered/"

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

for (util_level in util_levels) {
  for (objective in objectives) {
    scenario <- paste(util_level,objective,sep="-")
    print(paste("Processing",scenario))
    
    directory <- paste(base_dir,scenario,sep="")
    setwd(directory)
    filenames <- list.files()
    
    for (filename in filenames) {
      if (endsWith(filename,"fcinfo.csv")) {
        df <- read.csv(filename,header=TRUE)
        seed <- parse_seed(filename)
        print(df)
      }
    }
  }
}
