/*
@Library("sharelib") _

pipeline {
    agent none
    parameters {
        booleanParam(name: 'Refresh', defaultValue: false, description: '')
        string(name: 'GAMEDIR', defaultValue: '', description: '')
        string(name: 'ALIVEBRANCH', defaultValue: 'development', description: '')
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
    SteamDeploy()
}
*/

def call(body) {
    node('RemoteBuildPC') {        
        stage('SteamDeploy') {
            bat '''
                IF NOT "%GAMEDIR%"=="" (
                python D:\\_BuildTools\\Python\\ModifySteamSetting.py %GAMEDIR% %ALIVEBRANCH%
                D:
                cd D:\\_BuildTools\\SteamSDK\\sdk\\tools\\ContentBuilder
                run_build_Steam.bat
                python D:\\_BuildTools\\Python\\DiscordNotifySteamVersion.py %GAMEDIR% %ALIVEBRANCH%)
                '''
        }
    }
}