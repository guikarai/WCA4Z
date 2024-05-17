@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.build.*
import com.ibm.dbb.build.DBBConstants.CopyMode
import com.ibm.dbb.build.report.BuildReport
import com.ibm.dbb.build.report.records.DefaultRecordFactory

/************************************************************************************
 * This script downloads the load modules stored in a tar file and copy them to MVS *
 ************************************************************************************/

return 0

//Create a temporary directory on zFS to download the load module tar file
//and unpack the tar file to.

def currentDir = new File(getClass().protectionDomain.codeSource.location.path).parent

// load the Tools.groovy utility script
def tools = loadScript(new File("$currentDir/Tools.groovy"))

// parse command line arguments and load build properties
def usage = "DownloadLoadModule.groovy [options]"
def opts = tools.parseArgs(args, usage)
def properties = tools.loadProperties(opts)

def workDir = properties.workDir
def tempLoadDir = new File("$workDir/tempLoadDir")

println "workDir is $workDir"

def tempUnpackDir = new File("$workDir/tempUnpackDir")
!tempUnpackDir.exists() ?: tempUnpackDir.deleteDir()
tempUnpackDir.mkdirs()

def buildLabel = "Package"
def tarFile = new File("$tempLoadDir/${buildLabel}.tar")
println "tarFile is $tarFile"
assert tarFile.exists(), "can't find $tarFile"

// print command
// Write build list to file
println("** create command file")
def commandFile = new File("$properties.workDir/ReceivePackage.sh")

commandFile << "#!/bin/sh\n"
commandFile << "cd $tempLoadDir \n"

def chCommand = "chmod 755 $commandFile"
def process = chCommand.execute(null, tempLoadDir)
int rc = process.waitFor()
println "chmode return code is $rc"


//Unpack the tar file
def command = "tar -xf $tarFile.name -C $tempUnpackDir"
commandFile << command + "\n"
def process2 = command.execute(null, tempLoadDir)
int rc2 = process2.waitFor()

println "tar return code is $rc2"

//Retrieve the build report and parse the outputs from the build report
def buildReportFile = new File("$tempUnpackDir/BuildReport.json")
assert buildReportFile.exists(), "$buildReportFile does not exist"

def buildReport = BuildReport.parse(buildReportFile.newInputStream())
def executes = buildReport.records.findAll { record ->
    record.type == DefaultRecordFactory.TYPE_EXECUTE && !record.outputs.isEmpty()
}

assert executes.size() > 0, "There are no outputs found in the build report"


def loadDatasetList =  []
def memberDatasetList =  []

def loadDatasetToMembersMap = [:]
def loadCount = 0

def memberDatasetToMembersMap = [:]
def memberCount = 0

executes.each { execute ->
    execute.outputs.each { output ->
        def (dataset, member) = output.dataset.split("\\(|\\)")
        if (loadDatasetList.isEmpty() || loadDatasetList.contains(dataset))
          {
           if ((output.deployType != null) && (output.deployType.contains("LOAD"))) {
              if (loadDatasetToMembersMap[dataset] == null)
                  loadDatasetToMembersMap[dataset] = []
              loadDatasetToMembersMap[dataset].add(member)
              loadCount++
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
}

assert (loadCount  + memberCount) > 0, "There are no load modules or members to publish"






// Create PDS for load modules
loadDatasetToMembersMap.each { dataset, members ->
    commandFile << "cd $tempLoadDir/$dataset \n"
    datasetDir = new File("$tempLoadDir/$dataset")
    CopyToPDS copy = new CopyToPDS().copyMode(CopyMode.LOAD)

    members.each { member ->
        def file = new File(datasetDir, member)
        copy.file(file).dataset(dataset).member(file.name).copy()
        println "Copying load module to $dataset($member)"
		commandFile << "cp -X -I  $member $dataset \n"    
    }
}

// Create PDS for binaries (non load modules)
memberDatasetToMembersMap.each { dataset, members ->
    commandFile << "cd $tempLoadDir/$dataset \n"
    datasetDir = new File("$tempLoadDir/$dataset")
    CopyToPDS copy = new CopyToPDS().copyMode(CopyMode.BINARY)

    members.each { member ->
        def file = new File(datasetDir, member)
        copy.file(file).dataset(dataset).member(file.name).copy()
        println "Copying binary file to $dataset($member)"
		commandFile << "cp $member $dataset \n"         
    }
}