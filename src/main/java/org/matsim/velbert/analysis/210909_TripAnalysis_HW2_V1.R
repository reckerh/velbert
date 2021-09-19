## load the necessary libraries ##
library(sf)
library(ggplot2)
library(ggmap)
#stringr is also required to run this script, but I did not use it often so I 
#do not load at the beginning of the script and instead explicitly address it (stringr::function())



####### Read in and format data #######

## INPUT: Run-Name ##
runName <- "velbert-BaseCase-10pct"

#create output folders
dir.create("analysis", showWarnings = F)
dir.create(paste("analysis/", runName, sep = ""), showWarnings = F)


## Read in Trips -> INPUT: Trip-File-Name ##
#adjust the name of the trips file in the command below
#(the trips.csv file needs to be unzipped for this to work!)
t.trips <- read.csv2("output-velbert-BaseCase-10pct/velbert-BaseCase-10pct.output_trips.csv", #read in the MATSim output trips.csv file
                     colClasses = c("character", "numeric", rep("character", 4),#further specification of column classes, decimal separator etc. 
                                    rep("numeric", 2), rep("character", 7),
                                    rep("numeric", 2), rep("character", 2), 
                                    rep("numeric", 2), rep("character", 2)),
                     stringsAsFactors = F, dec = ".")

#create separate data frames for trip starts and ends 
tripStarts <- t.trips
tripEnds <- t.trips
#this is necessary as (as far as I know) each row of a data frame can only have 
#ONE active simple feature, while the trips data frame has two locations (start & end) per row

#create sf elements in the data frames
tripStarts <- st_as_sf(tripStarts, coords = c("start_x", "start_y"), crs = 25832, remove = F)
tripEnds <- st_as_sf(tripEnds, coords = c("end_x", "end_y"), crs = 25832, remove = F)
#x = UTM easting, y = UTM northing



####### Create a Shapefile of Velbert ##################
PLZ <- st_read("Shape_Auswertung/OSM_PLZ_072019.shp")                           #read in the shapefile with all PLZ zones in germany
wanted_PLZ <- c("42549", "42551", "42553", "42555")                             #create a vector with the PLZ codes of Velbert
PLZ_Velbert <- PLZ[PLZ$plz %in% wanted_PLZ,]                                    #extract the rows with the PLZ codes of Velbert from all PLZ
Velbert <- st_union(PLZ_Velbert)                                                #unionize the shapes of the Velbert PLZ codes into one shape
plot(Velbert)                                                                   #plot the shape
rm(PLZ, wanted_PLZ, PLZ_Velbert)                                                #remove data that is not needed anymore; transform the CRS of the
Velbert <- st_transform(Velbert, crs = 25832)                                   #Velbert shape to match the MATSim output CRS
##end##



####### Filter out trips inside of Velbert #######

#tripStarts
tripStartsVelbert <- tripStarts[st_intersects(tripStarts, Velbert, sparse = F),]

#tripEnds
tripEndsVelbert <- tripEnds[st_intersects(tripEnds, Velbert, sparse = F),]


#test if it was successful by plotting the velbert shape and all points that have been kept
ggplot() +
  geom_sf(data = Velbert) +
  geom_sf(data = tripStartsVelbert, col = "red") +
  geom_sf(data = tripEndsVelbert, col = "green")


## reduce the trips who start/end in Velbert to their IDs ##
tripStartsVelbertIDs <- tripStartsVelbert$trip_id
tripEndsVelbertIDs <- tripEndsVelbert$trip_id


## extract the unique trip IDs ##
VelbertTripIDs <- c(tripStartsVelbertIDs, tripEndsVelbertIDs)
VelbertTripIDs <- unique(VelbertTripIDs)


## Filter the Trips by Start/End in Velbert ##
trips <- t.trips
trips <- trips[trips$trip_id %in% VelbertTripIDs,]                              #Only keep rows of the trips data frame whose trip_id column 
                                                                                #is contained in the VelbertTripIDs vector


#######  Trip Analysis #######

## look at modes ##
table(trips$longest_distance_mode)
#there are records where trips have no longest distance mode, those are trips with no recorded
#traveled length (although some have euclidean lengths) -> I will remove them from the modal split calculation data 
trips <- trips[-which(trips$longest_distance_mode==""),]


## look at the activities ##
table(sub("_[^_]+$", "", trips$start_activity_type))                            #this command removes the time suffix from the activity types so the 
table(sub("_[^_]+$", "", trips$end_activity_type))                              #tables are clustered better
                                                                                #the activities look alright, no stage activities etc. are listed

## calculate the overall modal splits ##
MS <- aggregate(person~longest_distance_mode, data = trips, function(x){length(x)})   #take the number of rows (trips) per mode
MS$TripSum <- sum(MS$person)                                                    #Calculate the sum of all trips
MS$MSPercent <- (MS$person/MS$TripSum)*100                                      #Calculate the overall modal split


## prepare distance-based modal splits / create distance bins and sort the trips into them ##
trips$distbins <- NA
trips$distbins[which(trips$traveled_distance <= 1000)] <- "<=1km"
trips$distbins[which(trips$traveled_distance > 1000 & trips$traveled_distance <= 3000)] <- ">1 bis <=3km"
trips$distbins[which(trips$traveled_distance > 3000 & trips$traveled_distance <= 5000)] <- ">3 bis <=5km"
trips$distbins[which(trips$traveled_distance > 5000 & trips$traveled_distance <= 10000)] <- ">5 bis <=10km"
trips$distbins[which(trips$traveled_distance > 10000)] <- "mehr als 10km"
#the rows/trips are sorted into the distance bins based on the traveled distance


## calculate distance-based modal splits ##
MSdist <- aggregate(person~longest_distance_mode+distbins, data = trips, function(x){length(x)})  #count trips per dist bin and mode
MSdistSums <- aggregate(person~distbins, data = trips, function(x){length(x)})                    #Count trips per dist bin
MSdist$TripSum <- NA
MSdist$TripSum <- MSdistSums$person[match(MSdist$distbins, MSdistSums$distbins)]#add the trips per dist bin to the df with trips per dist bin and mode
MSdist$MSPercent <- (MSdist$person/MSdist$TripSum)*100                          #calculate the modal splits per dist bin


## create and print/save plots of the modal splits ##

#overall MS (bar plot)
ggplot(MS, aes(x = longest_distance_mode, y = MSPercent, label = round(MSPercent,1), fill = longest_distance_mode)) +
  geom_bar(stat = "identity") +
  geom_text(size = 3, position = position_stack(vjust = 0.5)) +
  scale_fill_manual(values = c("dodgerblue4", "chocolate", "chartreuse4", "coral", "deepskyblue")) + 
  theme_light() +
  theme(legend.position = "none") +
  ylab("Modal Split Percentage") +
  xlab("Main trip mode (determined by the longest mode distance)") +
  ggtitle(paste("Overall Modal Split of the run ", runName, sep = "")) +
  ggsave(paste("analysis/", runName, "/MSOverall.png", sep = ""), type = "cairo")


#dist-based MS (stacked bar plot)
ggplot(MSdist, aes(x = distbins, y = MSPercent, label = round(MSPercent,1), fill = longest_distance_mode))+
  geom_bar(stat = "identity") +
  geom_text(size = 3, position = position_stack(vjust = 0.5)) +
  scale_fill_manual(values = c("dodgerblue4", "chocolate", "chartreuse4", "coral", "deepskyblue")) +
  theme_light() +
  theme(axis.text.x = element_text(angle = 90, vjust = 0.5, hjust=1)) +
  labs(fill = "Main trip mode \n(determined by the \nlongest mode distance)") +
  ylab("Modal Split Percentage") +
  xlab("Distance bin") +
  ggtitle(paste("Distance-Based Modal Split of the run ", runName, sep = "")) +
  ggsave(paste("analysis/", runName, "/MSDist.png", sep = ""), type = "cairo")



##### Further trip analyses #####

## trip distances & durations ##
#create a new column adding a date (UTC 0 Seconds) to the trip dur
trips$durWDate <- paste0("1970-01-01", " ", trips$trav_time)

#recreate the date in the POSIXct format
trips$durPOSIX <- as.POSIXct(trips$durWDate, format = c("%Y-%m-%d %H:%M:%S"), tz = "GMT")

#get the trip duration in seconds (numeric value of the POSIXct date in the GMT/UTC-timezone)
trips$durSec <- as.numeric(trips$durPOSIX)

#get the mean trip duration and distance per mode
meanTripDurDist <- aggregate(.~longest_distance_mode, 
                             function(x){mean(x)}, data = trips[,c("durSec", "traveled_distance", "longest_distance_mode")])
write.csv2(meanTripDurDist, file = paste0("analysis/", runName, "/meanTripDurDist.csv"), row.names = F)

