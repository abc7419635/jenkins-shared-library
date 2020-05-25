/*
@Library("sharelib") _

pipeline {
    agent none
    parameters {
        booleanParam(name: 'Refresh', defaultValue: false, description: '')
        string(name: 'WORKDIR', defaultValue: 'E:\\_ReDreamArchive\\', description: '')
        string(name: 'DATAPATH', defaultValue: '', description: '')
        string(name: 'ZIPNAME', defaultValue: '', description: '')
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
    RemoteBuildCompress()
}
*/

def call(body) {
    node('RemoteBuildPC') {        
        stage('CompressUpload') {
            bat '''
                7z a %WORKDIR%%ZIPNAME% %DATAPATH%
                '''
        }
    }
}