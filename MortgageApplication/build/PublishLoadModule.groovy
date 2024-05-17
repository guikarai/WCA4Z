@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import java.io.File
import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import org.apache.http.entity.FileEntity
import com.ibm.dbb.build.*
import com.ibm.dbb.build.DBBConstants.CopyMode
import com.ibm.dbb.build.report.BuildReport
import com.ibm.dbb.build.report.records.DefaultRecordFactory


/************************************************************************************
 * This script publishes the outputs generated from a build to a tar file
 * It generates the script to unpack the tar file and restore the members
 *
 ************************************************************************************/


// load the Tools.groovy utility script
def tools = loadScript(new File("Tools.groovy"))

// parse command line arguments and load build properties
def usage = "PublishLoadModule.groovy [options]"
def opts = tools.parseArgs(args, usage)
def properties = tools.loadProperties(opts)

def workDirProp = properties.workDir

parseAndCreate (workDirProp)

println ("about to call delete PDS")

def copybookPDS = "${properties.hlq}.COPYBOOK"
deletePDS (copybookPDS)

def parseAndCreate (String workDir) {

	//Retrieve the build report and parse the outputs from the build report
	def buildReportFile = new File("$workDir/BuildReport.json")
	assert buildReportFile.exists(), "$buildReportFile does not exist"
	
	def buildReport = BuildReport.parse(buildReportFile.newInputStream())
	def executes = buildReport.records.findAll { record ->
	    record.type == DefaultRecordFactory.TYPE_EXECUTE && !record.outputs.isEmpty()
	}
	
	if  (executes.size() == 0) {
	        println ( "There are no outputs found in the build report, nothing to package" )
	        return
	        }
	
	
	// Let's create the shell script to handle that tar file
	
	// create file
	println("** create command file")
	def commandFile = new File("$workDir/processPackage.sh")
	!commandFile.exists() ?: commandFile.delete()
	
	
	
	
	def loadDatasetToLoadsMap = [:]
	def loadCount = 0
	
	def memberDatasetToMembersMap = [:]
	def memberCount = 0
	
	def createdFolderList =  []
	
	executes.each { execute ->
	    execute.outputs.each { output ->
	        def (dataset, member) = output.dataset.split("\\(|\\)")
	           if ((output.deployType != null) && (output.deployType.contains("LOAD")))
	          {
	              if (loadDatasetToLoadsMap[dataset] == null)
	                  loadDatasetToLoadsMap[dataset] = []
	              loadDatasetToLoadsMap[dataset].add(member)
	              loadCount++
	          }
	          else if ((output.deployType != null) && (output.deployType.contains("COPY")))
	           {
				// do nothing we will not send copybooks
	          }
	          else
	          {
	              if (memberDatasetToMembersMap[dataset] == null)
	                  memberDatasetToMembersMap[dataset] = []
	              memberDatasetToMembersMap[dataset].add(member)
	              memberCount++
	          }
	
	    }
	}
	
	assert (loadCount  + memberCount) > 0, "There are no load modules or members to publish"
	
	
	//Create a temporary directory on zFS to copy the load modules from data sets to
	def tempLoadDir = new File("$workDir/tempLoadDir")
	!tempLoadDir.exists() ?: tempLoadDir.deleteDir()
	tempLoadDir.mkdirs()
	
	//def date = new Date()
	//def sdf = new SimpleDateFormat("yyyyMMdd-HHmmss")
	//def startTime = sdf.format(date) as String
	//def buildLabel = "build.$startTime" as String
	def buildLabel = "Package"
	
	def tempUnpackDir = "$workDir/tempUnpackDir"
	def tarFileName = "$workDir/${buildLabel}.tar"
	def tarFile = new File("$tarFileName")
	!tarFile.exists() ?: tarFile.delete()
	
	
	// write commands to unpack the tar file that we will create later on
	commandFile << "#!/bin/sh\n"
	commandFile << "\n"
	commandFile << "mkdir $tempUnpackDir \n"
	commandFile << "cd $tempUnpackDir \n"
	commandFile << "\n"
	commandFile << "# unpack tar file \n"
	def command = "tar -xf $tarFileName -C $tempUnpackDir"
	commandFile << command + "\n"
	commandFile << "\n# copy files to PDS \n"
	
	//For each load modules, use CopyToHFS with option 'CopyMode.LOAD' to maintain
	//SSI and  Alias
	
	println "Number of load modules to publish: $loadCount"
	
	// Create files for load modules
	loadDatasetToLoadsMap.each { dataset, members ->
	
	// add commands for the unpack script
	    commandFile << "cd $tempUnpackDir/$dataset \n"
	
	// create folder
	     datasetDir = new File("$tempLoadDir/$dataset")
	     createdFolderList.add (datasetDir)
	     datasetDir.mkdirs()
	
	
	     CopyToHFS copy = new CopyToHFS().copyMode(CopyMode.LOAD)
	
	    members.each { member ->
	
	        def fullyQualifiedDsn = "$dataset($member)"
	        def file = new File(datasetDir, member)
	        copy.dataset(dataset).member(member).file(file).copy()
	        println "Copying $dataset($member) to $datasetDir"
	
	        // add shell command entry
	        // without quotes, the HLQ of the user is added
	        // with quotes it's the full PDS name
	 	commandFile << "echo \"running cp $member to $dataset \" \n"
		commandFile << "cp -X -I  $member \"//'$dataset'\" \n"
	    }
	}
	
	 println "Number of other files to publish: $memberCount"
	
	// Create dedicated directories for datasets (e.g. Object decks and DBRMs)
	memberDatasetToMembersMap.each { dataset, members ->
	
	// add commands for the unpack script
	    commandFile << "cd $tempUnpackDir/$dataset \n"
	
	    copy = new CopyToHFS().copyMode(CopyMode.BINARY)
	
	    datasetDir2 = new File("$tempLoadDir/$dataset")
	    createdFolderList.add (datasetDir2)
	    datasetDir2.mkdirs()
	
	    members.each { member ->
	        def fullyQualifiedDsn = "$dataset($member)"
	        def file = new File(datasetDir2, member)
	        copy.dataset(dataset).member(member).file(file).copy()
	        println "Copying $dataset($member) to $datasetDir2"
	
	 // add commands for the unpack script
	 	commandFile << "echo \"running cp $member to $dataset \" \n"
		commandFile << "cp $member \"//'$dataset'\" \n"
	    }
	}
	
	// Append build report
	def exportBuildReport = new File("$tempLoadDir/BuildReport.json")
	exportBuildReport << buildReportFile.text
	
	//Package the load files just copied into a tar file using the build
	//label as the name for the tar file.
	def buildGroup = "${properties.collection}" as String
	
	def tarOut = ["sh", "-c", "cd $tempLoadDir && tar cf $tarFile *"].execute().text
	
	println "created archive file: $tarFile"
	
	// clean-up temporary directory
	println "delete temporary directories"
	createdFolderList.each { folder ->
	    println "deleting $folder"
		folder.deleteDir()
	}
	// we can replace with this to delete them all at once
	tempLoadDir.deleteDir()
	
	 // add commands to delete unpack dir
	commandFile << "# delete unpack directory \n"
	commandFile << "cd $workDir \n"
	commandFile << "echo \"delete $tempUnpackDir\" \n"
	commandFile << "rm -r $tempUnpackDir \n"
	
	def work = new File("$workDir")
	
	// make shell script executable
	def chCommand = "chmod 755 $commandFile"
	def process = chCommand.execute(null, work)
	int rc = process.waitFor()
	println "chmode return code is $rc"
	
	
	//Execute shell script
	
	def commandShell = "sh $commandFile"
	println "execute shell script : $commandShell"
	def sout = new StringBuilder(), serr = new StringBuilder()
	def process2 = commandShell.execute(null, work)
	process2.consumeProcessOutput(sout, serr)
	int rc2 = process2.waitFor()
	
	println "out> $sout err> $serr"
	
	println "finished executing shell, rc is $rc2"
}

def deletePDS (String pdsName) {
	println ("Delete PDS " + pdsName)
                      
	def logFile = new File("${properties.workDir}/${pdsName}.log")
	// define the BPXWDYN options for allocated temporary datasets
	def tempCreateOptions = "cyl space(5,5) unit(vio) blksize(80) lrecl(80) recfm(f,b) new"
                      
	// define the MVSExec command for IDCAMS
	def idcams = new MVSExec().pgm("IDCAMS").parm("")

	def records = " DELETE '$pdsName' "
	idcams.dd(new DDStatement().name("SYSIN").instreamData(records))
	idcams.dd(new DDStatement().name("SYSPRINT").options(tempCreateOptions))   
	
	idcams.copy(new CopyToHFS().ddName("SYSPRINT").file(logFile).hfsEncoding(properties.logEncoding))
	
	// execute the MVSExec compile command
	def rc = idcams.execute()

	println ("IDCAMS result = " + rc)

	println (logFile.text)
	logFile.delete() 
                 
}

