@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript

import com.ibm.dbb.build.*
import com.ibm.dbb.build.report.*
import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*
import java.nio.file.*
import groovy.time.*
import java.util.*


def usage = "BuildCPL.groovy [options]"
println("load tools")
// load the Tools.groovy utility script
def tools = loadScript(new File("Tools.groovy"))
def opts = tools.parseArgs(args, usage)
println("load properties")
def properties = tools.loadProperties(opts)

def startTime = new Date()
properties.startTime = startTime.format("yyyyMMdd.hhmmss.mmm")
println("** Build CPL start at $properties.startTime")

// get the last successful build's buildHash
println("** Searching for last successful build commit hash for build group $properties.collection")
def lastBuildHash = null
def repositoryClient = tools.getDefaultRepositoryClient()
def lastBuildResult = repositoryClient.getLastBuildResult(properties.collection, BuildResult.COMPLETE, BuildResult.CLEAN)
if (lastBuildResult)
	lastBuildHash = lastBuildResult.getProperty("buildHash")

// test if we have at least one build result. If not, create one.
if (lastBuildHash == null) {
	// Simulation d'un build & build result
	println (" First build - generate empty build result")
	println ("initialize build result")
	tools.initializeBuildArtifacts()

	// use build list for initial scan
	def buildList = new File("$scriptDir/files.txt")

	if (!repositoryClient.collectionExists(properties.collection))
		repositoryClient.createCollection(properties.collection)

	println("** Scan the build list to collect dependency data")
	def scanner = new DependencyScanner()
	def logicalFiles = [] as List<LogicalFile>

	buildList.each { file ->
		println("Scanning $file")
		def logicalFile = scanner.scan(file, properties.sourceDir)
		logicalFiles.add(logicalFile)

		if (logicalFiles.size() == 500) {
			println("** Storing ${logicalFiles.size()} logical files in repository collection '$properties.collection'")
			repositoryClient.saveLogicalFiles(properties.collection, logicalFiles);
			println(repositoryClient.getLastStatus())
			logicalFiles.clear()
		}
	}


	println("** Storing all ${logicalFiles.size()} logical files in repository collection '$properties.collection'")
	repositoryClient.saveLogicalFiles(properties.collection, logicalFiles);
	println(repositoryClient.getLastStatus())

	// generate build report
	def (File jsonFile, File htmlFile) = tools.generateBuildReport()

	// finalize build result
	def processCounter = 0
	println ("finalize build result")
	tools.finalizeBuildResult(jsonReport:jsonFile, htmlReport:htmlFile, filesProcessed:processCounter)
	System.exit(0)
}

println("** found last build result - there's no need to create a first one")
// get the last tagged (CPL) build's buildHash
println("** Searching for last successful build commit hash with tag CPL for build group $properties.collection")
def lastCPLBuildHash = null
def lastCPLBuildResult = null

//Get all BuildResult
Map<String,String> map = ['group':properties.collection,'order':'DESC', 'status':'CLEAN']

def allBuildResult = repositoryClient.getAllBuildResults(map)
allBuildResult.each{ build ->
	if(build.getProperty("Deliver")=="CPL"){
		lastCPLBuildHash = build.getProperty("buildHash")
		lastCPLBuildResult = build
		return
	}
}

// if no lastBuildHash, then just copy MortgageApplication/build/files.txt to buildlist and exit
if (lastCPLBuildHash == null) {
	println("Could not locate last tagged build commit hash for build group $properties.collection.")
	System.exit(0)
}

println("Last successful tagged commit hash located. label : ${lastCPLBuildResult.getLabel()} , buildHash : $lastCPLBuildHash")

println ("initialize build result")
tools.initializeBuildArtifacts()

// create workdir (if necessary)
new File(properties.workDir).mkdirs()

// create folders
def folderPerm = "$properties.workDir/folderPerm"
def folderTemp = "$properties.workDir/folderTemp"
new File(folderTemp).mkdirs()

//create tar file
def tarFileName = "$properties.workDir/package.tar"
def tarFile = new File("$tarFileName")

//create shell file
def commandFile = new File("$properties.workDir/processPackage.sh")

//get file properties
def propt = new Properties()
File propertiesFile = new File("$properties.sourceDir/MortgageApplication/build/scriptToPDS.properties")
propertiesFile.withInputStream {
	propt.load(it)
}

//create .properties
def propFile = new File("$folderTemp/package.properties")
propFile << "collection = ${properties.collection}\n"
propFile << "Url = ${tools.getBuildResult().getUrl()}\n"
propFile << "Label = ${tools.getBuildResult().getLabel()}\n"


// execute git command
def cmd = "git diff --name-only $lastCPLBuildHash $properties.buildHash"
def out = new StringBuffer()
def err = new StringBuffer()
println("** Executing Git command: $cmd")
def process = cmd.execute()
process.consumeProcessOutput(out, err)
process.waitForOrKill(1000)

// handle command error
if (err.size() > 0) {
	println "** Error occurred executing git command: ${err.toString()}"
	System.exit(1)
}
def changedFiles = out.readLines()
println("Number of changed files detected since build ${lastCPLBuildResult.getLabel()} : ${changedFiles.size()}")
println(out)

// if no changed files, created empty build list file and exit
if (changedFiles.size() == 0) {
	println("** No changed files detected since last successful build.  Creating empty file $properties.workDir/buildlist.txt")
	new File("$properties.workDir/buildList.txt").createNewFile()
	System.exit(0)
}

// scan the changed files to make sure dependency data is up to date
println("** Scan the changed file list to collect the latest dependency data")
def scanner = new DependencyScanner()
def logicalFiles = [] as List<LogicalFile>

changedFiles.each { file ->
	println("Scanning changed file $file")
	def logicalFile = scanner.scan(file, properties.sourceDir)
	logicalFiles.add(logicalFile)
}

println("** Store the dependency data in repository collection '$properties.collection'")
// create collection if needed
if (!repositoryClient.collectionExists(properties.collection))
	repositoryClient.createCollection(properties.collection)

repositoryClient.saveLogicalFiles(properties.collection, logicalFiles);
println(repositoryClient.getLastStatus())

// resolve impacted programs/files for changed files
println("** Creating build list by resolving impacted programs/files for changed files")
def buildList = [] as Set<String>
def buildListImpact = [] as Set<String>
changedFiles.each { changedFile ->
	// if the changed file has a build script then skip impact analysis and add to build list
	buildList.add(changedFile)
	if (ScriptMappings.getScriptName(changedFile)) {
		println("Found build script mapping for $changedFile. Adding to build list.")
	}
	else {
		println("Searching for programs impacted by changed file $changedFile")
		def resolver = tools.getDefaultImpactResolver(changedFile)
		def impacts = resolver.resolve()
		impacts.each { impact ->
			def impactFile = impact.getFile()
			// Add impacted files
			if (ScriptMappings.getScriptName(impactFile)) {
				println("$impactFile is impacted by changed file $changedFile. Adding to build list.")
				buildListImpact.add(impactFile)
			}
		}
	}
}

// generate build report
def (File jsonFile, File htmlFile) = tools.generateBuildReport()

// finalize build result
def processCounter = 0
println ("finalize build result")
tools.finalizeBuildResult(jsonReport:jsonFile, htmlReport:htmlFile, filesProcessed:processCounter)


// Load the ScriptMapping to map to folders
properties = loadProp(opts)

// Write build list to file
println("** Writing buildlist to $folderTemp/buildlist.txt")
def buildListFile = new File("$folderTemp/buildList.txt")
def subdiv = folderTemp
buildList.each { file ->
	loadfile(buildListFile,file,subdiv,folderTemp,propt)
}

// Write build list impact to file
def numberFile = buildList.size()
println("** Writing buildlistImpact to $folderTemp/buildlistImpact.txt")
def buildListImpactFile = new File("$folderTemp/buildListImpact.txt")
buildListImpact.each { file2 ->
	def x = false
	buildList.each{ file1 ->
		//if the file is already in the BuildList don't add it
		if(file1==file2){
			x=true
		}
	}
	if(x==false){
		loadfile(buildListImpactFile,file2,subdiv,folderTemp,propt)
		numberFile++
	}
}

println("** Create tar file")
def tarOut = ["sh", "-c", "cd $folderTemp && tar cf $tarFile *"].execute().text

//delete folderTemp
commandFile << "# delete unpack directory \n"
commandFile << "cd $properties.workDir \n"
commandFile << "echo \"delete $folderTemp\" \n"
commandFile << "rm -r $folderTemp \n"

//shell command
commandFile << "#!/bin/sh\n"
commandFile << "\n"
commandFile << "mkdir $folderPerm \n"
commandFile << "cd $folderPerm \n"
commandFile << "\n"
commandFile << "# unpack tar file \n"
def command = "tar -xf $tarFileName -C $folderPerm"
commandFile << command + "\n"

// make shell script executable
def chCommand = "chmod 755 $commandFile"
def work = new File("$properties.workDir")
def process1 = chCommand.execute(null, work)
int rc = process1.waitFor()
println ("chmode return code is $rc")

//Execute shell script
def commandShell = "sh $commandFile"
println ("execute shell script : $commandShell")
def sout = new StringBuilder(), serr = new StringBuilder()
def process2 = commandShell.execute(null, work)
process2.consumeProcessOutput(sout, serr)
int rc2 = process2.waitFor()
println ("out> $sout err> $serr")
println ("finished executing shell, rc is $rc2")


// Print end build message
def endTime = new Date()
def duration = TimeCategory.minus(endTime, startTime)
println("** Impact analysis finished at $endTime")
println("** Total # build files calculated = $numberFile")
println("** Total analysis time : $duration")

//load file into PDS mapping
def loadfile(File buildListImpactFile, String file, String subdiv, String folderTemp, Properties propt){
	buildListImpactFile << (file + "\n")
	def y = 0
	for(i=0;i<file.length();i++){
		if(file[i]=="/"){
			y=i
		}
	}
	//Get the mapping if there is one, else add to root folder
	if(ScriptMappings.getScriptName(file)){
		String mappings = ScriptMappings.getScriptName(file)
		subdiv = folderTemp+"/"+ propt."$mappings"
		new File(subdiv).mkdir()
		println("found scriptMap")
	}
	else{
			subdiv=folderTemp
	}
	println("** Copy $file to $subdiv")
	Files.copy(Paths.get("$file"),Paths.get(subdiv+ file.substring(y)))
}

//load new properties
def loadProp(OptionAccessor opts) {
	// check to see if there is a ./build.properties to load
	def properties = BuildProperties.getInstance()
	def scriptDir = new File(getClass().protectionDomain.codeSource.location.path).parent
	def buildPropFile = new File("$scriptDir/build.properties")
	if (buildPropFile.exists())
		   BuildProperties.load(buildPropFile)

	// set command line arguments
	if (opts.s) properties.sourceDir = opts.s
	if (opts.w) properties.workDir = opts.w
	if (opts.b) properties.buildHash = opts.b
	if (opts.q) properties.hlq = opts.q
	if (opts.c) properties.collection = opts.c
	if (opts.t) properties.team = opts.t
	if (opts.r) properties.repo = opts.r
	if (opts.i) properties.id = opts.i
	if (opts.p) properties.pw = opts.p
	if (opts.P) properties.pwFile = opts.P
	if (opts.e) properties.logEncoding = opts.e
	if (opts.E) properties.errPrefix = opts.E
	if (opts.u) properties.userBuild = "true"

	// load file.properties containing file specific properties like script mappings and CICS/DB2 content flags
	def filePath = "$scriptDir/additionalMapping.properties"
	println ("filePath = " + filePath)
	properties.load(new File(filePath))

	println("** Build properties at startup:")
	println(properties.list())

	return properties
}