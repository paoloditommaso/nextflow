package nextflow.cloud.gce

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import nextflow.exception.AbortOperationException
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

@SuppressWarnings("UnnecessaryQualifiedReference")
class GceApiHelperTest extends Specification {

    static String testProject = "testProject"
    static String testZone = "testZone"

    static boolean runAgainstGce() {
        System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
    }

    @Shared
    GceApiHelper sharedHelper

    def setupSpec() {

        sharedHelper = runAgainstGce() ?
                Spy(GceApiHelper, constructorArgs: [testProject,testZone])  :
                Spy(GceApiHelper, constructorArgs: [testProject,testZone,Stub(Compute)])

        sharedHelper.readGoogleMetadata(_) >> "metadata"
    }

    def 'should report error when region and zone are null'() {
        when:
        new GceApiHelper(null,null)
        then:
        thrown(AbortOperationException)
    }

    @IgnoreIf({GceApiHelperTest.runAgainstGce()})
    //If we have a google credentials file, we can read the project name from it
    def 'should report error when project is missing in initialization'() {
        when:
        new GceApiHelper(null,testZone)
        then:
        thrown(AbortOperationException)
    }

    def 'should report error when zone is missing in initialization'() {
        when:
        new GceApiHelper(testProject,null)
        then:
        thrown(AbortOperationException)
    }

    //TODO: See is we can also read this data form the credentials file
    def 'should read metadata if it is available'() {
        when:
        def project = sharedHelper.readProject()

        then:
        if(runAgainstGce())
            assert project != "metadata" //should get a real project name here
        else
            assert project == "metadata" //should get the stubbed out value

        when:
        def zone = sharedHelper.readZone()
        then:
        zone == "metadata"

        when:
        def instanceId = sharedHelper.readInstanceId()
        then:
        instanceId == "metadata"
    }

    def 'should return a valid boot disk'() {
        when:
        AttachedDisk disk = sharedHelper.createBootDisk("testDisk","testimage")
        then:
        disk.getBoot()
        disk.getInitializeParams().getDiskName() == "testDisk"
        disk.getInitializeParams().getSourceImage() == GceApiHelper.imageName("testimage")
    }

    def 'should return a valid network interface'() {
        when:
        NetworkInterface netInt = sharedHelper.createNetworkInterface()
        then:
        netInt as NetworkInterface
    }

    //This is, of course, by no means a definite test that ensures that all names will be unique, but it will hopefully catch silly mistakes
    def 'should return random names'() {
        when:
        def randomNames = []
        1000.times {randomNames << sharedHelper.randomName()}
        def baseRandomNames = []
        1000.times {baseRandomNames << sharedHelper.randomName("test-")}
        then:
        randomNames.every {testString -> randomNames.count {it == testString} == 1}
        baseRandomNames.every {testString -> testString.startsWith("test") && baseRandomNames.count {it == testString} == 1}
        randomNames != baseRandomNames
    }

    def 'should validate label values correctly'() {
        given:
        def tooLong = ""
        64.times {tooLong += "x"}
        def justRight = ""
        63.times {justRight += "x"}

        when:
        def tlres = sharedHelper.validateLabelValue(tooLong)
        def jrres = sharedHelper.validateLabelValue(justRight)
        def illegalres = sharedHelper.validateLabelValue("æ !#%&")
        def legalres = sharedHelper.validateLabelValue("12345abcde")
        then:
        tlres != null
        jrres == null
        illegalres != null
        legalres == null
    }

    def 'should create correct scheduling'() {
        when:
        def nonPre = sharedHelper.createScheduling(false)
        def Pre = sharedHelper.createScheduling(true)
        then:
        !nonPre.getPreemptible()
        Pre.getPreemptible()
    }

    //TODO: Need to discover if there is "official" instructions how to do this correctly
    def 'should convert public ip to google dns name correctly'() {
        when:
        def ret = sharedHelper.publicIpToDns("192.168.1.254")
        then:
        ret == "254.1.168.192.bc.googleusercontent.com"
    }

    def 'should throw an error when converting an illegal public ip'() {
        when:
        sharedHelper.publicIpToDns("12.12")
        then:
        thrown(IllegalArgumentException)
    }

    def 'should attach scripts as metadata to an instance'() {
        given:
        Instance gceInstance = new Instance()
        when:
        sharedHelper.setStartupScript(gceInstance,"startup")
        sharedHelper.setShutdownScript(gceInstance,"shutdown")
        def metadata = gceInstance.getMetadata()
        then:
        metadata.getItems().any{it.getKey() == "startup-script" && it.getValue() == "startup"}
        metadata.getItems().any{it.getKey() == "shutdown-script" && it.getValue() == "shutdown"}

    }

    def 'should block until a GCE operation returns status "DONE"'() {
        given:
        Compute.GlobalOperations globalOperations = Mock()
        Compute compute = Mock(Compute)

        def runningOp = new Operation().setStatus("RUNNING")
        def doneOp = new Operation().setStatus("DONE")

        Compute.GlobalOperations.Get computeGlobalOperations = Stub()
        computeGlobalOperations.execute() >>> [runningOp,doneOp]

        GceApiHelper helper = Spy(GceApiHelper,constructorArgs: [testProject,testZone,compute])

        when:
        def ret = helper.blockUntilComplete(runningOp,100,10)
        then:
        (1.._) * globalOperations.get(_,_) >>{
            computeGlobalOperations
        }
        (1.._) * compute.globalOperations() >> {
            globalOperations
        }
        !ret
    }

    def 'should block until multiple GCE operations returns status "DONE"'() {
        given:
        Compute.GlobalOperations globalOperations = Mock()
        Compute compute = Mock(Compute)

        def runningOp = new Operation().setStatus("RUNNING")
        def doneOp = new Operation().setStatus("DONE")

        Compute.GlobalOperations.Get computeGlobalOperations = Stub()
        computeGlobalOperations.execute() >>> [runningOp,doneOp,runningOp,doneOp]

        GceApiHelper helper = Spy(GceApiHelper,constructorArgs: [testProject,testZone,compute])

        when:
        def ret = helper.blockUntilComplete([runningOp,runningOp],100,10)
        then:
        (1.._) * globalOperations.get(_,_) >>{
            computeGlobalOperations
        }
        (1.._) * compute.globalOperations() >> {
            globalOperations
        }
        !ret
    }

    def 'should timeout while waiting too long for an operation to complete'() {
        given:
        Compute.GlobalOperations globalOperations = Mock()
        Compute compute = Mock(Compute)

        def runningOp = new Operation().setStatus("RUNNING")

        Compute.GlobalOperations.Get computeGlobalOperations = Stub()
        computeGlobalOperations.execute() >> {runningOp}

        GceApiHelper helper = Spy(GceApiHelper,constructorArgs: [testProject,testZone,compute])

        when:
        helper.blockUntilComplete(runningOp,100,10)
        then:
        (1.._) * globalOperations.get(_,_) >>{
            computeGlobalOperations
        }
        (1.._) * compute.globalOperations() >> {
            globalOperations
        }
        thrown(InterruptedException)
    }

    def 'should timeout while waiting too long for multiple operations to complete'() {
        given:
        Compute.GlobalOperations globalOperations = Mock()
        Compute compute = Mock(Compute)

        def runningOp = new Operation().setStatus("RUNNING")
        def doneOp = new Operation().setStatus("DONE")

        Compute.GlobalOperations.Get computeGlobalOperations = Stub()
        computeGlobalOperations.execute() >>> [runningOp,doneOp,runningOp,runningOp]

        GceApiHelper helper = Spy(GceApiHelper,constructorArgs: [testProject,testZone,compute])

        when:
        helper.blockUntilComplete([runningOp,runningOp],100,50)
        then:
        (1.._) * globalOperations.get(_,_) >>{
            computeGlobalOperations
        }
        (1.._) * compute.globalOperations() >> {
            globalOperations
        }
        thrown(InterruptedException)
    }


    @IgnoreIf({!GceApiHelperTest.runAgainstGce()})
    def 'should get the google credentials file'() {
        when:
        def fileContent = sharedHelper.getCredentialsFile()
        then:
        fileContent
    }

    @IgnoreIf({!GceApiHelperTest.runAgainstGce()})
    def 'should read project name from google credentials file'() {
        when:
        def project = sharedHelper.readProject()
        then:
        project != "metadata"
    }

    def 'should get a list of instances from gce api'() {
        given:
        Compute compute = Mock()
        Compute.Instances instances = Mock()
        Compute.Instances.List list = Mock()
        GceApiHelper helper = new GceApiHelper("testProject","testZone",compute)
        when:
        def instList = helper.getInstanceList("")
        then:
        1 * compute.instances() >> {instances}
        1 * instances.list(_,_) >> {list}
        1 * list.execute() >>{
            new InstanceList().setItems([new Instance().setName("instance")])
        }

        instList.size() == 1
        instList.get(0).getName() == "instance"
    }


}
