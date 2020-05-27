/*
@Library("sharelib") _

pipeline {
    agent none
    parameters {
        booleanParam(name: 'Refresh', defaultValue: false, description: '')
        booleanParam(name: 'SkipP4Update', defaultValue: false, description: '')
        string(name: 'P4Credential', defaultValue: 'programmer', description: '')
        string(name: 'P4Workspace', defaultValue: 'RD_GameModel', description: '')
        choice(name: 'P4Stream', choices: ['//GD2ReDream/GameModel', '//GD2ReDream/RD_GameModelCCB'], description: '')
        string(name: 'P4RootDir', defaultValue: 'D:\\RD_GameModel', description: '')
        string(name: 'BOTO_CONFIG', defaultValue: 'D:\\JenkinsRemoteRoot\\.boto', description: '')
        string(name: 'GSPath', defaultValue: 'gs://server_model_release/', description: '')
        booleanParam(name: 'DeployToStaging', defaultValue: false, description: '')
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
    GameModelServiceScript()
}
*/

import java.text.SimpleDateFormat

def call(body) {
    node('ServerModelBuildPC') {
        stage('Sync Perforce') {
            if(env.SkipP4Update=='false')
            {
                dir(env.P4RootDir) {
                    //checkout perforce(credential: 'programmer', populate: syncOnly(force: false, have: true, modtime: false, quiet: false, revert: false), workspace: manualSpec(charset: 'utf8', name: 'RD_DailyCCB', pinHost: true, spec: clientSpec(allwrite: false, backup: true, changeView: '', clobber: true, compress: false, line: 'UNIX', locked: false, modtime: false, rmdir: true, serverID: '', streamName: '//GD2ReDream/RD_DailyCCB', type: 'WRITABLE', view: '')))
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

        stage('Build Services') {
            dir(env.P4RootDir) {
                bat '''
                cd GameModel\\Model.Server
                call Deployment\\Bot\\ClearLogs.bat
                call Deployment\\DeployCore\\Instances\\ClearLogs.bat
                "C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\MSBuild\\15.0\\Bin\\MSBuild.exe" "GameModel.sln" -p:Configuration=Release -restore -t:rebuild
                '''
            }
        }

        stage('Compress') {
            def date = new Date()
            def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
            def timestring = sdf.format(date)
            env.ZIPFILEPATH = env.P4RootDir + '\\GameModel\\DeploymentPack\\Release_' + env.P4Stream.substring(13) + '_' + env.BUILD_NUMBER + '_' + timestring
            echo env.ZIPFILEPATH
            
            bat '''                
                7z a %ZIPFILEPATH% %P4RootDir%\\GameModel\\Model.Server\\Deployment
                '''
        }

        stage('Upload') {
            if(env.GSPath!='')
            {
                bat '''
                    gsutil cp %ZIPFILEPATH%.7z %GSPath%
                    '''
            }
            else
            {
                echo 'Skip Upload to GCP'
            }
        }

        stage('DeployToStaging') {
            if(env.DeployToStaging=='true')
            {
                bat '''
                    set TAR_PATH=D:\\Docker\\service\\ansible\\volume\\ansible\\Deployment.tar
                    del %TAR_PATH%&
                    call 7z a -ttar %TAR_PATH% %P4RootDir%\\GameModel\\Model.Server\\Deployment
                    docker exec ansible ansible-playbook ./ansible/playbooks/staging/update-services.yml
                    docker exec ansible ansible-playbook ./ansible/playbooks/staging/start-services.yml -vvv
                    python D:\\_Pythan\\ReportSuccess.py StagingServerStart
                    '''
            }
            else
            {
                echo 'Skip Deploy To Staging'
            }
        }
    }
}