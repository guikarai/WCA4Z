@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.build.*
import java.nio.file.*

// load the Tools.groovy utility script
def tools = loadScript(new File("Tools.groovy"))
def scriptDir = getScriptDir()

// parse command line arguments and load build properties
def usage = "impact.groovy [options]"
def opts = parseArgs(args, usage)

// create workdir (if necessary)
new File(opts.w).mkdirs()
    
println("** Copying $scriptDir/files.txt to $opts.w/buildList.txt ")
Files.copy(Paths.get("$scriptDir/files.txt"), Paths.get("$opts.w/buildList.txt"))
    
    
System.exit(0)
	
	
	def parseArgs(String[] cliArgs, String usage) {
	def cli = new CliBuilder(usage: usage)
	cli.s(longOpt:'sourceDir', args:1, argName:'dir', 'Absolute path to source directory')
	cli.w(longOpt:'workDir', args:1, argName:'dir', 'Absolute path to the build output directory')
	cli.b(longOpt:'buildHash', args:1, argName:'hash', 'Git commit hash for the build')
	cli.l(longOpt:'lastBuildHash', args:1, argName:'hash', 'Git commit hash for the last build')
	cli.q(longOpt:'hlq', args:1, argName:'hlq', 'High level qualifier for partition data sets')
	cli.c(longOpt:'collection', args:1, argName:'name', 'Name of the dependency data collection')
	cli.t(longOpt:'team', args:1, argName:'hlq', 'Team build hlq for user build syslib concatenations')
	cli.r(longOpt:'repo', args:1, argName:'url', 'DBB repository URL')
	cli.i(longOpt:'id', args:1, argName:'id', 'DBB repository id')
	cli.p(longOpt:'pw', args:1, argName:'password', 'DBB password')
	cli.P(longOpt:'pwFile', args:1, argName:'file', 'Absolute or relative (from sourceDir) path to file containing DBB password')
	cli.e(longOpt:'logEncoding', args:1, argName:'encoding', 'Encoding of output logs. Default is EBCDIC')
	cli.u(longOpt:'userBuild', 'Flag indicating running a user build')
	cli.E(longOpt:'errPrefix', args:1, argName:'errorPrefix', 'Unique id used for IDz error message datasets')
	cli.h(longOpt:'help', 'Prints this message')
	cli.C(longOpt:'clean', 'Deletes the dependency collection and build reeult group from the DBB repository then terminates (skips build)')

	def opts = cli.parse(cliArgs)
	if (opts.h) { // if help option used, print usage and exit
		 cli.usage()
		System.exit(0)
	}

	return opts
}