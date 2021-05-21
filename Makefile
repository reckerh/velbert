SHARED-SVN := C:/Users/Janekdererste/blup/shared-svn
VERSION := matsim-velbert-1.0
OUTPUT_FOLDER := projects/matsim-velbert/matsim-input/$(VERSION)
JAR := velbert-0.0.1-SNAPSHOT.jar
SNZ_SCENARIO := $(SHARED-SVN)/projects/matsim-velbert/matsim-input/matsim-velbert-snz-original
RAW_INPUT := $(SHARED-SVN)/projects/matsim-verlbert/raw-input/

NETWORK := $(SHARED-SVN)/$(OUTPUT_FOLDER)/$(VERSION).network.xml.gz
POPULATION := $(SHARED-SVN)/$(OUTPUT_FOLDER)/$(VERSION).plans.xml.gz

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
	 --name $(VERSION)\
	 --sample-size 0.25\
	 --population $(SNZ_SCENARIO)/population.xml.gz\
	 --attributes $(SNZ_SCENARIO)/personAttributes.xml.gz\
	 --output $(SHARED-SVN)/$(OUTPUT_FOLDER)\

	java -jar $(JAR) prepare resolve-grid-coords\
	 --$(POPULATION)
	 --grid-resolution 500\
	 --input-crs EPSG:25832\
	 --landuse $(RAW_INPUT)/landuse/landuse.shp\
	 --output $(POPULATION)\

	java -jar $(JAR) prepare downsample-population scenarios/input/duesseldorf-$V-25pct.plans.xml.gz\
     	 --sample-size 0.25\
     	 --samples 0.1 0.01\


# aggregate target
prepare: $(NETWORK) $(POPULATION)
	@echo "Done"