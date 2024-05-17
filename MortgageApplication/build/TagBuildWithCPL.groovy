@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript

import com.ibm.dbb.build.*
import com.ibm.dbb.build.report.*
import com.ibm.dbb.repository.*
import com.ibm.dbb.dependency.*
import groovy.time.*

def usage = "TagBuildWithCPL.groovy [options]" 
 
println("Debut du script valid")
// load the Tools.groovy utility script
def tools = loadScript(new File("Tools.groovy"))

def opts = tools.parseArgs(args, usage)  
def properties = tools.loadProperties(opts)  

RepositoryClient repoclient = tools.getDefaultRepositoryClient()

// Fin simulation d'un build & build result
println ("retrieve last build result from collection " + properties.collection)
def lastBuildResult = repoclient.getLastBuildResult(properties.collection, BuildResult.COMPLETE, BuildResult.CLEAN)
println ("check last build result" + lastBuildResult)
 assert lastBuildResult, "can't find last buils result" 

lastBuildResult.addProperty("Deliver", "CPL")
lastBuildResult.save()
println("check new property"+ lastBuildResult)
