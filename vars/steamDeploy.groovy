/*
@Library("sharelib") _

pipeline {
    agent none
    parameters {
        booleanParam(name: 'Refresh', defaultValue: false, description: '')
        string(name: 'STEAM_DEPOT_FILE', defaultValue: 'D:\\_BuildTools\\SteamSDK\\sdk\\tools\\ContentBuilder\\scripts\\depot_build_993871_Steam.vdf', description: '')
        string(name: 'STEAM_APP_FILE', defaultValue: 'D:\\_BuildTools\\SteamSDK\\sdk\\tools\\ContentBuilder\\scripts\\app_build_993870_Steam.vdf', description: '')
        string(name: 'GAME_PATH', defaultValue: '', description: '')
        string(name: 'ALIVE_BRANCH', defaultValue: 'development', description: '')
    }
    
    stages {
        stage('Init Parameters') {
            steps {
                echo 'Init Parameters'

                script {
                    if(env.Refresh=='false') {
                        steamDeploy()
                    }
                }
            }
        }
    }
}
*/

def call(body) {
    node('RemoteBuildPC') {        
        stage('SteamDeploy') {
            bat '''
                IF NOT "%GAME_PATH%"=="" (
                python D:\\_BuildTools\\Python\\ModifySteamSettingV2.py %STEAM_DEPOT_FILE% %STEAM_APP_FILE% %GAME_PATH% %ALIVE_BRANCH%
                D:
                cd D:\\_BuildTools\\SteamSDK\\sdk\\tools\\ContentBuilder
                run_build_Steam.bat
                python D:\\_BuildTools\\Python\\DiscordNotifySteamVersionV2.py %GAME_PATH% %ALIVE_BRANCH%)
                '''
        }
    }
}