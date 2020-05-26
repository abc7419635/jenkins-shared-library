/*
@Library("sharelib") _

pipeline {
    agent none
    parameters {
        booleanParam(name: 'Refresh', defaultValue: false, description: '')
        booleanParam(name: 'SkipP4Update', defaultValue: false, description: '')
        string(name: 'P4Credential', defaultValue: 'programmer', description: '')
        string(name: 'P4Workspace', defaultValue: 'RD_DailyBuild', description: '')
        choice(name: 'P4Stream', choices: ['//GD2ReDream/DailyBuild'], description: '')
        string(name: 'P4RootDir', defaultValue: 'D:\\RD_DailyBuild', description: '')
        string(name: 'BOTO_CONFIG', defaultValue: 'D:\\JenkinsRemoteRoot\\.boto', description: '')
        string(name: 'GSPath', defaultValue: 'gs://server_model_release/', description: '')
    }
    
    stages {
        stage('Init Parameters') {
            steps {
                echo 'Init Parameters'
            }
        }
    }
}

if(env.Refresh=='false')
{
    UnrealBuildScript()
}
*/

import java.text.SimpleDateFormat

def call(body) {
    node('ServerModelBuildPC') {
        stage('Sync Perforce') {
            if(env.SkipP4Update=='false')
            {
                dir(env.P4RootDir) {
                    checkout perforce(credential: env.P4Credential,
                    populate: syncOnly(force: false, have: true, modtime: false, quiet: false, revert: false),
                    workspace: manualSpec(charset: 'utf8', name: env.P4Workspace, pinHost: true,
                    spec: clientSpec(allwrite: false, backup: true, changeView: '', clobber: true, compress: false, line: 'UNIX', locked: false, modtime: false, rmdir: true, serverID: '',
                    streamName: env.P4Stream, type: 'WRITABLE', view: '')))
                }
            }
            else
            {
                echo 'Skip Sync Perforce'
            }
        }
    }
}