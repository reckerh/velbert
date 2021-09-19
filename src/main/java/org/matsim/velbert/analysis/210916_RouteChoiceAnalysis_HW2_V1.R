##### route choice analysis #####
#the route choice analysis shall be conducted by looking at the number of link leave-events on 
#groups of links and bringing this number into relation to their free Speed and the changes to that

#This analysis will be conducted simultaneously in this script for all the four 10%-cases that were calculated.

## Read in and merge the files ##
## Step 1: read in linkLeaveCounts and rename their columns ##
#BaseCase
llBaseCase <- read.csv("output-velbert-BaseCase-10pct/linkLeaveCounts.csv", header = F, sep = ",")
names(llBaseCase) <- c("linkId", "countBaseCase")

#PF1:
llPF1 <- read.csv("output-velbert-PF1-10pct/linkLeaveCounts.csv", header = F, sep = ",")
names(llPF1) <- c("linkId", "countPF1")

#PF2:
llPF2 <- read.csv("output-velbert-PF2-10pct/linkLeaveCounts.csv", header = F, sep = ",")
names(llPF2) <- c("linkId", "countPF2")

#Sensitivity Test
llSensitivity <- read.csv("output-velbert-Sensitivity-10pct/linkLeaveCounts.csv", header = F, sep = ",")
names(llSensitivity) <- c("linkId", "countSensitivity")


## Step 2: read in linkFreeSpeeds and rename their columns ##
#BaseCase
fsBaseCase <- read.csv("output-velbert-BaseCase-10pct/linkFreeSpeeds.csv", header = F, sep = ",")
names(fsBaseCase) <- c("linkId", "speedBaseCase")

#PF1:
fsPF1 <- read.csv("output-velbert-PF1-10pct/linkFreeSpeeds.csv", header = F, sep = ",")
names(fsPF1) <- c("linkId", "speedPF1")

#PF2:
fsPF2 <- read.csv("output-velbert-PF2-10pct/linkFreeSpeeds.csv", header = F, sep = ",")
names(fsPF2) <- c("linkId", "speedPF2")

#Sensitivity Test
fsSensitivity <- read.csv("output-velbert-Sensitivity-10pct/linkFreeSpeeds.csv", header = F, sep = ",")
names(fsSensitivity) <- c("linkId", "speedSensitivity")


## Step 3: merge all data frames ##
#fsBaseCase & fsPF1
rcAnalysis <- merge(fsBaseCase, fsPF1, by = "linkId", all = T)

#+fsPF2
rcAnalysis <- merge(rcAnalysis, fsPF2, by = "linkId", all = T)

#+fsSensitivity
rcAnalysis <- merge(rcAnalysis, fsSensitivity, by = "linkId", all = T)

#+llBaseCase
rcAnalysis <- merge(rcAnalysis, llBaseCase, by = "linkId", all = T)

#+llPF1
rcAnalysis <- merge(rcAnalysis, llPF1, by = "linkId", all = T)

#+llPF2
rcAnalysis <- merge(rcAnalysis, llPF2, by = "linkId", all = T)

#+llSensitivity
rcAnalysis <- merge(rcAnalysis, llSensitivity, by = "linkId", all = T)

#remove data frames that are not needed anymore
rm(fsBaseCase, fsPF1, fsPF2, fsSensitivity, 
   llBaseCase, llPF1, llPF2, llSensitivity)

#length(row.names(rcAnalysis)[which(is.na(rcAnalysis$speedBaseCase))])
#sum(rcAnalysis$countSensitivity[which(is.na(rcAnalysis$speedBaseCase))], na.rm=T)
#Anmerkung: Beim Mergen gab es ca. 50 Links, die im BaseCase keine Auslastung hatten und für die daher
#keine BaseCase-Daten eingefügt werden konnten. Diese enthalten jedoch nur eine geringe Zahl an linkLeave-Events
#(PF1: 32; PF2: 29; Sensitivity: 73),; daher sollte es
#keinen Einfluss auf die Gesamtaussage der folgenden Analyse haben, dass sie bei dieser wegfallen werden
#(da aggregate NA-Einträge in der Formel-Version standardmäßig nicht berücksichtigt)


## sort speeds into speed bins ##

#baseCase
rcAnalysis$speedBinsBaseCase <- NA
rcAnalysis$speedBinsBaseCase[which(rcAnalysis$speedBaseCase<=(30/3.6))] <- "<=30 km/h"
rcAnalysis$speedBinsBaseCase[which(rcAnalysis$speedBaseCase>(30/3.6) &
                                     rcAnalysis$speedBaseCase<(51/3.6))] <- ">30 km/h bis <51 km/h"
rcAnalysis$speedBinsBaseCase[which(rcAnalysis$speedBaseCase>=(51/3.6) &
                                     rcAnalysis$speedBaseCase<(100/3.6))] <- ">=51 km/h bis <100 km/h"
rcAnalysis$speedBinsBaseCase[which(rcAnalysis$speedBaseCase>=(100/3.6))] <- ">=100 km/h"

#PF1
rcAnalysis$speedBinsPF1 <- NA
rcAnalysis$speedBinsPF1[which(rcAnalysis$speedPF1<=(30/3.6))] <- "<=30 km/h"
rcAnalysis$speedBinsPF1[which(rcAnalysis$speedPF1>(30/3.6) &
                                rcAnalysis$speedPF1<(100/3.6))] <- ">30 km/h bis <100 km/h"
rcAnalysis$speedBinsPF1[which(rcAnalysis$speedPF1>=(100/3.6))] <- ">=100 km/h"

#PF2
rcAnalysis$speedBinsPF2 <- NA
rcAnalysis$speedBinsPF2[which(rcAnalysis$speedPF2<=(30/3.6))] <- "<=30 km/h"
rcAnalysis$speedBinsPF2[which(rcAnalysis$speedPF2>(30/3.6) &
                                rcAnalysis$speedDF2<(51/3.6))] <- ">30 km/h bis <51 km/h"
rcAnalysis$speedBinsPF2[which(rcAnalysis$speedPF2>=(51/3.6))] <- ">=51 km/h"

#Sensitivity
rcAnalysis$speedBinsSensitivity <- NA
rcAnalysis$speedBinsSensitivity[which(rcAnalysis$speedSensitivity<=3)] <- "<=3 m/s"
rcAnalysis$speedBinsSensitivity[which(rcAnalysis$speedSensitivity>3)] <- ">3 m/s"


## transform the absolute number of counts into a relative number of counts ##
rcAnalysis$countBaseCaseRel <- rcAnalysis$countBaseCase/sum(rcAnalysis$countBaseCase, na.rm=T)
rcAnalysis$countPF1Rel <- rcAnalysis$countPF1/sum(rcAnalysis$countPF1, na.rm = T)
rcAnalysis$countPF2Rel <- rcAnalysis$countPF2/sum(rcAnalysis$countPF2, na.rm=T)
rcAnalysis$countSensitivityRel <- rcAnalysis$countSensitivity/sum(rcAnalysis$countSensitivity, na.rm=T)


## Analyse the counts based on the freeSpeed bins ##
resRCAnalysis <- aggregate(.~speedBinsBaseCase+speedBinsPF1+speedBinsPF2+speedBinsSensitivity,
                           data = rcAnalysis[c(
                             "speedBinsBaseCase", "speedBinsPF1", "speedBinsPF2", "speedBinsSensitivity",
                             "countBaseCaseRel", "countPF1Rel", "countPF2Rel", "countSensitivityRel",
                             "countBaseCase", "countPF1", "countPF2", "countSensitivity"
                           )], function(x){sum(x)})
#Anm.: Hier steigert sich ggf. das Problem, dass einzelne Rows wegen NAs ausgeschlossen werden, geringfügig, da es natürlich auch Cross-NAs zwischen 
#PF1 und PF2, rows, die es in BaseCase nicht aber dafür in PF2 gibt, etc. gibt...
colSums(resRCAnalysis[,c(5:8)])
#Weiterhin scheint das jedoch trotzdem nur eine untergeordnete Rolle zu spielen, 
#bei allen Cases sind rund 99,96% bis 99,98% der Counts enthalten 

write.csv2(resRCAnalysis, file = "analysis/210916_resRCAnalysis_10pct.csv", row.names = F)
