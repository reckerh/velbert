SHARED-SVN := C:/Users/Janekdererste/repos/shared-svn
VERSION := matsim-velbert-1.0
OUTPUT_FOLDER := projects/matsim-velbert/matsim-input/$(VERSION)
JAR := velbert-0.0.1-SNAPSHOT.jar
SNZ_SCENARIO := $(SHARED-SVN)/projects/matsim-velbert/matsim-input/matsim-velbert-snz-original
RAW_INPUT := $(SHARED-SVN)/projects/matsim-velbert/raw-input/

NETWORK := $(SHARED-SVN)/$(OUTPUT_FOLDER)/$(VERSION).network.xml.gz
TMP_POPULATION := $(SHARED-SVN)/$(OUTPUT_FOLDER)/tmp-25pct.plans.xml.gz
POPULATION := $(SHARED-SVN)/$(OUTPUT_FOLDER)/$(VERSION)-25pct.plans.xml.gz
VEHICLES := $(SHARED-SVN)/$(OUTPUT_FOLDER)/$(VERSION).vehicles.xml.gz

# build the application before we can do anything with it
$(JAR):
	java --version
	mvn package

# create network
$(NETWORK): $(JAR)
	java -Xmx20G -jar $(JAR)  prepare network --sharedSvn $(SHARED-SVN)\

	java -jar $(JAR) prepare pt --sharedSvn $(SHARED-SVN)\

# do population stuff
$(POPULATION): $(JAR)
	java -jar $(JAR) prepare trajectory-to-plans\
	 --name tmp\
	 --sample-size 0.25\
	 --population $(SNZ_SCENARIO)/population.xml.gz\
	 --attributes $(SNZ_SCENARIO)/personAttributes.xml.gz\
	 --output $(SHARED-SVN)/$(OUTPUT_FOLDER)\

	java -jar $(JAR) prepare resolve-grid-coords\
	 $(TMP_POPULATION)\
	 --grid-resolution 500\
	 --input-crs EPSG:25832\
	 --landuse $(RAW_INPUT)/landuse/landuse.shp\
	 --output $(POPULATION)\

	java -jar $(JAR) prepare downsample-population\
 	 $(POPULATION)\
     --sample-size 0.25\
     --samples 0.1 0.01\

$(VEHICLES):
	java -jar $(JAR) prepare vehicle-types\
	 --sharedSvn $(SHARED-SVN)

# aggregate target
prepare: $(NETWORK) $(POPULATION) $(VEHICLES)
	rm $(TMP_POPOULATION)
	@echo "Done"