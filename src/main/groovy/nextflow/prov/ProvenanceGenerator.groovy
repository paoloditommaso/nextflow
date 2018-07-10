package nextflow.prov

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.trace.TraceRecord
import org.openprovenance.prov.interop.InteropFramework
import org.openprovenance.prov.model.Activity
import org.openprovenance.prov.model.Agent
import org.openprovenance.prov.model.Document
import org.openprovenance.prov.model.Entity
import org.openprovenance.prov.model.Namespace
import org.openprovenance.prov.model.ProvFactory
import org.openprovenance.prov.model.QualifiedName
import org.openprovenance.prov.model.Used
import org.openprovenance.prov.model.WasAssociatedWith
import org.openprovenance.prov.model.WasGeneratedBy

import javax.xml.datatype.DatatypeFactory
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

/**
 * Created by edgar on 19/06/18.
 */
@Slf4j
@CompileStatic
public class ProvenanceGenerator {
    enum ProvenanceType {
        activityType, fileChecksum, fileSize, fileName
    }
    //** PROV info **
    public static final String PROVBOOK_NS = "prov";
    public static final String PROVBOOK_PREFIX = "PROV";

    private final String CHECKSUM_TYPE = "SHA-256"
    private final String provFileName = "provenance.json"
    private final String activity_prefix = "activity_"
    private final String used_prefix = "used_"
    private final String generatedBy_prefix = "generatedBy_"
    private final String agent_prefix="agent_"
    private final String associatedWith_prefix = "associatedWith_"

    private final ProvFactory pFactory = InteropFramework.newXMLProvFactory();
    private final Namespace ns;

    ProvenanceGenerator(){
        ns = new Namespace();
        ns.addKnownNamespaces();
        ns.register(PROVBOOK_PREFIX, PROVBOOK_NS);
    }

    public QualifiedName qn(String n) {
        return ns.qualifiedName(PROVBOOK_PREFIX, n, pFactory);
    }

    public Document getProvDocument(){ //TODO make it singleton
        return pFactory.newDocument()
    }
    private Map<QualifiedName, Entity> inputEntityMap = new HashMap<QualifiedName, Entity>();
    //TODO Can merge both entity maps (I guess)
    private Map<QualifiedName, Entity> outputEntityMap = new HashMap<QualifiedName, Entity>();
    private Map<QualifiedName, Activity> activityMap = new HashMap<QualifiedName, Activity>();
    private Map<QualifiedName, Used> usedMap = new HashMap<QualifiedName, Used>();
    private Map<QualifiedName, WasGeneratedBy> generatedMap = new HashMap<QualifiedName, WasGeneratedBy>();
    private Map<QualifiedName, WasAssociatedWith> associatedMap = new HashMap<QualifiedName, WasAssociatedWith>();
    private Map<QualifiedName, Agent> agentMap = new HashMap<QualifiedName, Agent>();

    public void setElementsToProvFile(Document provDocument) {
        /**
         * Fill the PROV document with the inputs entities
         */
        log.debug "Input Entity List: ${inputEntityMap.size()}"
        if (inputEntityMap.isEmpty()) {
            log.debug "INPUT is empty"
        } else {
            for (Map.Entry<QualifiedName, Entity> entity : inputEntityMap.entrySet()) {
                provDocument.getStatementOrBundle().add(entity.value)
            }
        }
        /**
         * Fill the PROV document with the output entities
         */
        log.debug "Output Entity List:: ${outputEntityMap.size()}"
        if (outputEntityMap.isEmpty()) {
            log.debug "OUTPUT is empty"
        } else {
            for (Map.Entry<QualifiedName, Entity> entity : outputEntityMap.entrySet()) {
                provDocument.getStatementOrBundle().add(entity.value)
            }
        }
        //ACTIVITY
        for (Map.Entry<QualifiedName, Activity> activity : activityMap.entrySet()) {
            provDocument.getStatementOrBundle().add(activity.value)
        }
        //USED
        for (Map.Entry<QualifiedName, Used> used : usedMap.entrySet()) {
            provDocument.getStatementOrBundle().add(used.value)
        }
        //WAS GENERATED BY
        for (Map.Entry<QualifiedName, WasGeneratedBy> generated : generatedMap.entrySet()) {
            provDocument.getStatementOrBundle().add(generated.value)
        }
        //ASSOCIATED WITH by
        for (Map.Entry<QualifiedName, WasAssociatedWith> associated : associatedMap.entrySet()) {
            provDocument.getStatementOrBundle().add(associated.value)
        }
        //AGENT
        for (Map.Entry<QualifiedName, Agent> agent : agentMap.entrySet()) {
            provDocument.getStatementOrBundle().add(agent.value)
        }

    }

    public generateProvenance(TraceRecord trace){
        Activity activity_object= generateActivities(trace)

        /**
         * Get the I/O objects from the trace.
         * Convert the string into a List<String> to iterate it
         */
        def inputFiles = trace.getFmtStr("input")
        List<String> inputList = Arrays.asList(inputFiles.split(";"));

        def outputFiles = trace.getFmtStr("output")
        List<String> outputList = Arrays.asList(outputFiles.split(";"));

        /**
         * Iterate the INPUT list to get the values we need
         */
        generateInputEntity(trace, inputList, activity_object)

        /**
         * Iterate the OUTPUT list to get the values we need
         */
        generateOutputEntity(trace, outputList, activity_object)

        //TODO ERRO WITH QN
        // generateSoftwareAgent(activity_object,trace)
    }

    public void generateProvFile(Document provDocument){
        provDocument.setNamespace(ns);
        InteropFramework intF=new InteropFramework();
        intF.writeDocument(provFileName, provDocument);
    }

    private Activity generateActivities(TraceRecord trace){
        /**
         * Code to generate the ACTITY object.
         * It's the object who represents the process itself
         */
        String activityId = "${activity_prefix}${trace.getTaskId()}"
        Activity activity_object = pFactory.newActivity(qn(activityId.toString()));

        String typeAux = "Process"
        Object typeObj = typeAux
        pFactory.addType(activity_object, typeObj, qn(ProvenanceType.activityType.toString()))

        pFactory.addLabel(activity_object, trace.get("name").toString())

        setActivityTime(activity_object, trace)

        activityMap.put(activity_object.getId(), activity_object)

        return activity_object
    }

    private void setActivityTime(Activity activity_object, TraceRecord trace){
        /**
         * add start adn end time to the activity
         */
        //convert miliseconds to Gregorain
        final GregorianCalendar calendarGregStart = new GregorianCalendar();
        calendarGregStart.setTimeInMillis(trace.get("start") as long);
        def gregorianStart = DatatypeFactory.newInstance().newXMLGregorianCalendar(calendarGregStart);
        activity_object.setStartTime(gregorianStart)

        final GregorianCalendar calendarGregEnd = new GregorianCalendar();
        calendarGregEnd.setTimeInMillis(trace.get("complete") as long);
        def gregorianEnd = DatatypeFactory.newInstance().newXMLGregorianCalendar(calendarGregEnd);
        activity_object.setEndTime(gregorianEnd)
    }

    private void generateSoftwareAgent(Activity activity_object, TraceRecord trace){
        String associatedWithId = "${associatedWith_prefix}_${trace.getTaskId()}"
        String softwareId = "${agent_prefix}_${activity_object.getId()}"

        Agent softwareAgent = new org.openprovenance.prov.xml.Agent()
        softwareAgent.setId(qn(softwareId.toString()))
        pFactory.addLabel(softwareAgent, trace.get('script').toString())
        pFactory.newAgent(softwareAgent)

        WasAssociatedWith associatedWith = pFactory.newWasAssociatedWith(qn(associatedWithId.toString()),activity_object.getId(),softwareAgent.getId()) //id, activity, agent

        associatedMap.put(associatedWith.getId(), associatedWith)
        agentMap.put(softwareAgent.getId(), softwareAgent)
    }

    private void generateInputEntity(TraceRecord trace, List<String> inputList, Activity activity_object){

        for (elem in inputList) {
            Path pathAux = Paths.get(elem);
            String pathString = pathAux.toString().trim()
            //remove space from the begining of the path.. need to avoid uncomprensive behaviour
            def entity_name = pathAux.getFileName()
            //XXX check if the ELEM is already on the inputList (global) or not --> done with a MAP
            Entity input_entity = pFactory.newEntity(qn(pathString.toString()));

            input_entity.setValue(pFactory.newValue(entity_name.toString(), qn(ProvenanceType.fileName.toString())))

            File fileAux = new File(pathString)

            Object checkAux = getFileSHA256(fileAux)
            pFactory.addType(input_entity, checkAux, qn(ProvenanceType.fileChecksum.toString()))

            Object sizeAux = fileAux.length()
            pFactory.addType(input_entity, sizeAux, qn(ProvenanceType.fileSize.toString()))

            /*
             Create the relation btwn the ACTIVITY and the ENTITY
              */
            Used usedAction = pFactory.newUsed(activity_object.getId(), input_entity.getId());
            usedAction.setId(qn("${used_prefix}${trace.getTaskId()}_${pathString}"))
            usedMap.put(usedAction.getId(), usedAction)

            /*
            Save the input element as an ENTITY inside the GLOBAL list of the Input entities
             */
            inputEntityMap.put(input_entity.getId(), input_entity)
        }
    }

    private void generateOutputEntity(TraceRecord trace, List<String> outputList, Activity activity_object){
        for (elem in outputList) {
            Path pathAux = Paths.get(elem);
            String pathString = pathAux.toString().trim()

            //remove space from the begining of the path.. need to avoid uncomprensive behaviour
            def entity_name = pathAux.getFileName()

            Entity output_entity = pFactory.newEntity(qn(pathString.toString()));

            output_entity.setValue(pFactory.newValue(entity_name.toString(), qn(ProvenanceType.fileName.toString())))

            File fileAux = new File(pathString)
            Object checkAux = getFileSHA256(fileAux)
            pFactory.addType(output_entity, checkAux, qn(ProvenanceType.fileChecksum.toString()))

            Object sizeAux = fileAux.length()
            pFactory.addType(output_entity, sizeAux, qn(ProvenanceType.fileSize.toString()))

            /*
            Create the relation btwn ACTIVITY and the ENTITY
             */
            WasGeneratedBy generationAction = pFactory.newWasGeneratedBy(output_entity, "", activity_object)
            generationAction.setId(qn("${generatedBy_prefix}${trace.getTaskId()}_${pathString}"))
            generatedMap.put(generationAction.getId(), generationAction)

            /*
            Save the input element as a ENTITY inside the GLOBAL list of the Input entities
             */
            outputEntityMap.put(output_entity.getId(), output_entity)
        }
    }

    private String getFileSHA256(File fileAux ){
        //-- Digest sha256
        // https://gist.github.com/ikarius/299062/85b6540c99878f50f082aaee236ef15fc78e527c
        MessageDigest digest = MessageDigest.getInstance(CHECKSUM_TYPE);
        fileAux.withInputStream() { is ->
            byte[] buffer = new byte[8192]
            int read = 0
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] elementHash = digest.digest()
        return Base64.getEncoder().encodeToString(elementHash).toString()
    }
}
