library(ggplot2)

algos <- c("culling-dagp-1", "culling-dagp-2", "dagp-penalty-0.1")
algo.names <- c("BaselineGP", "CullingGP", "C-DAGP")
# algos <- c("dagp-penalty-1", "dagp-penalty-0.1", "dagp-penalty-0.01")
# algo.names <- c("1", "0.1", "0.01")
objectives <- rep(c("max-tardiness", "mean-tardiness", "mean-weighted-tardiness"), 2)
utils <- c(rep(0.85, 3), rep(0.95, 3))
ddfactors <- rep(4, 6)

scenarios.name <- c("<Tmax, 0.85, 4>", "<Tmean, 0.85, 4>", "<TWT, 0.85, 4>",
                    "<Tmax, 0.95, 4>", "<Tmean, 0.95, 4>", "<TWT, 0.95, 4>")

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

for (s in 1:length(scenarios.name)) {
  scenario.name <- scenarios.name[s]
  scenario <- paste0(objectives[s], "-", utils[s], "-", ddfactors[s])
  testfile <- paste0("missing-", utils[s], "-", ddfactors[s], ".csv")
  
  for (a in 1:length(algos)) {
    algo <- algos[a]
    df <- read.csv(paste0(algo, "/", scenario, "/test/", testfile), header = TRUE)
    result.df <- rbind(result.df, 
                       cbind(Scenario = rep(scenario.name, nrow(df)),
                             Algo = rep(algo.names[a], nrow(df)), 
                             df))
  }
}

runs <- unique(result.df$Run)
generations <- max(result.df$Generation)

testfit.df <- data.frame(Scenario = character(),
                         Algo = character(),
                         Generation = integer(),
                         Mean = double(),
                         StdDev = double(),
                         StdError = double(),
                         ConfInterval = double())

for (s in 1:length(scenarios.name)) {
  scenario.name <- scenarios.name[s]
  
  for (a in 1:length(algos)) {
    algo <- algo.names[a]
    
    for (g in 1:generations) {
      rows <- subset(result.df, Scenario == scenario.name &
                       Algo == algo & Generation == g)
      
      if (nrow(rows) == 0)
        next
      
      rows.mean <- mean(rows$TestFitness)
      rows.sd <- sd(rows$TestFitness)
      rows.se <- rows.sd / sqrt(nrow(rows))
      rows.ci <- 1.96 * rows.sd
      
      testfit.df <- rbind(testfit.df, data.frame(Scenario = scenario.name,
                                                 Algo = algo,
                                                 Generation = g,
                                                 Mean = rows.mean,
                                                 StdDev = rows.sd,
                                                 StdError = rows.se,
                                                 ConfInterval = rows.ci))
    }
  }
}

g <- ggplot(testfit.df, aes(Generation, Mean, colour = factor(Algo), shape = factor(Algo))) +
  geom_ribbon(aes(ymin = Mean-StdError, ymax = Mean+StdError, fill = factor(Algo)), alpha = 0.3) +
  geom_line() + geom_point(size = 1)
g <- g + facet_wrap(~ Scenario, ncol = 3, scales = "free")

g <- g + theme(legend.title = element_blank())
g <- g + theme(legend.position = "bottom")

g <- g + labs(y = "Test Fitness")

g <- g + theme(axis.title.x = element_text(size = 12, face = "bold"))
g <- g + theme(axis.title.y = element_text(size = 12, face = "bold"))
g <- g + theme(axis.text.x = element_text(size = 10))
g <- g + theme(axis.text.y = element_text(size = 10))
g <- g + theme(strip.text.x = element_text(size = 12))

ggsave("testfit-curve.pdf", width = 9, height = 6)

# table showing

finalTestFit.df <- data.frame(Scenario = character(),
                              Algo = character(),
                              Run = integer(),
                              TestFitness = double())

for (s in 1:length(scenarios.name)) {
  scenario.name <- scenarios.name[s]
  
  for (a in 1:length(algos)) {
    algo <- algo.names[a]
    
    rows <- subset(result.df, Scenario == scenario.name & Algo == algo & Generation == generations)
    
    finalTestFit.df <- rbind(finalTestFit.df, data.frame(Scenario = rep(scenario.name, nrow(rows)),
                                                         Algo = rep(algo, nrow(rows)),
                                                         Run = rows$Run,
                                                         TestFitness = rows$TestFitness))
  }
}

for (s in 1:length(scenarios.name)) {
  scenario.name <- scenarios.name[s]
  rows1 <- subset(finalTestFit.df, Scenario == scenario.name & Algo == algo.names[1])
  rows2 <- subset(finalTestFit.df, Scenario == scenario.name & Algo == algo.names[2])
  rows3 <- subset(finalTestFit.df, Scenario == scenario.name & Algo == algo.names[3])
  cat(sprintf("%s & %.2f(%.2f) & %.2f(%.2f) & %.2f(%.2f) \\\\\n", scenarios.name[s], mean(rows1$TestFitness), sd(rows1$TestFitness), mean(rows2$TestFitness), sd(rows2$TestFitness), mean(rows3$TestFitness), sd(rows3$TestFitness)))          
}
