/*
 * Copyright 2013-2019, Centre for Genomic Regulation (CRG)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.scm

import spock.lang.Specification

import nextflow.cloud.aws.AmazonCloudDriver

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.services.codecommit.CodeCommitClient
import software.amazon.awssdk.services.codecommit.CodeCommitClientBuilder

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RepositoryProviderTest extends Specification {

    def 'should create repository provider object' () {

        def provider

        when:
        provider = RepositoryProvider.create(new ProviderConfig('github'),'project/x')
        then:
        provider instanceof GithubRepositoryProvider
        provider.endpointUrl == 'https://api.github.com/repos/project/x'

        when:
        provider = RepositoryProvider.create(new ProviderConfig('gitlab'),'project/y')
        then:
        provider instanceof GitlabRepositoryProvider
        provider.endpointUrl == 'https://gitlab.com/api/v4/projects/project%2Fy'

        when:
        provider = RepositoryProvider.create(new ProviderConfig('bitbucket'),'project/z')
        then:
        provider instanceof BitbucketRepositoryProvider
        provider.endpointUrl == 'https://bitbucket.org/api/2.0/repositories/project/z'

        when:
        def driver = Mock(AmazonCloudDriver)
        def client = GroovyMock(CodeCommitClient)
        def builder = GroovyMock(CodeCommitClientBuilder)
        def awsCredentials = Mock(AwsCredentialsProvider)
        driver.region >> "us-west-2"
        driver.getCredentialsProvider0() >> awsCredentials
        builder.credentialsProvider(_) >> builder
        builder.build() >> client
        provider = RepositoryProvider.create(new ProviderConfig('codecommit'), 'codecommit://project')
        then:
        provider instanceof AwsCodeCommitRepositoryProvider
        provider.endpointUrl == 'https://git-codecommit.us-west-2.amazonaws.com/v1/repos/project'

        when:
        provider = RepositoryProvider.create(new ProviderConfig('local', [path:'/user/data']),'local/w')
        then:
        provider.endpointUrl == 'file:/user/data/w'
    }

    def 'should set credentials' () {

        given:
        def config = Mock(ProviderConfig)
        def provider = Spy(RepositoryProvider)
        provider.config = config

        when:
        provider.setCredentials('pditommaso', 'secret1')
        then:
        1 * config.setUser('pditommaso')
        1 * config.setPassword('secret1')

    }
}
