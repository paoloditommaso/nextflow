package nextflow.cloud.gce.pipelines

import com.google.api.services.genomics.v2alpha1.model.*
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import nextflow.exception.ProcessUnrecoverableException
import nextflow.processor.TaskBean
import nextflow.processor.TaskHandler
import nextflow.processor.TaskRun
import nextflow.processor.TaskStatus

import java.nio.file.Path

@Slf4j
class GooglePipelinesTaskHandler extends TaskHandler {

    final GooglePipelinesExecutor executor
    final TaskBean taskBean
    final GooglePipelinesConfiguration pipeConfig

    final String taskName
    final String taskInstanceName

    private final Path exitFile
    private final Path wrapperFile
    private final Path outputFile
    private final Path errorFile
    private final Path logFile
    private final Path scriptFile
    private final Path inputFile
    private final Path stubFile
    private final Path traceFile

    //Constants
    final static String mountPath = "/work"
    final static String diskName = "nf-pipeline-work"
    final static String fileCopyImage = "google/cloud-sdk:alpine"



    Mount sharedMount
    Pipeline taskPipeline

    private Operation operation
    private Metadata metadata

    @PackageScope
    final List<String> stagingCommands = []
    @PackageScope
    final List<String> unstagingCommands = []

    GooglePipelinesTaskHandler(TaskRun task, GooglePipelinesExecutor executor, GooglePipelinesConfiguration pipeConfig) {
        super(task)
        this.executor = executor
        this.taskBean = new TaskBean(task)
        this.pipeConfig = pipeConfig

        this.logFile = task.workDir.resolve(TaskRun.CMD_LOG)
        this.scriptFile = task.workDir.resolve(TaskRun.CMD_SCRIPT)
        this.inputFile = task.workDir.resolve(TaskRun.CMD_INFILE)
        this.outputFile = task.workDir.resolve(TaskRun.CMD_OUTFILE)
        this.errorFile = task.workDir.resolve(TaskRun.CMD_ERRFILE)
        this.exitFile = task.workDir.resolve(TaskRun.CMD_EXIT)
        this.wrapperFile = task.workDir.resolve(TaskRun.CMD_RUN)
        this.stubFile = task.workDir.resolve(TaskRun.CMD_STUB)
        this.traceFile = task.workDir.resolve(TaskRun.CMD_TRACE)

        this.taskName = GooglePipelinesHelper.sanitizeName("nf-task-${executor.session.uniqueId}-${task.name}")
        this.taskInstanceName = GooglePipelinesHelper.sanitizeName("$taskName-$task.id")

        validateConfiguration()

        log.debug "[GOOGLE PIPELINE] Created handler for task '${task.name}'."
    }

    void validateConfiguration() {
        if (!task.container) {
            throw new ProcessUnrecoverableException("No container is specified for process $task.name . Either specify the container to use in the process definition or with 'process.container' value in your config")
        }
    }

    @Override
    boolean checkIfRunning() {
        operation = executor.helper.checkOperationStatus(operation)
        return !operation.getDone()
    }

    @Override
    //TODO: Catch pipeline errors and report them back
    boolean checkIfCompleted() {
        operation = executor.helper.checkOperationStatus(operation)

        def events = extractRuntimeDataFromOperation()
        events?.reverse()?.each {
            log.trace "[GOOGLE PIPELINE] New event for task '$task.name' - time: ${it.get("timestamp")} - ${it.get("description")}"
        }

        if (operation.getDone()) {
            log.debug "[GOOGLE PIPELINE] Task '$task.name' complete. Start Time: ${metadata?.getStartTime()} - End Time: ${metadata?.getEndTime()}"

            // finalize the task
            task.exitStatus = readExitFile()
            task.stdout = outputFile
            task.stderr = errorFile
            status = TaskStatus.COMPLETED
            return true
        } else
            return false
    }

    private List<Event> extractRuntimeDataFromOperation() {
        def metadata = operation.getMetadata() as Metadata
        if (!this.metadata) {
            this.metadata = metadata
            return metadata?.getEvents()
        } else {
            //Get the new events
            def delta = metadata.getEvents().size() - this.metadata.getEvents().size()
            this.metadata = metadata
            return delta > 0 ? metadata.getEvents().take(delta) : [] as List<Event>
        }
    }

    private int readExitFile() {
        try {
            exitFile.text as Integer
        }
        catch (Exception e) {
            log.debug "[GOOGLE PIPELINE] Cannot read exitstatus for task: `$task.name`", e
            return Integer.MAX_VALUE
        }
    }

    @Override
    void kill() {
        log.debug "[GOOGLE PIPELINE] Killing pipeline '$operation.name'"
        executor.helper.cancelOperation(operation)
    }

    @Override
    void submit() {

        final launcher = new GooglePipelinesScriptLauncher(this.taskBean, this)
        launcher.build()

        //TODO: chmod 777 is bad m'kay
        //TODO: eliminate cd command as well as wildcard +x
        String stagingScript = """
           mkdir -p $task.workDir ;
           chmod 777 $task.workDir ;
           ${stagingCommands.join(" ; ")} ;
           cd $task.workDir ;
           chmod 777 ${TaskRun.CMD_SCRIPT} ${TaskRun.CMD_RUN}            
        """.stripIndent().leftTrim()

        String mainScript = "cd ${task.workDir} ; echo \$(./${TaskRun.CMD_RUN}) | bash 2>&1 | tee ${TaskRun.CMD_LOG}"

        /*
         * -m = run in parallel
         * -q = quiet mode
         * cp = copy
         * -P = preserve POSIX attributes
         * -c = continues on errors
         * -r = recursive copy
         */
        def gsCopyPrefix = "gsutil -m -q cp -P -c"

        //Copy the logs provided by Google Pipelines for the pipline to our work dir.
        if (System.getenv().get("NXF_DEBUG")) {
            unstagingCommands << "$gsCopyPrefix -r /google/ ${task.workDir.toUriString()} || true".toString()
        }

        //add the task output files to unstaging command list
        [TaskRun.CMD_ERRFILE,
         TaskRun.CMD_OUTFILE,
         TaskRun.CMD_EXIT,
         TaskRun.CMD_LOG
        ].each {
            unstagingCommands << "$gsCopyPrefix ${task.workDir}/$it ${task.workDir.toUriString()} || true".toString()
        }

        //Copy nextflow task progress files as well as the files we need to unstage
        String unstagingScript = """                                                
            ${unstagingCommands.join(" ; ")}                        
        """.stripIndent().leftTrim()

        log.debug "Staging script for task $task.name -> $stagingScript"
        log.debug "Main script for task $task.name -> $mainScript"
        log.debug "Unstaging script for task $task.name -> $unstagingScript"

        //Create the mount for out work files.
        sharedMount = executor.helper.configureMount(diskName, mountPath)

        //need the cloud-platform scope so that we can execute gsutil cp commands
        def resources = executor.helper.configureResources(pipeConfig.vmInstanceType, pipeConfig.project, pipeConfig.zone, diskName, [GooglePipelinesHelper.SCOPE_CLOUD_PLATFORM], pipeConfig.preemptible)

        def stagingAction = executor.helper.createAction("$taskInstanceName-staging".toString(), fileCopyImage, ["bash", "-c", stagingScript], [sharedMount], [GooglePipelinesHelper.ActionFlags.ALWAYS_RUN, GooglePipelinesHelper.ActionFlags.IGNORE_EXIT_STATUS])

        //TODO: Do we really want to override the entrypoint?
        def mainAction = executor.helper.createAction(taskInstanceName, task.container, ['-o', 'pipefail', '-c', mainScript], [sharedMount], [], "bash")

        def unstagingAction = executor.helper.createAction("$taskInstanceName-unstaging".toString(), fileCopyImage, ["bash", "-c", unstagingScript], [sharedMount], [GooglePipelinesHelper.ActionFlags.ALWAYS_RUN, GooglePipelinesHelper.ActionFlags.IGNORE_EXIT_STATUS])

        taskPipeline = executor.helper.createPipeline([stagingAction, mainAction, unstagingAction], resources)

        operation = executor.helper.runPipeline(taskPipeline)

        log.trace "[GOOGLE PIPELINE] Submitted task '$task.name. Assigned Pipeline operation name = '${operation.getName()}'"
    }
}