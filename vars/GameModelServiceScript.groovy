/*
@Library("sharelib") _

pipeline {
    agent none
    parameters {
            booleanParam(name: 'Refresh', defaultValue: false, description: '')
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

def call(body) {
    node('ServerModelBuildPC') {
        stage('Sync Perforce') {
            dir(env.P4RootDir) {
                //checkout perforce(credential: 'programmer', populate: syncOnly(force: false, have: true, modtime: false, quiet: false, revert: false), workspace: manualSpec(charset: 'utf8', name: 'RD_DailyCCB', pinHost: true, spec: clientSpec(allwrite: false, backup: true, changeView: '', clobber: true, compress: false, line: 'UNIX', locked: false, modtime: false, rmdir: true, serverID: '', streamName: '//GD2ReDream/RD_DailyCCB', type: 'WRITABLE', view: '')))
                checkout perforce(credential: env.P4Credential, populate: syncOnly(force: false, have: true, modtime: false, quiet: false, revert: false), workspace: manualSpec(charset: 'utf8', name: env.P4Workspace, pinHost: true, spec: clientSpec(allwrite: false, backup: true, changeView: '', clobber: true, compress: false, line: 'UNIX', locked: false, modtime: false, rmdir: true, serverID: '', streamName: env.P4Stream, type: 'WRITABLE', view: '')))
            }
        }

        stage('Build Services') {
            dir(env.P4RootDir) {
                bat '''
                cd GameModel\\Model.Server
                call Deployment\\Bot\\ClearLogs.bat
                call Deployment\\DeployCore\\Instances\\ClearLogs.bat
                "..\\Tools\\ExcelParser\\MSBuild\\15.0\\Bin\\MSBuild.exe" "GameModel.sln" -p:Configuration=Release -restore -t:rebuild
                '''
            }
        }

        stage('CompressUpload') {
            bat '''
                set x=%date:~0,4%%date:~5,2%%date:~8,2%
                IF "%time:~0,1%" == " " (
                    set y=0%time:~1,1%%time:~3,2%%time:~6,2%
                )ELSE (
                    set y=%time:~0,2%%time:~3,2%%time:~6,2%
                )

                set FILEPATH=%P4RootDir%\\GameModel\\DeploymentPack\\Release_%P4Stream:~13%_%BUILD_NUMBER%_%x%_%y%
                7z a %FILEPATH% %P4RootDir%\\GameModel\\Model.Server\\Deployment

                gsutil cp %FILEPATH%.7z %GSPath%'
                '''
        }

        if(env.DeployToStaging=='true')
        {
            echo 'true'
            stage('DeployToStaging') {
                bat '''
                    set TAR_PATH=D:\\Docker\\service\\ansible\\volume\\ansible\\Deployment.tar
                    del %TAR_PATH%&
                    call 7z a -ttar %TAR_PATH% %P4RootDir%\\GameModel\\Model.Server\\Deployment
                    docker exec ansible ansible-playbook ./ansible/playbooks/staging/update-services.yml
                    docker exec ansible ansible-playbook ./ansible/playbooks/staging/start-services.yml -vvv
                    python D:\\_Pythan\\ReportSuccess.py StagingServerStart
                    '''
            }
        }
    }
}