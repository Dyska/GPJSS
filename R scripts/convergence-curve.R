library(ggplot2)

working_dir <- "/Users/dyska/Desktop/Uni/COMP489/GPJSS/grid_results/dynamic/test"
setwd(working_dir)

objectives <- c("max-flowtime","mean-flowtime","mean-weighted-flowtime")
objectives.names <- c("maxFT","meanFT","meanWFT")
util_levels <- c(0.85,0.95)
#gps <- c("simple-fc")
#gps.name <- c("SeqGPHH")
gps <- c("ccgp-fc")
gps.name <- c("CCGPHH")

algos <- c("Baseline",
            "Score-0.001-BB-0.5xTVW",
            "2-Clustering-2-Clustering",
            "2-Clustering-3-Clustering",
            "2-Clustering-BB-0.25xTVW",
            "2-Clustering-top-1",
            "3-Clustering-2-Clustering",
            "3-Clustering-3-Clustering",
            "3-Clustering-BB-0.25xTVW",
            "3-Clustering-top-1"
           )

algos.name <- c(
                "CCGPHH",
                #"SeqGPHH",
                "T-0.5TVW",
                "2C-2C",
                "2C-3C",
                "2C-0.25TVW",
                "2C-Top1",
                "3C-2C",
                "3C-3C",
                "3C-0.25TVW",
                "3C-Top1"
                )

col <- "test-fit"
col.name <- "Test Fitness"
# col <- "size"
# col.name <- "Size"

result.df <- data.frame(Scenario = character(),
                        Algo = character(),
                        Run = integer(),
                        Generation = integer(),
                        Size = integer(),
                        UniqueTerminals = integer(),
                        Obj = integer(),
                        TrainFitness = double(),
                        TestFitness = double(),
                        Time = double())

for (g in 1:length(gps)) {
  for (util_level in util_levels) {
    for (i in 1:length(objectives)) {
      for (a in 1:length(algos)) {
        objective <- objectives[i]
        instance <- paste0(util_level,"-",objective)
        gp = gps[g]
        testfile <- paste0(gp,"/",instance,"-",algos[a],"/test/missing-",util_level,"-4.csv")
        #print(testfile)
        algoname = algos.name[a]
        instance <- paste0(util_level,"-",objectives.names[i])
        df <- read.csv(testfile, header = TRUE)
        result.df <- rbind(result.df,
                           cbind(Scenario = rep(instance, nrow(df)),
                                 Algo = rep(algoname, nrow(df)),
                                 df))  
      }
    }
  }
}

runs <- unique(result.df$Run)
generations <- max(result.df$Generation)

curve.df <- data.frame(Scenario = character(),
                       Algo = character(),
                       Generation = integer(),
                       Mean = double(),
                       StdDev = double(),
                       StdError = double(),
                       ConfInterval = double())

for (gp in 1:length(gps)) {
  for (util_level in util_levels) {
    for (i in 1:length(objectives)) {
      for (a in 1:length(algos)) {
        instance <- paste0(util_level,"-",objectives.names[i])
        algo <- algos.name[a]
        #print(paste0(algo," ",instance))
        
        for (g in 50:generations) {
          rows <- subset(result.df, Scenario == instance & Algo == algo & Generation == g)
          
          if (nrow(rows) == 0)
            next

          switch(col,
                 "test-fit"={rows <- rows$TestFitness},
                 "size"={rows <- rows$Size})


          rows.mean <- mean(rows)
          rows.sd <- sd(rows)
          rows.se <- rows.sd / sqrt(length(rows))
          rows.ci <- 1.96 * rows.sd

          curve.df <- rbind(curve.df, data.frame(Scenario = instance,
                                                 Algo = algo,
                                                 Generation = g,
                                                 Mean = rows.mean,
                                                 StdDev = rows.sd,
                                                 StdError = rows.se,
                                                 ConfInterval = rows.ci))
        }
      }
    }
  }
}

for (gp in 1:length(gps)) {
  for (util_level in util_levels) {
    for (i in 1:length(objectives)) {
      for (a in 1:length(algos)) {
        instance <- paste0(util_level,"-",objectives.names[i])
        algo <- paste0(algos.name[a])
        print(paste0(algo," ",instance))
        
        for (g in 50:generations) {
          rows <- subset(result.df, Scenario == instance & Algo == algo & Generation == g)
          
          if (nrow(rows) == 0)
            next
          
          switch(col,
                 "test-fit"={rows <- rows$TestFitness},
                 "size"={rows <- rows$Size})
          
          rows.mean <- mean(rows)
          rows.sd <- sd(rows)
          rows.se <- rows.sd / sqrt(length(rows))
          rows.ci <- 1.96 * rows.sd
          
          curve.df <- rbind(curve.df, data.frame(Scenario = instance,
                                                 Algo = algo,
                                                 Generation = g,
                                                 Mean = rows.mean,
                                                 StdDev = rows.sd,
                                                 StdError = rows.se,
                                                 ConfInterval = rows.ci))
        }
      }
    }
  }
}



#g <- ggplot(curve.df, aes(Generation-1, Mean, colour = factor(Algo), shape = factor(Algo))) +
 # geom_ribbon(aes(ymin = Mean-StdDev, ymax = Mean+StdDev, fill = factor(Algo)), alpha = 0.3) +
#  geom_line() + geom_point(size = 2)

g <- ggplot(curve.df, aes(Generation, Mean, colour = factor(Algo))) +
 #geom_ribbon(aes(ymin = Mean-StdError, ymax = Mean-StdError, fill = factor(Algo)), alpha = 0.3) +
  geom_line() + geom_point(size = 1.5)
g <- g + facet_wrap(~ Scenario, ncol = 3, scales = "free")
g <- g + theme(legend.title = element_blank())
g <- g + theme(legend.text = element_text(size = 14))
g <- g + theme(legend.position = "bottom")
g <- g + labs(y = col.name)
g <- g + theme(axis.title.x = element_text(size = 16, face = "bold"))
g <- g + theme(axis.title.y = element_text(size = 16, face = "bold"))
g <- g + theme(axis.text.x = element_text(size = 14))
g <- g + theme(axis.text.y = element_text(size = 14))
g <- g + theme(strip.text.x = element_text(size = 16))
print(g)

ggsave(paste0(gps[1],"-",col.name, "-curve.pdf"), width = 8, height = 6)


#scen <- "0.85-maxFT"
#algo <- "CCGPHH"
#other_algo <- "2C-3C"
#g <- 73
#check_rows1 <- subset(result.df, Scenario == scen & Algo == algo & Generation == g)
#check_rows2 <- subset(result.df, Scenario == scen & Algo == other_algo & Generation == g)





