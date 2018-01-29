/*
 * Copyright (c) 2013-2018, Centre for Genomic Regulation (CRG).
 * Copyright (c) 2013-2018, Paolo Di Tommaso and the respective authors.
 *
 *   This file is part of 'Nextflow'.
 *
 *   Nextflow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Nextflow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Nextflow.  If not, see <http://www.gnu.org/licenses/>.
 */

package nextflow.cli
import java.nio.file.Path
import java.nio.file.Paths

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.config.ConfigBuilder
import nextflow.exception.AbortOperationException
import nextflow.scm.AssetManager
import nextflow.util.ConfigHelper
import picocli.CommandLine

/**
 *  Prints the pipeline configuration
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
//@Parameters(commandDescription = "Print a project configuration")
@CommandLine.Command(name = "Config", description ="Print a project configuration")
class CmdConfig extends CmdBase {

    static final public NAME = 'config'

    @Parameter(description = 'project name')
    @CommandLine.Parameters(description = "Project name")    //TODO is it mandatory?
    List<String> args = []

    @Parameter(names=['-a','-show-profiles'], description = 'Show all configuration profiles')
    @CommandLine.Option(names=['-a','--show-profiles'], description = 'Show all configuration profiles')
    boolean showAllProfiles

    @Parameter(names=['-profile'], description = 'Choose a configuration profile')
    @CommandLine.Option(names=['--profile'], description = 'Choose a configuration profile')
    String profile

    @Parameter(names = '-properties', description = 'Prints config using Java properties notation')
    @CommandLine.Option(names =['--properties'], description = 'Prints config using Java properties notation')
    boolean printProperties

    @Parameter(names = '-flat', description = 'Print config using flat notation')
    @CommandLine.Option(names =['--flat'], description = 'Print config using flat notation')
    boolean printFlatten


    @Override
    String getName() { NAME }

    private OutputStream stdout = System.out

    @Override
    void run() {
        Path base = null
        if( args ) base = getBaseDir(args[0])
        if( !base ) base = Paths.get('.')

        if( profile && showAllProfiles ) {
            throw new AbortOperationException("Option `--profile` conflicts with option `--show-profiles`")
        }

        if( printProperties && printFlatten )
            throw new AbortOperationException("Option `--flat` and `--properties` conflicts")

        def config = new ConfigBuilder()
                .setOptions(launcher.options)
                .setBaseDir(base.complete())
                .setCmdConfig(this)
                .configObject()

        if( printProperties ) {
            printProperties(config, stdout)
        }
        else if( printFlatten ) {
            printFlatten(config, stdout)
        }
        else {
            printCanonical(config, stdout)
        }
    }

    /**
     * Prints a {@link ConfigObject} using Java {@link Properties} in canonical format
     * ie. any nested config object is printed withing curly brackets
     *
     * @param config The {@link ConfigObject} representing the parsed workflow configuration
     * @param output The stream where output the formatted configuration notation
     */
    protected void printCanonical(ConfigObject config, OutputStream output) {
        output << ConfigHelper.toCanonicalString(config)
    }

    /**
     * Prints a {@link ConfigObject} using Java {@link Properties} format
     *
     * @param config The {@link ConfigObject} representing the parsed workflow configuration
     * @param output The stream where output the formatted configuration notation
     */
    protected void printProperties(ConfigObject config, OutputStream output) {
        output << ConfigHelper.toPropertiesString(config)
    }

    /**
     * Prints a {@link ConfigObject} using properties dot notation.
     * String values are enclosed in single quote characters.
     *
     * @param config The {@link ConfigObject} representing the parsed workflow configuration
     * @param output The stream where output the formatted configuration notation
    */
    protected void printFlatten(ConfigObject config, OutputStream output) {
        output << ConfigHelper.toFlattenString(config)
    }

    /**
     * Prints the {@link ConfigObject} configuration object using the default notation
     *
     * @param config The {@link ConfigObject} representing the parsed workflow configuration
     * @param output The stream where output the formatted configuration notation
     */
    protected void printDefault(ConfigObject config, OutputStream output) {
        def writer = new PrintWriter(output,true)
        config.writeTo( writer )
    }


    Path getBaseDir(String path) {

        def file = Paths.get(path)
        if( file.isDirectory() )
            return file

        if( file.exists() ) {
            return file.parent ?: Paths.get('/')
        }

        def manager = new AssetManager(path)
        manager.isLocal() ? manager.localPath.toPath() : null

    }



}
