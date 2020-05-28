/*
@Library("sharelib") _

pipeline {
    agent none
    parameters {
        booleanParam(name: 'Refresh', defaultValue: false, description: '')
        string(name: 'WORKDIR', defaultValue: 'E:\\_ReDreamArchive\\', description: '')
        string(name: 'DATAPATH', defaultValue: '', description: '')
        string(name: 'ZIPNAME', defaultValue: '', description: '')
        string(name: 'SteamBranch', defaultValue: '', description: '')
    }
    
    stages {
        stage('Init Parameters') {
            steps {
                echo 'Init Parameters'

                script {
                    if(env.Refresh=='false') {
                        remoteBuildCompress()
                    }
                }
            }
        }
    }
}
*/

def call(body) {
    node('RemoteBuildPC') {        
        stage('CompressUpload') {
            bat '''
                7z a %WORKDIR%%ZIPNAME% %DATAPATH%
                '''
        }
        stage('SteamDeploy') {
            if(env.SteamBranch != "") {
                build job: 'SteamDeploy', parameters: [string(name: 'GAME_PATH', value: env.DATAPATH),
                string(name: 'ALIVE_BRANCH', value: env.SteamBranch)], wait: false
            }
            else {
                echo 'Skip Steam Deploy'
            }
        }
    }
}