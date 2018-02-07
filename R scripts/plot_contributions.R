#Simply want to read in csv file and show distribution of contributions
require(ggplot2)
library(easyGgplot2)

#Set constants
util_level <- 0.85
objective <- "mean-flowtime"
seed <- 12

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

result.df <- data.frame(Scenario = character(),
                        Rule = integer(),
                        Contribution = double(),
                        Cluster = character())

scenario <- paste(util_level,objective,sep="-")
print(paste("Processing",scenario))

directory <- paste(base_dir,scenario,sep="")
setwd(directory)
filename <- paste0("job.",seed," - SEQUENCING.fcinfo.csv")

df <- read.csv(filename,header=TRUE)
num_bb = nrow(df) / 30

result.df <- rbind(result.df, 
                   cbind(Scenario = rep(scenario, nrow(df)),
                         Rule = c(1:30), 
                         df))

#want rule 1 to have all it's contributions, rule 2 to have all it's contributions etc

g <- ggplot(result.df,aes(Rule,Contribution)) +
  geom_point(colour = "blue", size = 1.0) +
  theme(plot.title = element_text(hjust = 0.5))
print(g)
setwd(base_dir)
ggsave(paste0(util_level,"-",objective,"-",seed,"-contributions.pdf"), width = 8, height = 6)

