![Nextflow logo](https://github.com/nextflow-io/trademark/blob/master/nextflow2014_no-bg.png)

*"Dataflow variables are spectacularly expressive in concurrent programming"*
<br>[Henri E. Bal , Jennifer G. Steiner , Andrew S. Tanenbaum](http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.145.7873)


[![Chat on Gitter](https://img.shields.io/gitter/room/nextflow-io/nextflow.svg?colorB=26af64&style=popout)](https://gitter.im/nextflow-io/nextflow)
[![Nextflow version](https://img.shields.io/github/release/nextflow-io/nextflow.svg?colorB=26af64&style=popout)](https://github.com/nextflow-io/nextflow/releases/latest)
[![Nextflow Twitter](https://img.shields.io/twitter/url/https/nextflowio.svg?colorB=26af64&style=popout)](https://twitter.com/nextflowio)
[![Nextflow Publication](https://img.shields.io/badge/Published-Nature%20Biotechnology-26af64.svg?colorB=26af64&style=popout)](https://www.nature.com/articles/nbt.3820)
[![install with bioconda](https://img.shields.io/badge/install%20with-bioconda-brightgreen.svg?colorB=26af64&style=popout)](http://bioconda.github.io/recipes/nextflow/README.html)
[![Nextflow license](https://img.shields.io/github/license/nextflow-io/nextflow.svg?colorB=26af64&style=popout)](https://github.com/nextflow-io/nextflow/blob/master/COPYING)

Quick overview
==============
Nextflow is a bioinformatics workflow manager that provides tools for developing portable and reproducible workflows.
It supports deploying workflows on a variety of execution platforms including local, HPC schedulers, AWS Batch,
Google Genomics Pipelines API, and Kubernetes. Additionally, it provides support for manage your workflow dependencies
through built-in support for Conda, Docker, Singularity, and Modules.

## Contents
- [Installation](#installation)
- [Documentation](#documentation)
- [Tool Management](#tool-management)
  - [Docker and Singularity](#containers)
- [HPC Schedulers](#hpc-schedulers)
  - [SGE](#hpc-schedulers)
  - [Univa Grid Engine](#hpc-schedulers)
  - [LSF](#hpc-schedulers)
  - [SLURM](#hpc-schedulers)
  - [PBS/Torque](#hpc-schedulers)
  - [HTCondor (experimental)](#hpc-schedulers)
- [Cloud Support](#cloud-support)
  - [Kubernetes](#cloud-support)
  - [Google Cloud](#cloud-support)
  - [AWS Batch](#cloud-support)
  - [Amazon Cloud](#cloud-support)
  - [Google Genomics Pipelines API](#cloud-support)
- [Build from source](#build-from-source)
- [Contributing](#contributing)
- [License](#license)
- [Citations](#citations)
- [Credits](#credits)


Installation
============

Download the package
--------------------

Nextflow does not require any installation procedure, just download the distribution package by copying and pasting
this command in your terminal:

```
curl -fsSL get.nextflow.io | bash
```

It creates the ``nextflow`` executable file in the current directory. You may want to move it to a folder accessible from your ``$PATH``.

Download from Conda
-------------------

Nextflow can also be installed from Bioconda [![install with bioconda](https://img.shields.io/badge/install%20with-bioconda-brightgreen.svg?style=flat-square)](http://bioconda.github.io/recipes/nextflow/README.html)

Documentation
=============

Nextflow documentation is available at this link http://docs.nextflow.io


Tool management
================

A key goal of *Nextflow* is to enable portable and reproducible workflows. Both Docker and Singularity container engines are supported. 



```nextflow
process samtools {
  container='biocontainers/samtools:1.3.1'

  """
  samtools --version 
  """

}
```




HPC Schedulers
==============

*Nextflow* supports common HPC schedulers, abstracting the submission of jobs from the user. 

Currently the following clusters are supported:

  + [SGE](https://www.nextflow.io/docs/latest/executor.html#sge)
  + [Univa Grid Engine](https://www.nextflow.io/docs/latest/executor.html#sge)
  + [LSF](https://www.nextflow.io/docs/latest/executor.html#lsf)
  + [SLURM](https://www.nextflow.io/docs/latest/executor.html#slurm)
  + [PBS/Torque](https://www.nextflow.io/docs/latest/executor.html#pbs-torque)
  + [HTCondor (experimental)](https://www.nextflow.io/docs/latest/executor.html#htcondor)

For example to submit the execution to a SGE cluster create a file named `nextflow.config`, in the directory
where the pipeline is going to be launched, with the following content:

```nextflow
process {
  executor='sge'
  queue='<your execution queue>'
}
```

In doing that, processes will be executed by Nextflow as SGE jobs using the `qsub` command. Your 
pipeline will behave like any other SGE job script, with the benefit that *Nextflow* will 
automatically and transparently manage the processes synchronisation, file(s) staging/un-staging, etc.  


Cloud support
=============
*Nextflow* also supports running workflows through various cloud platforms. This includes creation of clusters on both Amazon AWS and Google GCE. 

Currently supported cloud platforms:
  + [Kubernetes](https://www.nextflow.io/docs/latest/kubernetes.html)
  + [AWS Batch](https://www.nextflow.io/docs/latest/awscloud.html#aws-batch)
  + [Amazon AWS](https://www.nextflow.io/docs/latest/awscloud.html)
  + [Google GCE](https://www.nextflow.io/docs/latest/google.html)
  + [Google Genomics Pipelines API](https://www.nextflow.io/docs/latest/google.html#google-pipelines)




Build from source
=================

Required dependencies
---------------------

* Compiler Java 8
* Runtime Java 8 or later

Build from source
-----------------

*Nextflow* is written in [Groovy](http://groovy-lang.org) (a scripting language for the JVM). A pre-compiled,
ready-to-run, package is available at the [Github releases page](https://github.com/nextflow-io/nextflow/releases),
thus it is not necessary to compile it in order to use it.

If you are interested in modifying the source code, or contributing to the project, it worth knowing that
the build process is based on the [Gradle](http://www.gradle.org/) build automation system.

You can compile *Nextflow* by typing the following command in the project home directory on your computer:

```bash
make compile
```

The very first time you run it, it will automatically download all the libraries required by the build process.
It may take some minutes to complete.

When complete, execute the program by using the `launch.sh` script in the project directory.

The self-contained runnable Nextflow packages can be created by using the following command:

```bash
make pack
```

In order to install the compiled packages use the following command:

```bash
make install
```

Then you will be able to run nextflow using the `nextflow` launcher script in the project root folder.

Known compilation problems
---------------------------

Nextflow required JDK 8 to be compiled. The Java compiler used by the build process can be choose by setting the
`JAVA_HOME` environment variable accordingly.


If the compilation stops reporting the error: `java.lang.VerifyError: Bad <init> method call from inside of a branch`,
this is due to a bug affecting the following Java JDK:

- 1.8.0 update 11
- 1.8.0 update 20

Upgrade to a newer JDK to avoid to this issue. Alternatively a possible workaround is to define the following variable
in your environment:

```bash
_JAVA_OPTIONS='-Xverify:none'
```

Read more at these links:

- https://bugs.openjdk.java.net/browse/JDK-8051012
- https://jira.codehaus.org/browse/GROOVY-6951


IntelliJ IDEA
---------------

Nextflow development with [IntelliJ IDEA](https://www.jetbrains.com/idea/) requires the latest version of the IDE (2017.3 or higher).

If you have it installed in your computer, follow the steps below in order to use it with Nextflow:

1. Clone the Nextflow repository to a directory in your computer.
2. Open IntelliJ IDEA and choose "Import project" in the "File" menu bar.
3. Select the Nextflow project root directory in your computer and click "OK".
4. Then, choose the "Gradle" item in the "external module" list and click on "Next" button.
5. Confirm the default import options and click on "Finish" to finalize the project configuration.
6. When the import process complete, select the "Project structure" command in the "File" menu bar.
7. In the showed dialog click on the "Project" item in the list of the left, and make sure that
   the "Project SDK" choice on the right contains Java 8.



Contributing
============

Project contribution are more than welcome. See the [CONTRIBUTING](CONTRIBUTING.md) file for details.

Information on setting up your development environment to work on *Nextflow* can be found [here](BuildFromSource.md)

Community
=========

You can post questions, or report problems by using the Nextflow [discussion forum](https://groups.google.com/forum/#!forum/nextflow)
or the [Nextflow channel on Gitter](https://gitter.im/nextflow-io/nextflow).


Build servers
=============

  * [Travis-CI](https://travis-ci.org/nextflow-io/nextflow)
  * [Groovy Joint build](http://ci.groovy-lang.org/project.html?projectId=JointBuilds_Nextflow&guest=1)

License
=======

The *Nextflow* framework is released under the Apache 2.0 license.

Citations
=========

If you use Nextflow in your research, please cite:

P. Di Tommaso, et al. Nextflow enables reproducible computational workflows. Nature Biotechnology 35, 316–319 (2017) doi:[10.1038/nbt.3820](http://www.nature.com/nbt/journal/v35/n4/full/nbt.3820.html)

Credits
=======

Nextflow is built on two great pieces of open source software, namely <a href='http://groovy-lang.org' target='_blank'>Groovy</a>
and <a href='http://www.gpars.org/' target='_blank'>Gpars</a>.

YourKit is kindly supporting this open source project with its full-featured Java Profiler.
Read more http://www.yourkit.com
