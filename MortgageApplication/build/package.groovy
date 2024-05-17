import java.io.File
import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import org.apache.http.entity.FileEntity
import com.ibm.dbb.build.*
import com.ibm.dbb.build.DBBConstants.CopyMode
import com.ibm.dbb.build.report.BuildReport
import com.ibm.dbb.build.report.records.DefaultRecordFactory

/************************************************************************************
 * This script creates an archive with the outputs generated from a build in the
   work directory of the build.
 *
 ************************************************************************************/
 def usage = "package.groovy [options]"  
 
// load the Tools.groovy utility script
def scriptDir = new File(getClass().protectionDomain.codeSource.location.path).parent
File sourceFile = new File("$scriptDir/Tools.groovy")
Class groovyClass = new GroovyClassLoader(getClass().getClassLoader()).parseClass(sourceFile)
GroovyObject tools = (GroovyObject) groovyClass.newInstance()
 
def opts = tools.parseArgs(args, usage)
def properties = tools.loadProperties(opts)  

def workDir = properties.workDir
def loadDatasets = properties.loadDatasets

//Retrieve the build report and parse the outputs from the build report
def buildReportFile = new File("$workDir/BuildReport.json")
assert buildReportFile.exists(), "$buildReportFile does not exist"

def buildReport = BuildReport.parse(buildReportFile.newInputStream())
def executes = buildReport.records.findAll { record ->
    record.type == DefaultRecordFactory.TYPE_EXECUTE && !record.outputs.isEmpty()
}

assert executes.size() > 0, "There are no outputs found in the build report"

//If the user specifies the build property 'loadDatasets' then retrieves it
//and filters out only outputs that match with the specified data sets.

def loadDatasetArray  = loadDatasets?.split(",")
def loadDatasetList = loadDatasetArray == null ? [] : Arrays.asList(loadDatasetArray)

def loadDatasetToMembersMap = [:]
def loadCount = 0
executes.each { execute ->
    execute.outputs.each { output ->
        def (dataset, member) = output.dataset.split("\\(|\\)")
        if (loadDatasetList.isEmpty() || loadDatasetList.contains(dataset))
        {
            if (loadDatasetToMembersMap[dataset] == null)
                loadDatasetToMembersMap[dataset] = []
            loadDatasetToMembersMap[dataset].add(member)
            loadCount++
        }
    }
}

assert loadCount > 0, "There are no load modules to publish"

//Create a temporary directory on zFS to copy the load modules from data sets to
def tempLoadDir = new File("$workDir/tempLoadDir")
!tempLoadDir.exists() ?: tempLoadDir.deleteDir()
tempLoadDir.mkdirs()

//For each load modules, use CopyToHFS with option 'CopyMode.LOAD' to maintain
//SSI and
CopyToHFS copy = new CopyToHFS().copyMode(CopyMode.LOAD)
println "Number of load modules to package: $loadCount"
loadDatasetToMembersMap.each { dataset, members ->
    members.each { member ->
        def fullyQualifiedDsn = "$dataset($member)"
        def file = new File(tempLoadDir, member)
        copy.dataset(dataset).member(member).file(file).copy()
        println "Copying $dataset($member) to $tempLoadDir"
    }
}

//Package the load files just copied into a tar file named archive.tar.

def tarFile = new File("$tempLoadDir/archive.tar")
def process = "tar -cvf $tarFile .".execute(null, tempLoadDir)
int rc = process.waitFor()
assert rc == 0, "Failed to package load modules"
println "created archive file: $tarFile"
